/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings.wifi.details;

import static com.android.settings.wifi.WifiSettings.WIFI_DIALOG_ID;

import android.app.Activity;
import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.wifi.WifiConfigUiBase;
import com.android.settings.wifi.WifiDialog;
import com.android.settings.wifi.dpp.WifiDppUtils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.wifi.AccessPoint;
import com.android.settingslib.wifi.WifiSavedConfigUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Detail page for the currently connected wifi network.
 *
 * <p>The AccessPoint should be saved to the intent Extras when launching this class via
 * {@link AccessPoint#saveWifiState(Bundle)} in order to properly render this page.
 */
public class WifiNetworkDetailsFragment extends DashboardFragment implements
        WifiDialog.WifiDialogListener, DialogInterface.OnDismissListener {

    private static final String TAG = "WifiNetworkDetailsFrg";

    private AccessPoint mAccessPoint;
    private WifiDetailPreferenceController mWifiDetailPreferenceController;
    private List<WifiDialog.WifiDialogListener> mWifiDialogListeners = new ArrayList<>();

    private WifiDialog mDialog;
    private static final int REQUEST_CODE_WIFI_DPP_ENROLLEE_QR_CODE_SCANNER = 0;

    @Override
    public void onAttach(Context context) {
        mAccessPoint = new AccessPoint(context, getArguments());
        super.onAttach(context);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.WIFI_NETWORK_DETAILS;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.wifi_network_details_fragment;
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        if (dialogId == WIFI_DIALOG_ID) {
            return SettingsEnums.DIALOG_WIFI_AP_EDIT;
        }
        return 0;
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (getActivity() == null || mWifiDetailPreferenceController == null
                || mAccessPoint == null) {
            return null;
        }
        final List<AccessPoint> accessPoints = WifiSavedConfigUtils.getAllConfigs(
                getContext(), getContext().getSystemService(WifiManager.class));
        for (AccessPoint accessPoint : accessPoints) {
            if (mAccessPoint.matches(accessPoint.getConfig())) {
                mAccessPoint = accessPoint;
                break;
            }
        }
        mDialog = WifiDialog.createModal(getActivity(), this, mAccessPoint,
                WifiConfigUiBase.MODE_MODIFY);
        return mDialog;
    }

    @Override
    public void onDialogShowing() {
        super.onDialogShowing();
        setOnDismissListener(this);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mDialog = null;
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mAccessPoint.isSaved() && mAccessPoint.getConfig().canModify) {
            MenuItem item = menu.add(0, Menu.FIRST, 0, R.string.wifi_modify);
            item.setIcon(com.android.internal.R.drawable.ic_mode_edit);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case Menu.FIRST:
                if (!mWifiDetailPreferenceController.canModifyNetwork()) {
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(),
                            RestrictedLockUtilsInternal.getDeviceOwner(getContext()));
                } else {
                    if (mDialog != null) {
                        removeDialog(WIFI_DIALOG_ID);
                        mDialog = null;
                    }
                    showDialog(WIFI_DIALOG_ID);
                }
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        final ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);

        mWifiDetailPreferenceController = WifiDetailPreferenceController.newInstance(
                mAccessPoint,
                cm,
                context,
                this,
                new Handler(Looper.getMainLooper()),  // UI thread.
                getSettingsLifecycle(),
                context.getSystemService(WifiManager.class),
                mMetricsFeatureProvider);

        controllers.add(mWifiDetailPreferenceController);
        controllers.add(new AddDevicePreferenceController(context).init(mAccessPoint));

        final WifiMeteredPreferenceController meteredPreferenceController =
                new WifiMeteredPreferenceController(context, mAccessPoint.getConfig());
        controllers.add(meteredPreferenceController);

        final WifiPrivacyPreferenceController privacyController =
                new WifiPrivacyPreferenceController(context);
        privacyController.setWifiConfiguration(mAccessPoint.getConfig());
        privacyController.setIsEphemeral(mAccessPoint.isEphemeral());
        privacyController.setIsPasspoint(
                mAccessPoint.isPasspoint() || mAccessPoint.isPasspointConfig());
        controllers.add(privacyController);

        // Sets callback listener for wifi dialog.
        mWifiDialogListeners.add(mWifiDetailPreferenceController);
        mWifiDialogListeners.add(privacyController);
        mWifiDialogListeners.add(meteredPreferenceController);

        return controllers;
    }

    @Override
    public void onSubmit(WifiDialog dialog) {
        for (WifiDialog.WifiDialogListener listener : mWifiDialogListeners) {
            listener.onSubmit(dialog);
        }
    }

    @Override
    public void onScan(WifiDialog dialog, String ssid) {
        startActivityForResult(WifiDppUtils.getEnrolleeQrCodeScannerIntent(ssid),
                REQUEST_CODE_WIFI_DPP_ENROLLEE_QR_CODE_SCANNER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_WIFI_DPP_ENROLLEE_QR_CODE_SCANNER) {
            if (resultCode == Activity.RESULT_OK) {
                if (mDialog != null) {
                    mDialog.dismiss();
                    mDialog = null;
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        final List<AccessPoint> accessPoints = WifiSavedConfigUtils.getAllConfigs(
                getContext(), getContext().getSystemService(WifiManager.class));
        boolean exit = true;
        for (AccessPoint accessPoint : accessPoints) {
            if (mAccessPoint.matches(accessPoint.getConfig())) {
                exit = false;
                break;
            }
        }
        if (exit) {
            Log.d(TAG, "current AP key " + mAccessPoint.getKey() + " is not exist");
            finish();
        }
    }
}

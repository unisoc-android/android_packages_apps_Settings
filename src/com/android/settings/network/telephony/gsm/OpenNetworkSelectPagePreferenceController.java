/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.network.telephony.gsm;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.text.TextUtils;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.network.SubscriptionsChangeListener;
import com.android.settings.network.telephony.MobileNetworkUtils;
import com.android.settings.network.telephony.NetworkSelectSettings;
import com.android.settings.network.telephony.NetworkSelectWarningDialogFragment;
import com.android.settings.network.telephony.TelephonyBasePreferenceController;

import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settingslib.utils.ThreadUtils;
/**
 * Preference controller for "Open network select"
 */
public class OpenNetworkSelectPagePreferenceController extends
        TelephonyBasePreferenceController implements
        AutoSelectPreferenceController.OnNetworkSelectModeListener,
        NetworkSelectWarningDialogFragment.WarningDialogListener,
        SubscriptionsChangeListener.SubscriptionsChangeListenerClient,
        LifecycleObserver, OnStart, OnStop {
    private static final String TAG = "OpenNetworkSelectPagePreferenceController";

    private TelephonyManager mTelephonyManager;
    private Preference mPreference;
    // UNISOC: Bug1113489
    private Fragment mParentFragment;
    private FragmentManager mFragmentManager;
    // UNISOC: Bug1125715 Listen for changes to the network service state
    private ServiceStateListener mServiceStateListener;
    // UNISOC: Bug1181285
    private final Handler mUiHandler;
    // UNISOC: Bug1181101
    private SubscriptionsChangeListener mChangeListener;

    public OpenNetworkSelectPagePreferenceController(Context context, String key) {
        super(context, key);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        // UNISOC: Bug1125715 Listen for changes to the network service state
        mServiceStateListener = new ServiceStateListener(Looper.getMainLooper());
        // UNISOC: Bug1181285
        mUiHandler = new Handler(Looper.getMainLooper());
        // UNISOC: Add for Bug1181101
        mChangeListener = new SubscriptionsChangeListener(context, this);
    }

    /** UNISOC: Bug1125715 Listen for changes to the network service state */
    @Override
    public void onStart() {
        mServiceStateListener.register();
        mChangeListener.start();
    }

    @Override
    public void onStop() {
        mServiceStateListener.unregister();
        mChangeListener.stop();
    }
    /** @} */

    @Override
    public int getAvailabilityStatus(int subId) {
        return MobileNetworkUtils.shouldDisplayNetworkSelectOptions(mContext, subId)
                ? AVAILABLE
                : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        /* UNISOC: Bug1181285 query network selection mode in backgroundd
        preference.setEnabled(mTelephonyManager.getNetworkSelectionMode()
                != TelephonyManager.NETWORK_SELECTION_MODE_AUTO);
        /* @{ */
        ThreadUtils.postOnBackgroundThread(() -> {
            final int mode = mTelephonyManager.getNetworkSelectionMode();
            mUiHandler.post(() -> {
                preference.setEnabled(mode != TelephonyManager.NETWORK_SELECTION_MODE_AUTO);
            });
        });
        /* @} */
    }

    @Override
    public CharSequence getSummary() {
        final ServiceState ss = mTelephonyManager.getServiceState();
        /* UNISOC: Bug1104684 Show network operator name under the condition that
         * Voice or Data state is in service @{
        if (ss != null && ss.getState() == ServiceState.STATE_IN_SERVICE) {
        /* @{ */
        if (ss != null
                && (ss.getState() == ServiceState.STATE_IN_SERVICE
                || ss.getDataRegState() == ServiceState.STATE_IN_SERVICE)) { /* @} */
            return mTelephonyManager.getNetworkOperatorName();
        } else {
            return mContext.getString(R.string.network_disconnected);
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            /* UNISOC: Bug1113489 Show warning dialog to confirm whether or not selecting network
             * @orig
            final Bundle bundle = new Bundle();
            bundle.putInt(Settings.EXTRA_SUB_ID, mSubId);
            new SubSettingLauncher(mContext)
                    .setDestination(NetworkSelectSettings.class.getName())
                    .setSourceMetricsCategory(SettingsEnums.MOBILE_NETWORK_SELECT)
                    .setTitleRes(R.string.choose_network_title)
                    .setArguments(bundle)
                    .launch();
            * @{ */
            // UNISOC: Bug1113489 Diasble preference first to prevent continuously click
            mPreference.setEnabled(false);

            // UNISOC: Bug929604 Not allowed selecting operator during a call
            if (!MobileNetworkUtils.isNetworkSelectEnabled(mContext)) {
                mPreference.setEnabled(true);
                return false;
            }

            showWarningDialog();
            /* @} */
            return true;
        }

        return false;
    }

    public OpenNetworkSelectPagePreferenceController init(int subId) {
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);
        return this;
    }

    /**
     * UNISOC: Bug1113489 Show warning dialog to confirm whether or not selecting network
     * Add this extended constuction method to make sure robotests building successfully.
     * @{
     */
    public OpenNetworkSelectPagePreferenceController initEx(
            FragmentManager fragmentManager, Fragment parentFragment, int subId) {
        mFragmentManager = fragmentManager;
        mParentFragment = parentFragment;
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(mContext)
                .createForSubscriptionId(mSubId);
        return this;
    }
    /**
     * @}
     */

    @Override
    public void onNetworkSelectModeChanged() {
        updateState(mPreference);
    }

    /** UNISOC: Bug1113489
     *
     */
    @Override
    public void onDialogDismiss(InstrumentedDialogFragment dialog) {
        if (mPreference != null) {
            mPreference.setEnabled(true);
        }
    }

    private void showWarningDialog() {
        Log.d(TAG, "showWarningDialog");
        final NetworkSelectWarningDialogFragment fragment = NetworkSelectWarningDialogFragment
                .newInstance(mParentFragment, mSubId);
        fragment.registerForOpenNetwork(this);
        fragment.show(mFragmentManager,
                NetworkSelectWarningDialogFragment.DIALOG_TAG);
    }
    /**
     * @}
     */

    /** UNISOC: Bug1125715 Listen for changes to the network service state @{ */
    private class ServiceStateListener extends PhoneStateListener {

        public ServiceStateListener(Looper looper) {
            super(looper);
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (mPreference != null) {
                // Update preference and refresh summary
                updateState(mPreference);
            }
        }

        public void register() {
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_SERVICE_STATE);
        }

        public void unregister() {
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
        }
    }
    /** @} */

    /** UNISOC: Add for Bug1181101
     */
    @Override
    public void onSubscriptionsChanged() {
        boolean isAvailable = isAvailable();
        mPreference.setVisible(isAvailable);
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled){
    }
    /**
     * @｝
     */
}
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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.settings.SettingsEnums;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.network.SubscriptionsChangeListener;
import com.android.settings.network.telephony.MobileNetworkUtils;
import com.android.settings.network.telephony.NetworkSelectSettings;
import com.android.settings.network.telephony.MobileNetworkSettings;
import com.android.settings.network.telephony.NetworkSelectWarningDialogFragment;
import com.android.settings.network.telephony.TelephonyTogglePreferenceController;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnCreate;
import com.android.settingslib.core.lifecycle.events.OnDestroy;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Preference controller for "Auto Select Network"
 */
public class AutoSelectPreferenceController extends
        TelephonyTogglePreferenceController implements LifecycleObserver,
        OnCreate, OnDestroy,OnStart,OnStop,
        MobileNetworkSettings.onManualSelectNetworkDoneListener,
        NetworkSelectWarningDialogFragment.WarningDialogListener,
        SubscriptionsChangeListener.SubscriptionsChangeListenerClient{
    private static final long MINIMUM_DIALOG_TIME_MILLIS = TimeUnit.SECONDS.toMillis(1);

    private static final String TAG = "AutoSelectPreferenceController";

    private final Handler mUiHandler;
    private TelephonyManager mTelephonyManager;
    private boolean mOnlyAutoSelectInHome;
    private List<OnNetworkSelectModeListener> mListeners;
    @VisibleForTesting
    ProgressDialog mProgressDialog;
    @VisibleForTesting
    SwitchPreference mSwitchPreference;
    // UNISOC: Add for Bug1181101
    PreferenceCategory mPreferenceCategory;

    private Fragment mParentFragment;
    private FragmentManager mFragmentManager;

    // UNISOC: Add for Bug1181101
    private SubscriptionsChangeListener mChangeListener;
    private static final String KEY_NETWORK_OPERATORS_CATEGORY = "network_operators_category_key";

    public AutoSelectPreferenceController(Context context, String key) {
        super(context, key);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mListeners = new ArrayList<>();
        mUiHandler = new Handler(Looper.getMainLooper());
        // UNISOC: Add for Bug1181101
        mChangeListener = new SubscriptionsChangeListener(context, this);
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        return MobileNetworkUtils.shouldDisplayNetworkSelectOptions(mContext, subId)
                ? AVAILABLE
                : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mSwitchPreference = screen.findPreference(getPreferenceKey());
        // UNISOC: Add for Bug1181101
        mPreferenceCategory = screen.findPreference(KEY_NETWORK_OPERATORS_CATEGORY);
    }

    @Override
    public void onCreate(Bundle icicle) {
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onDestroy(){
        Log.d(TAG, "onDestroy");
        // UNISOC: Add for Bug1196595
        dismissProgressBar();
    }

    public boolean isChecked() {
        /* @orig
        return mTelephonyManager.getNetworkSelectionMode()
                == TelephonyManager.NETWORK_SELECTION_MODE_AUTO;
         /* @{ */
        return false;
        /* @} */
    }

    @Override
    public void updateState(Preference preference) {
        /* @orig
        super.updateState(preference);

        preference.setSummary(null);
        if (mTelephonyManager.getServiceState().getRoaming()) {
            preference.setEnabled(true);
        } else {
            preference.setEnabled(!mOnlyAutoSelectInHome);
            if (mOnlyAutoSelectInHome) {
                preference.setSummary(mContext.getString(
                        R.string.manual_mode_disallowed_summary,
                        mTelephonyManager.getSimOperatorName()));
            }
        }
        /* UNISOC: Add for Bug1156498 @{ */
        super.updateState(null);
        ThreadUtils.postOnBackgroundThread(() -> {
            // query network selection mode in background
            final int mode = mTelephonyManager.getNetworkSelectionMode();
            final boolean autoSelection = (mode == TelephonyManager.NETWORK_SELECTION_MODE_AUTO);
            //Update UI in UI thread
            mUiHandler.post(() -> {
                mSwitchPreference.setChecked(autoSelection);
                preference.setSummary(null);
                if (mTelephonyManager.getServiceState().getRoaming()) {
                    preference.setEnabled(true);
                } else {
                    preference.setEnabled(!mOnlyAutoSelectInHome);
                    if (mOnlyAutoSelectInHome) {
                        preference.setSummary(mContext.getString(
                                R.string.manual_mode_disallowed_summary,
                                mTelephonyManager.getSimOperatorName()));
                    }
                }
            });
        });
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        /* UNISOC: Bug929604 Not allowed selecting operator during a call @{ */
        if (!MobileNetworkUtils.isNetworkSelectEnabled(mContext)) {
            mSwitchPreference.setChecked(!isChecked);
            return false;
        }
        /* @} */
        if (isChecked) {
            final long startMillis = SystemClock.elapsedRealtime();
            showAutoSelectProgressBar();
            mSwitchPreference.setEnabled(false);
            ThreadUtils.postOnBackgroundThread(() -> {
                // set network selection mode in background
                Log.d(TAG, "select network automatically...");
                mTelephonyManager.setNetworkSelectionModeAutomatic();
                final int mode = mTelephonyManager.getNetworkSelectionMode();

                //Update UI in UI thread
                final long durationMillis = SystemClock.elapsedRealtime() - startMillis;
                mUiHandler.postDelayed(() -> {
                            mSwitchPreference.setEnabled(true);
                            mSwitchPreference.setChecked(
                                    mode == TelephonyManager.NETWORK_SELECTION_MODE_AUTO);
                            for (OnNetworkSelectModeListener lsn : mListeners) {
                                lsn.onNetworkSelectModeChanged();
                            }
                            dismissProgressBar();
                        },
                        Math.max(MINIMUM_DIALOG_TIME_MILLIS - durationMillis, 0));
            });
            return false;
        } else {
            /*
             * UNISOC: Show warning dialog to confirm doing network scan
             * @orig
            final Bundle bundle = new Bundle();
            bundle.putInt(Settings.EXTRA_SUB_ID, mSubId);
            new SubSettingLauncher(mContext)
                    .setDestination(NetworkSelectSettings.class.getName())
                    .setSourceMetricsCategory(SettingsEnums.MOBILE_NETWORK_SELECT)
                    .setTitleRes(R.string.choose_network_title)
                    .setArguments(bundle)
                    .launch();
             /* @{ */

            // UNISOC: Bug1113489 Diasble preference first to prevent continuously click
            mSwitchPreference.setEnabled(false);

            showWarningDialog();
            /* @ }*/
            return false;
        }
    }

    public AutoSelectPreferenceController init(int subId) {
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);
        final PersistableBundle carrierConfig = mContext.getSystemService(
                CarrierConfigManager.class).getConfigForSubId(mSubId);
        mOnlyAutoSelectInHome = carrierConfig != null
                ? carrierConfig.getBoolean(
                CarrierConfigManager.KEY_ONLY_AUTO_SELECT_IN_HOME_NETWORK_BOOL)
                : false;
        return this;
    }

    public AutoSelectPreferenceController initEx(FragmentManager fragmentManager,
            Fragment parentFragment, int subId) {
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);
        final PersistableBundle carrierConfig = mContext.getSystemService(
                CarrierConfigManager.class).getConfigForSubId(mSubId);
        mOnlyAutoSelectInHome = carrierConfig != null
                ? carrierConfig.getBoolean(
                CarrierConfigManager.KEY_ONLY_AUTO_SELECT_IN_HOME_NETWORK_BOOL)
                : false;
        mFragmentManager = fragmentManager;
        mParentFragment = parentFragment;

        return this;
    }

    /** UNISOC: Add for Bug1181101
     */
    public void onStart() {
        mChangeListener.start();

    }

    public void onStop() {
        mChangeListener.stop();
    }
    /**
     * @} */

    public AutoSelectPreferenceController addListener(OnNetworkSelectModeListener lsn) {
        mListeners.add(lsn);

        return this;
    }

    private void showAutoSelectProgressBar() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setMessage(
                    mContext.getResources().getString(R.string.register_automatically));
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setIndeterminate(true);
        }
        mProgressDialog.show();
    }

    private void dismissProgressBar() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    /**
     * Callback when network select mode is changed
     *
     * @see TelephonyManager#getNetworkSelectionMode()
     */
    public interface OnNetworkSelectModeListener {
        void onNetworkSelectModeChanged();
    }

    /**UNISOC: Show warning dialog to confirm whether or not selecting network @{ */
    private void showWarningDialog() {
        Log.d(TAG, "showWarningDialog");
        final NetworkSelectWarningDialogFragment fragment = NetworkSelectWarningDialogFragment
                .newInstance(mParentFragment, mSubId);
        fragment.registerForAutoSelect(this);
        fragment.show(mFragmentManager,
                NetworkSelectWarningDialogFragment.DIALOG_TAG);
    }
    /**
     * @}
     **/

    /**
     * UNISOC: FL0108030015 If manually selection failed, try auto select @{
     * */
    @Override
    public void onManualSelectNetworkDone(boolean success) {
        Log.d(TAG,"onManualNetworkSelectDone: " + success);
        if (!success){
            setChecked(true);
        }
    }
    /**
     * @}
     */

    /** UNISOC: Bug1113489
     *
     */
    @Override
    public void onDialogDismiss(InstrumentedDialogFragment dialog) {
        if (mSwitchPreference != null) {
            mSwitchPreference.setEnabled(true);
        }
    }
    /**
     * @}
     */

    /** UNISOC: Add for Bug1181101
     */
    @Override
    public void onSubscriptionsChanged() {
        boolean isAvailable = isAvailable();
        mPreferenceCategory.setVisible(isAvailable);
        mSwitchPreference.setVisible(isAvailable);
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled){
    }
    /**
     * @｝
     */
}
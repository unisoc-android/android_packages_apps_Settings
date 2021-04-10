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

package com.android.settings.network.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settings.network.telephony.MobileNetworkUtils;

import java.util.List;
import android.util.Log;

/**
 * Preference controller for "Wifi Calling"
 */
public class WifiCallingPreferenceController extends TelephonyBasePreferenceController implements
        LifecycleObserver, OnStart, OnStop, BroadcastReceiverChanged.BroadcastReceiverChangedClient {

    private static final String TAG = "WifiCallingPreferenceController";

    private TelephonyManager mTelephonyManager;
    @VisibleForTesting
    CarrierConfigManager mCarrierConfigManager;
    @VisibleForTesting
    ImsManager mImsManager;
    @VisibleForTesting
    PhoneAccountHandle mSimCallManager;
    private PhoneCallStateListener mPhoneStateListener;
    private Preference mPreference;
    private boolean mEditableWfcRoamingMode;
    private boolean mUseWfcHomeModeForRoaming;
    public static final String OMA_WFC_ENABLE = "oma.wfc.enable";
    BroadcastReceiver mReceiver;
    /*UNISOC: Add for bug 1073296 @{*/
    private ContentObserver mWfcEnableObserver = null;
    private ContentObserver mWfcShownObserver = null;
    /*@}*/
    // UNISOC: fix for bug 1139650
    private BroadcastReceiverChanged mBroadcastReceiverChanged;

    public WifiCallingPreferenceController(Context context, String key) {
        super(context, key);
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mPhoneStateListener = new PhoneCallStateListener(Looper.getMainLooper());
        mEditableWfcRoamingMode = true;
        mUseWfcHomeModeForRoaming = false;
        initReceiver();// UNISOC: Bug 1098173
        // UNISOC: fix for bug 1145957
        mBroadcastReceiverChanged = new BroadcastReceiverChanged(context,this);
    }

    private void initReceiver() {
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
                    updateState(mPreference);
                }
            }
        };
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        return subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && MobileNetworkUtils.isWifiCallingEnabled(mContext, subId)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void onStart() {
        mPhoneStateListener.register(mSubId);
        if (mWfcEnableObserver == null) {
            mWfcEnableObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    updateState(mPreference);
                }
            };
        }
        if (mWfcShownObserver == null) {
            mWfcShownObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    updateState(mPreference);
                }
            };
        }
        // UNISOC: Add for Bug 1073231 control WFC showing via OMA request
        mContext.getContentResolver().registerContentObserver(
                MobileNetworkUtils.getNotifyContentUri(SubscriptionManager.WFC_ENABLED_CONTENT_URI, true, mSubId),
                true, mWfcEnableObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(MobileNetworkUtils.OMA_WFC_ENABLE + mSubId), true,
                mWfcShownObserver);
        IntentFilter filter = new IntentFilter(
                TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        // UNISOC: Bug 1098173
        mContext.registerReceiver(mReceiver, filter);
        // UNISOC: fix for bug 1145957
        mBroadcastReceiverChanged.start();
    }

    @Override
    public void onStop() {
        mPhoneStateListener.unregister();
        mContext.getContentResolver().unregisterContentObserver(mWfcEnableObserver);
        // UNISOC: Bug 807273
        mContext.getContentResolver().unregisterContentObserver(mWfcShownObserver);
        // UNISOC: Bug 1098173
        mContext.unregisterReceiver(mReceiver);
        // UNISOC: fix for bug 1145957
        mBroadcastReceiverChanged.stop();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        Intent intent = mPreference.getIntent();
        if (intent != null) {
            intent.putExtra(Settings.EXTRA_SUB_ID, mSubId);
        }
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setVisible(MobileNetworkUtils.isWifiCallingEnabled(mContext, mSubId));
        if (mSimCallManager != null) {
            Intent intent = MobileNetworkUtils.buildPhoneAccountConfigureIntent(mContext,
                    mSimCallManager);
            if (intent == null) {
                // Do nothing in this case since preference is invisible
                return;
            }
            final PackageManager pm = mContext.getPackageManager();
            List<ResolveInfo> resolutions = pm.queryIntentActivities(intent, 0);
            preference.setTitle(resolutions.get(0).loadLabel(pm));
            preference.setSummary(null);
            preference.setIntent(intent);
        } else {
            final String title = SubscriptionManager.getResourcesForSubId(mContext, mSubId)
                    .getString(R.string.wifi_calling_settings_title);
            preference.setTitle(title);
            int resId = com.android.internal.R.string.wifi_calling_off_summary;
            if (mImsManager.isWfcEnabledByUser()) {
                //UNISOC:fix for bug 1084169
                boolean showWifiCallingPreference = true;
                if (mCarrierConfigManager != null) {
                    final PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
                    if (carrierConfig != null) {
                        showWifiCallingPreference = carrierConfig.getBoolean(
                                CarrierConfigManagerEx.KEY_SUPPORT_SHOW_WIFI_CALLING_PREFERENCE);
                    }
                }
                if (showWifiCallingPreference) {
                    boolean wfcRoamingEnabled = mEditableWfcRoamingMode && !mUseWfcHomeModeForRoaming;
                    final boolean isRoaming = mTelephonyManager.isNetworkRoaming();
                    int wfcMode = mImsManager.getWfcMode(isRoaming && wfcRoamingEnabled);
                    switch (wfcMode) {
                        case ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY:
                            resId = com.android.internal.R.string.wfc_mode_wifi_only_summary;
                            break;
                        case ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED:
                            resId = com.android.internal.R.string
                                    .wfc_mode_cellular_preferred_summary;
                            break;
                        case ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED:
                            resId = com.android.internal.R.string.wfc_mode_wifi_preferred_summary;
                            break;
                        default:
                            break;
                    }
                } else {
                    resId = com.android.internal.R.string.wifi_calling_on_summary;
                }
            }
            preference.setSummary(resId);
        }
        //UNISOC:fix for bug 1083551,only data cards are available for WiFi Calling
        preference.setEnabled(!isInCall() && mSubId == SubscriptionManager.getDefaultDataSubscriptionId());
        /*UNISOC: Add for bug 1186246 @{ */
        if (synSettingForVoLTE()) {
            Log.d(TAG,"Vowifi enable to sync open VOLTE");
            mImsManager.setEnhanced4gLteModeSetting(true);
        }
        /* @} */
    }

    public WifiCallingPreferenceController init(int subId) {
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);
        mImsManager = ImsManager.getInstance(mContext, SubscriptionManager.getPhoneId(mSubId));
        mSimCallManager = mContext.getSystemService(TelecomManager.class)
                .getSimCallManagerForSubscription(mSubId);
        if (mCarrierConfigManager != null) {
            final PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
            if (carrierConfig != null) {
                mEditableWfcRoamingMode = carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL);
                mUseWfcHomeModeForRoaming = carrierConfig.getBoolean(
                        CarrierConfigManager
                                .KEY_USE_WFC_HOME_NETWORK_MODE_IN_ROAMING_NETWORK_BOOL);
            }
        }
        return this;
    }

    @Override
    public void onPhoneStateChanged() {
        updateState(mPreference);
    }

    @Override
    public void onCarrierConfigChanged(int phoneId) {}

    private class PhoneCallStateListener extends PhoneStateListener {

        public PhoneCallStateListener(Looper looper) {
            super(looper);
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            updateState(mPreference);
        }

        public void register(int subId) {
            mSubId = subId;
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE);
        }

        public void unregister() {
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
        }
    }

    /*UNISOC: Add for bug 1145957 @{ */
    private boolean isInCall() {
        return getTelecommManager().isInCall();
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }
    /* @} */
    /*UNISOC: Add for bug 1186246 @{ */
    private boolean synSettingForVoLTE() {
        return !isInCall() && mSubId == SubscriptionManager.getDefaultDataSubscriptionId() && mImsManager.isWfcEnabledByUser()
                && mImsManager.isNonTtyOrTtyOnVolteEnabled() && ImsManagerEx.synSettingForWFCandVoLTE(mContext) && mImsManager.isVolteEnabledByPlatform()
                && !mImsManager.isEnhanced4gLteModeSettingEnabledByUser();
    }
    /* @} */
}

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

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import android.content.Context;
import android.content.res.Resources;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.settingslib.WirelessUtils;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settings.network.SubscriptionsChangeListener;
import com.android.settings.R;

import java.util.List;

/**
 * Preference controller for "Enabled network mode"
 */
public class EnabledNetworkModePreferenceController extends
        TelephonyBasePreferenceController implements
        ListPreference.OnPreferenceChangeListener,LifecycleObserver,SubscriptionsChangeListener.SubscriptionsChangeListenerClient,
        BroadcastReceiverChanged.BroadcastReceiverChangedClient {

    private static final String TAG = "EnabledNetworkModePreferenceController";
    private CarrierConfigManager mCarrierConfigManager;
    private TelephonyManager mTelephonyManager;
    private boolean mIsGlobalCdma;
    @VisibleForTesting
    boolean mShow4GForLTE;
    private ListPreference mNetworkModePreference;
    private SubscriptionsChangeListener mSubscriptionsListener;
    private BroadcastReceiverChanged mBroadcastReceiverChanged;
    // UNISOC: Add for Bug1176994
    private Handler mUiHandler;
    // UNISOC: Add for Bug1191477
    private ContentResolver mContentResolver;
    private ContentObserver mContentObserver;

    public EnabledNetworkModePreferenceController(Context context, String key) {
        super(context, key);
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mSubscriptionsListener = new SubscriptionsChangeListener(context, this);
        mBroadcastReceiverChanged = new BroadcastReceiverChanged(context,this);
        // UNISOC: Add for Bug1176994
        mUiHandler = new Handler(Looper.getMainLooper());
        // UNISOC: Add for Bug1191477
        mContentResolver = context.getContentResolver();
    }

    @OnLifecycleEvent(ON_RESUME)
    public void onResume() {
        Log.d(TAG,"onResume");
        mSubscriptionsListener.start();
        mBroadcastReceiverChanged.start();
        // UNISOC: Add for Bug1191477
        mContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                updateState(mNetworkModePreference);
            }
        };
        mContentResolver.registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.PREFERRED_NETWORK_MODE + mSubId), false, mContentObserver);
    }

    @OnLifecycleEvent(ON_PAUSE)
    public void onPause() {
        Log.d(TAG,"onPause");
        mSubscriptionsListener.stop();
        mBroadcastReceiverChanged.stop();
        // UNISOC: Add for Bug1191477
        if (mContentObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
        }
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        boolean visible;
        final PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(subId);
        final TelephonyManager telephonyManager = TelephonyManager
                .from(mContext).createForSubscriptionId(subId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            visible = false;
        } else if (carrierConfig == null) {
            visible = false;
        } else if (carrierConfig.getBoolean(
                CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL)) {
            visible = false;
        } else if (carrierConfig.getBoolean(
                CarrierConfigManager.KEY_HIDE_PREFERRED_NETWORK_TYPE_BOOL)
                && !telephonyManager.getServiceState().getRoaming()
                && telephonyManager.getServiceState().getDataRegState()
                == ServiceState.STATE_IN_SERVICE) {
            visible = false;
        } else if (carrierConfig.getBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL)) {
            visible = false;
        } else if (!isAllowedToShowNetworkTypeOption(subId)) {
            visible = false;
        } else {
            visible = true;
        }

        return visible ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        final ListPreference listPreference = (ListPreference) preference;
        final int networkMode = getPreferredNetworkMode();
        updatePreferenceEntries(listPreference);
        updatePreferenceValueAndSummary(listPreference, networkMode);
        boolean isAirplaneModeOn = WirelessUtils.isAirplaneModeOn(mContext);
        boolean isNetworkOptionEnabled = !isAirplaneModeOn && !isPhoneInCall();
        preference.setEnabled(isNetworkOptionEnabled);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object object) {
        // UNISOC: Network type settings for global market
        if("true".equals(SystemProperties.get("persist.vendor.radio.engtest.enable","false"))) {
            Toast.makeText(mContext, R.string.network_mode_setting_prompt, Toast.LENGTH_SHORT).show();
        } else {
            final int settingsMode = Integer.parseInt((String) object);
            /*
            if (mTelephonyManager.setPreferredNetworkType(mSubId, settingsMode)) {
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.PREFERRED_NETWORK_MODE + mSubId,
                        settingsMode);
                updatePreferenceValueAndSummary((ListPreference) preference, settingsMode);
                return true;
            }
            */
            // UNISOC: Add for Bug1176994
            final ListPreference listPreference = (ListPreference)preference;
            listPreference.setEnabled(false);
            ThreadUtils.postOnBackgroundThread(() -> {
                Log.d(TAG, "set preferred network type in backgroud: " + settingsMode);
                boolean isSucceed = mTelephonyManager.setPreferredNetworkType(mSubId, settingsMode);
                mUiHandler.post(() -> {
                    listPreference.setEnabled(true);
                    if (isSucceed) {
                        Settings.Global.putInt(mContext.getContentResolver(),
                                Settings.Global.PREFERRED_NETWORK_MODE + mSubId,
                                settingsMode);
                        updatePreferenceValueAndSummary(listPreference, settingsMode);
                    }
                });
            });
        }

        return true;
    }

    public void init(int subId) {
        mSubId = subId;
        final PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);

        final boolean isLteOnCdma =
                mTelephonyManager.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE;
        mIsGlobalCdma = isLteOnCdma
                && carrierConfig.getBoolean(CarrierConfigManager.KEY_SHOW_CDMA_CHOICES_BOOL);
        mShow4GForLTE = carrierConfig != null
                ? carrierConfig.getBoolean(
                CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL)
                : false;
    }

    public void init(Lifecycle lifecycle,int subId) {
        lifecycle.addObserver(this);
        mSubId = subId;
        final PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);

        final boolean isLteOnCdma =
                mTelephonyManager.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE;
        mIsGlobalCdma = isLteOnCdma
                && carrierConfig.getBoolean(CarrierConfigManager.KEY_SHOW_CDMA_CHOICES_BOOL);
        mShow4GForLTE = carrierConfig != null
                ? carrierConfig.getBoolean(
                CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL)
                : false;
    }

    private int getPreferredNetworkMode() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE + mSubId,
                Phone.PREFERRED_NT_MODE);
    }

    private void updatePreferenceEntries(ListPreference preference) {
        final int phoneType = mTelephonyManager.getPhoneType();
        final PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            final int lteForced = android.provider.Settings.Global.getInt(
                    mContext.getContentResolver(),
                    android.provider.Settings.Global.LTE_SERVICE_FORCED + mSubId,
                    0);
            final boolean isLteOnCdma = mTelephonyManager.getLteOnCdmaMode()
                    == PhoneConstants.LTE_ON_CDMA_TRUE;
            final int settingsNetworkMode = android.provider.Settings.Global.getInt(
                    mContext.getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + mSubId,
                    Phone.PREFERRED_NT_MODE);
            if (isLteOnCdma) {
                if (lteForced == 0) {
                    preference.setEntries(
                            R.array.enabled_networks_cdma_choices);
                    preference.setEntryValues(
                            R.array.enabled_networks_cdma_values);
                } else {
                    switch (settingsNetworkMode) {
                        case TelephonyManager.NETWORK_MODE_CDMA_EVDO:
                        case TelephonyManager.NETWORK_MODE_CDMA_NO_EVDO:
                        case TelephonyManager.NETWORK_MODE_EVDO_NO_CDMA:
                            preference.setEntries(
                                    R.array.enabled_networks_cdma_no_lte_choices);
                            preference.setEntryValues(
                                    R.array.enabled_networks_cdma_no_lte_values);
                            break;
                        case TelephonyManager.NETWORK_MODE_GLOBAL:
                        case TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO:
                        case TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                        case TelephonyManager.NETWORK_MODE_LTE_ONLY:
                            preference.setEntries(
                                    R.array.enabled_networks_cdma_only_lte_choices);
                            preference.setEntryValues(
                                    R.array.enabled_networks_cdma_only_lte_values);
                            break;
                        default:
                            preference.setEntries(
                                    R.array.enabled_networks_cdma_choices);
                            preference.setEntryValues(
                                    R.array.enabled_networks_cdma_values);
                            break;
                    }
                }
            }
        } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
            if (MobileNetworkUtils.isTdscdmaSupported(mContext, mSubId)) {
                preference.setEntries(
                        R.array.enabled_networks_tdscdma_choices);
                preference.setEntryValues(
                        R.array.enabled_networks_tdscdma_values);
            } else if (carrierConfig != null
                    && !carrierConfig.getBoolean(CarrierConfigManager.KEY_PREFER_2G_BOOL)
                    && !carrierConfig.getBoolean(CarrierConfigManager.KEY_LTE_ENABLED_BOOL)) {
                preference.setEntries(R.array.enabled_networks_except_gsm_lte_choices);
                preference.setEntryValues(R.array.enabled_networks_except_gsm_lte_values);
            } else if (carrierConfig != null
                    && !carrierConfig.getBoolean(CarrierConfigManager.KEY_PREFER_2G_BOOL)) {
                int select = mShow4GForLTE
                        ? R.array.enabled_networks_except_gsm_4g_choices
                        : R.array.enabled_networks_except_gsm_choices;
                preference.setEntries(select);
                preference.setEntryValues(
                        R.array.enabled_networks_except_gsm_values);
            } else if (carrierConfig != null
                    && !carrierConfig.getBoolean(CarrierConfigManager.KEY_LTE_ENABLED_BOOL)) {
                preference.setEntries(
                        R.array.enabled_networks_except_lte_choices);
                preference.setEntryValues(
                        R.array.enabled_networks_except_lte_values);
            } else if (mIsGlobalCdma) {
                preference.setEntries(R.array.enabled_networks_cdma_choices);
                preference.setEntryValues(R.array.enabled_networks_cdma_values);
            } else {
                int select = mShow4GForLTE ? R.array.enabled_networks_4g_choices
                        : R.array.enabled_networks_choices;
                preference.setEntries(select);
                preference.setEntryValues(R.array.enabled_networks_values);
            }
        }
        //TODO(b/117881708): figure out what world mode is, then we can optimize code. Otherwise
        // I prefer to keep this old code
        if (MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
            preference.setEntries(
                    R.array.preferred_network_mode_choices_world_mode);
            preference.setEntryValues(
                    R.array.preferred_network_mode_values_world_mode);
        }

        /** UNISOC:Add for network mode  @{ */
        if (MobileNetworkUtils.isSupportNR(mContext, mSubId)) {
            preference.setEntries(R.array.enabled_5g_NR_networks_choices);
            preference.setEntryValues(R.array.enabled_5g_NR_networks_values);
        } else if (MobileNetworkUtils.isSupportLTE(mContext, mSubId)) {
            preference.setEntries(R.array.enabled_networks_choices);
            preference.setEntryValues(R.array.enabled_networks_values);
        } else if (MobileNetworkUtils.isSupportWCDMA(mContext, mSubId)) {
            preference.setEntries(R.array.enabled_networks_except_lte_choices);
            preference.setEntryValues(R.array.enabled_networks_except_lte_values);
        }

        Resources resource = SubscriptionManager.getResourcesForSubId(mContext, mSubId);
        boolean carrierCustomize = resource.getBoolean(R.bool.config_carrier_customize_network);
        if (carrierCustomize) {
            preference.setEntries(R.array.carrier_customize_network_choices);
            preference.setEntryValues(R.array.carrier_customize_network_values);
        }
        /** @} */
    }

    private void updatePreferenceValueAndSummary(ListPreference preference, int networkMode) {
        preference.setValue(Integer.toString(networkMode));
        switch (networkMode) {
            case TelephonyManager.NETWORK_MODE_TDSCDMA_WCDMA:
            case TelephonyManager.NETWORK_MODE_TDSCDMA_GSM_WCDMA:
            case TelephonyManager.NETWORK_MODE_TDSCDMA_GSM:
                preference.setValue(
                        Integer.toString(TelephonyManager.NETWORK_MODE_TDSCDMA_GSM_WCDMA));
                preference.setSummary(R.string.network_3G);
                break;
            case TelephonyManager.NETWORK_MODE_WCDMA_ONLY:
                preference.setValue(
                        Integer.toString(TelephonyManager.NETWORK_MODE_WCDMA_ONLY));
                preference.setSummary(R.string.network_3G_only);
                break;
            case TelephonyManager.NETWORK_MODE_GSM_UMTS:
            case TelephonyManager.NETWORK_MODE_WCDMA_PREF:
                if (!mIsGlobalCdma) {
                    preference.setValue(Integer.toString(TelephonyManager.NETWORK_MODE_WCDMA_PREF));
                    preference.setSummary(R.string.network_3G_auto);
                } else {
                    preference.setValue(Integer.toString(TelephonyManager
                            .NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                    preference.setSummary(R.string.network_global);
                }
                break;
            case TelephonyManager.NETWORK_MODE_GSM_ONLY:
                if (!mIsGlobalCdma) {
                    preference.setValue(
                            Integer.toString(TelephonyManager.NETWORK_MODE_GSM_ONLY));
                    preference.setSummary(R.string.network_2G_only);
                } else {
                    preference.setValue(
                            Integer.toString(TelephonyManager
                                    .NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                    preference.setSummary(R.string.network_global);
                }
                break;
            case TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA:
                if (MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
                    preference.setSummary(
                            R.string.preferred_network_mode_lte_gsm_umts_summary);
                    break;
                } else {
                    preference.setValue(
                            Integer.toString(TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA));
                    preference.setSummary(R.string.network_4g_auto);
                    break;
                }
            case TelephonyManager.NETWORK_MODE_LTE_ONLY:
                preference.setValue(
                        Integer.toString(TelephonyManager.NETWORK_MODE_LTE_ONLY));
                preference.setSummary(R.string.network_4G_only);
                break;
            case TelephonyManager.NETWORK_MODE_LTE_WCDMA:
                if (!mIsGlobalCdma) {
                    preference.setValue(
                            Integer.toString(TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA));
                    preference.setSummary(
                            mShow4GForLTE ? R.string.network_4G : R.string.network_lte);
                } else {
                    preference.setValue(
                            Integer.toString(TelephonyManager
                                    .NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                    preference.setSummary(R.string.network_global);
                }
                break;
            case TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO:
                if (MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
                    preference.setSummary(
                            R.string.preferred_network_mode_lte_cdma_summary);
                } else {
                    preference.setValue(
                            Integer.toString(TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO));
                    preference.setSummary(R.string.network_lte);
                }
                break;
            case TelephonyManager.NETWORK_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                preference.setValue(Integer.toString(TelephonyManager
                        .NETWORK_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA));
                preference.setSummary(R.string.network_3G);
                break;
            case TelephonyManager.NETWORK_MODE_CDMA_EVDO:
            case TelephonyManager.NETWORK_MODE_EVDO_NO_CDMA:
            case TelephonyManager.NETWORK_MODE_GLOBAL:
                preference.setValue(
                        Integer.toString(TelephonyManager.NETWORK_MODE_CDMA_EVDO));
                preference.setSummary(R.string.network_3G);
                break;
            case TelephonyManager.NETWORK_MODE_CDMA_NO_EVDO:
                preference.setValue(
                        Integer.toString(TelephonyManager.NETWORK_MODE_CDMA_NO_EVDO));
                preference.setSummary(R.string.network_1x);
                break;
            case TelephonyManager.NETWORK_MODE_TDSCDMA_ONLY:
                preference.setValue(
                        Integer.toString(TelephonyManager.NETWORK_MODE_TDSCDMA_ONLY));
                preference.setSummary(R.string.network_3G);
                break;
            case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_GSM:
            case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_GSM_WCDMA:
            case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA:
            case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_WCDMA:
            case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
            case TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                if (MobileNetworkUtils.isTdscdmaSupported(mContext, mSubId)) {
                    preference.setValue(
                            Integer.toString(TelephonyManager
                                    .NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA));
                    preference.setSummary(R.string.network_lte);
                } else {
                    preference.setValue(
                            Integer.toString(TelephonyManager
                                    .NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                    if (mTelephonyManager.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA
                            || mIsGlobalCdma
                            || MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
                        preference.setSummary(R.string.network_global);
                    } else {
                        preference.setSummary(mShow4GForLTE
                                ? R.string.network_4G : R.string.network_lte);
                    }
                }
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_GSM_WCDMA:
                preference.setValue(Integer.toString(RILConstants.NETWORK_MODE_NR_LTE_GSM_WCDMA));
                preference.setSummary(R.string.network_5G);
                break;
            case RILConstants.NETWORK_MODE_NR_ONLY:
                preference.setValue(Integer.toString(RILConstants.NETWORK_MODE_NR_ONLY));
                preference.setSummary(R.string.network_5G_only);
                break;
            default:
                preference.setSummary(
                        mContext.getString(R.string.mobile_network_mode_error, networkMode));
        }
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mNetworkModePreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onAirplaneModeChanged(boolean isAirplaneModeOn) {
        Log.d(TAG,"onAirplaneModeChanged isAirplaneModeOn="+isAirplaneModeOn);
        updateState(mNetworkModePreference);
    }

    @Override
    public void onSubscriptionsChanged() {
        Log.d(TAG,"onSubscriptionsChanged");
        if (SubscriptionManager.isValidSubscriptionId(mSubId)) {
            updateState(mNetworkModePreference);
        }
    }

    @Override
    public void onPhoneStateChanged() {
        Log.d(TAG,"onPhoneStateChanged");
        updateState(mNetworkModePreference);
    }

    @Override
    public void onCarrierConfigChanged(int phoneId) {}

    private boolean isPhoneInCall() {
        List<SubscriptionInfo> activeSubIdList = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
        if (activeSubIdList != null) {
            for (SubscriptionInfo subInfo : activeSubIdList) {
                if (mTelephonyManager.getCallState(subInfo.getSubscriptionId()) != TelephonyManager.CALL_STATE_IDLE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Special operator requirements like CMCC: Don't show network type option
     * for non-data card anyway USIM.
     */
    private boolean removeRestrictNetworkType(int phoneId) {
        return 1 == Settings.Global.getInt(mContext.getContentResolver(),
                    SettingsEx.GlobalEx.RESTRICT_NETWORK_TYPE + phoneId,0);
    }

    /**
     * Special operator requirements like CMCC: Don't show network type option
     * for non-USIM card anyway.
     */
    private boolean isNetworkOptionNotAllowedForNonUSIMCard() {
        if (mContext != null) {
            return  mContext.getResources().getBoolean(R.bool.config_network_option_not_allowed_for_non_USIM_card);
        }
        return false;
    }

    /**
     * As default, network type option should be shown for slot supported
     * multi-mode no matter whether it is primary card. Such as L+W devices. But
     * not now!
     */
    private boolean isNetworkOptionAllowedForNonPrimaryCard() {
        if (mContext != null) {
            return  mContext.getResources().getBoolean(R.bool.config_network_option_not_allowed_for_non_primary_card);
        }
        return true;
    }

    private boolean isAllowedToShowNetworkTypeOption(int subId) {
        final TelephonyManager tm = TelephonyManager.from(mContext).createForSubscriptionId(subId);
        TelephonyManagerEx tmEx = TelephonyManagerEx.from(mContext);
        int phoneId = SubscriptionManager.getPhoneId(subId);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            return false;
        }

        if (isNetworkOptionNotAllowedForNonUSIMCard() && !tmEx.isUsimCard(phoneId)) {
            Log.d(TAG, "Not allowed to show network type option for non-USIM card.");
            return false;
        }

        if (removeRestrictNetworkType(phoneId)) {
            Log.d(TAG,"remove restrict network type");
            return false;
        }

        boolean isPrimaryCard = tm.getSupportedRadioAccessFamily() == tmEx.getMaxRafSupported();
        Log.d(TAG,"tm.getSupportedRadioAccessFamily() ="+tm.getSupportedRadioAccessFamily());
        return isPrimaryCard
                || (!isNetworkOptionAllowedForNonPrimaryCard() && !MobileNetworkUtils.isSupportGSM(mContext,subId));
    }
}

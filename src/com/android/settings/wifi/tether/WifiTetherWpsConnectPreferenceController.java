package com.android.settings.wifi.tether;

import android.content.Context;
import android.icu.text.ListFormatter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import androidx.preference.Preference;

import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration.KeyMgmt;

import com.android.settings.R;
import com.android.settings.widget.HotspotWpsConnectPreference;

public class WifiTetherWpsConnectPreferenceController extends WifiTetherBasePreferenceController {

    private static final String PREF_KEY = "wifi_tether_wps_connect";
    HotspotWpsConnectPreference preference;
    private boolean supportHiddenSsid = WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_HIDE_SSID;
    public WifiTetherWpsConnectPreferenceController (Context context,
            OnTetherConfigUpdateListener listener) {
        super(context, listener);
    }

    @Override
    public void updateDisplay() {
        preference = (HotspotWpsConnectPreference) mPreference;
        if (supportHiddenSsid) {
            if (mHotspotState == WifiManager.WIFI_AP_STATE_ENABLED
                    && mWifiConfig != null
                    && mWifiConfig.getAuthType() != KeyMgmt.NONE
                    && !mWifiConfig.hiddenSSID) {
                preference.setEnabled(true);
            } else {
                preference.setEnabled(false);
            }
        } else {
            if (mHotspotState == WifiManager.WIFI_AP_STATE_ENABLED
                    && mWifiConfig != null
                    && mWifiConfig.getAuthType() != KeyMgmt.NONE) {
                preference.setEnabled(true);
            } else {
                preference.setEnabled(false);
            }
        }
    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

    @Override
    public boolean isAvailable() {
        if (preference != null && mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableSoftApWPS) == false) {
            return false;
        }
        return true;
    }
}
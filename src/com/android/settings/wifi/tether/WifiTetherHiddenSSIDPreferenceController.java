package com.android.settings.wifi.tether;

import android.content.Context;
import android.provider.Settings;
import com.android.settings.R;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.net.wifi.WifiManager;
import androidx.preference.SwitchPreference;
import androidx.preference.Preference;

import android.util.Log;
public class WifiTetherHiddenSSIDPreferenceController extends WifiTetherBasePreferenceController {

    public boolean settingsOn;
    private static final String PREF_KEY = "hotspot_hidden_ssid";

    public WifiTetherHiddenSSIDPreferenceController(Context context,
                OnTetherConfigUpdateListener listener) {
        super(context, listener);
    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public void updateDisplay() {
        settingsOn = mWifiConfig.hiddenSSID;
        ((SwitchPreference) mPreference).setChecked(settingsOn);
        ((SwitchPreference) mPreference).setSummary(R.string.hotspot_hidden_ssid_and_disable_wps_summary);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        settingsOn = (Boolean) newValue;
        if (settingsOn) {
            mWifiConfig.hiddenSSID = true;
        } else {
            mWifiConfig.hiddenSSID = false;
        }
        mListener.onTetherConfigUpdated();
        return true;
    }

    @Override
    public boolean isAvailable() {
        return WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_HIDE_SSID;
    }

    public void refreshHiddenSsidState() {
        if (mHotspotState == WifiManager.WIFI_AP_STATE_DISABLED) {
            ((SwitchPreference) mPreference).setEnabled(true);
        } else {
            ((SwitchPreference) mPreference).setEnabled(false);
        }
    }

    public boolean getIsHiddenSSID() {
        return settingsOn;
    }
}



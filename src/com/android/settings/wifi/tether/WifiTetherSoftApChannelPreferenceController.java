package com.android.settings.wifi.tether;

import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.util.Log;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;

public class WifiTetherSoftApChannelPreferenceController extends WifiTetherBasePreferenceController {

    private static final String PREF_KEY = "ap_channel";
    public static final int DEFAULT_CHANNEL = 0;
    private final String[] mApChannelEntries;
    private final String[] mApChannelEntriesValues;
    private int mApChannelValue = DEFAULT_CHANNEL;
    private static final int HOSTAPD_SUPPORT_2G_CHANNEL_MAX = 14;
    private boolean mHideChannel =true;

    public WifiTetherSoftApChannelPreferenceController(Context context,
            OnTetherConfigUpdateListener listener) {
        super(context, listener);
        mApChannelEntries = mContext.getResources().getStringArray(R.array.wifi_ap_channel);
        mApChannelEntriesValues = mContext.getResources().getStringArray(R.array.wifi_ap_channel_values);
    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public void updateDisplay() {
        final WifiConfiguration config = mWifiManager.getWifiApConfiguration();

        if ((config != null) &&
                (config.apChannel > DEFAULT_CHANNEL && config.apChannel <= HOSTAPD_SUPPORT_2G_CHANNEL_MAX)) {
            mApChannelValue = config.apChannel;
        }

        final ListPreference preference = (ListPreference) mPreference;
        preference.setSummary(getSummaryForApChannelType(mApChannelValue));
        preference.setValueIndex(Integer.parseInt((String)mApChannelEntriesValues[mApChannelValue]));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        int channelValueIndex = Integer.parseInt((String) newValue);
        preference.setSummary(getSummaryForApChannelType(channelValueIndex));
        mApChannelValue = Integer.parseInt((String)mApChannelEntriesValues[channelValueIndex]);
        WifiConfiguration config = mWifiManager.getWifiApConfiguration();
        if (mApChannelValue != config.apChannel) {
            mListener.onTetherConfigUpdated();
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        return !WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_COEXIST_LTE;
    }

    public void updateVisibility(boolean visible) {
        mPreference.setVisible(visible);
    }

    public int getApChannelType() {
        return mApChannelValue;
    }

    private String getSummaryForApChannelType(int apChannelIndex) {
        return mApChannelEntries[apChannelIndex];
    }
}



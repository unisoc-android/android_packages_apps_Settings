package com.android.settings.wifi.tether;

import android.content.Context;
import android.provider.Settings;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.util.Log;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;

import java.util.ArrayList;

public class WifiTetherSoftAp5GChannelPreferenceController extends WifiTetherBasePreferenceController {

    private static final String TAG = "WifiTetherAp5GChannelPref";
    private static final String PREF_KEY = "ap_5g_channel";
    private static String AUTO;
    public static final int DEFAULT_CHANNEL = 0;
    private static final int HOSTAPD_SUPPORT_2G_CHANNEL_MAX = 14;
    private int mApChannelValue = DEFAULT_CHANNEL;
    private CharSequence[] mApChannelEntries = null;  // {"Auto","36","40","48","149","153","157","161","165"}
    private CharSequence[] mApChannelEntryValues = null;
    private String mSoftApSupportChannels = null;
    private String m5GChannels[] = null;
    private final Context mContext;

    public WifiTetherSoftAp5GChannelPreferenceController(Context context,
            OnTetherConfigUpdateListener listener) {
        super(context, listener);
        mContext = context;
        AUTO = mContext.getString(R.string.hotspot_channel_auto);
        getSoftapSupportChannels();
    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public void updateDisplay() {
        final WifiConfiguration config = mWifiManager.getWifiApConfiguration();

        if (config != null && config.apChannel > HOSTAPD_SUPPORT_2G_CHANNEL_MAX) {
            Log.d(TAG, "config.apChannel:" + config.apChannel);
            mApChannelValue = config.apChannel;
        }

        final ListPreference preference = (ListPreference) mPreference;
        preference.setEntries(mApChannelEntries);
        preference.setEntryValues(mApChannelEntryValues);
        preference.setSummary(getSummaryForApChannelType(getIndex()));
        preference.setValueIndex(getIndex());
        Log.d(TAG,"updateDisplay index="+getIndex());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        mApChannelValue = Integer.parseInt((String) newValue);
        preference.setSummary(getSummaryForApChannelType(getIndex()));
        WifiConfiguration config = mWifiManager.getWifiApConfiguration();
        if (mApChannelValue != config.apChannel) {
            mListener.onTetherConfigUpdated();
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        return !WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_HIDE_5G_CHANNEL;
    }


    public void updateVisibility(boolean visible) {
        mPreference.setVisible(visible);
    }

    public int getApChannelType() {
        return mApChannelValue;
    }

    private String getSummaryForApChannelType(int ApChannelIndex) {
        Log.d(TAG,"getSummaryForApChannelType ApChannelIndex= "+ApChannelIndex);
        return mApChannelEntries[ApChannelIndex].toString();
    }

    private int getIndex() {
        int index = 0;
        if (m5GChannels != null) {
            for (int i = 0 ; i < m5GChannels.length; i++) {
                if (mSoftApSupportChannels != null) {
                    int channel = Integer.parseInt(m5GChannels[i]);
                    if (mApChannelValue == channel) {
                        index = i + 1;
                        break;
                    }
                }
            }
        }
        return index;
    }

    private void getSoftapSupportChannels() {
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();

        mSoftApSupportChannels = Settings.Global.getString(
                mContext.getContentResolver(), WifiFeaturesUtils.SOFTAP_SUPPORT_CHANNELS);
        Log.d(TAG, "mSoftApSupportChannels:" + mSoftApSupportChannels);
        if (mSoftApSupportChannels != null && mSoftApSupportChannels.contains(",")) {
            m5GChannels = mSoftApSupportChannels.split(",");
        } else {
            m5GChannels = null;
        }

        entries.add(AUTO);
        entryValues.add("0");
        if (mSoftApSupportChannels != null && m5GChannels != null) {
            for (int i = 0 ; i < m5GChannels.length; i++) {
                entries.add(m5GChannels[i]);
                entryValues.add(m5GChannels[i]);
            }
        }

        mApChannelEntries = entries.toArray(new CharSequence[entries.size()]);
        mApChannelEntryValues = entryValues.toArray(new CharSequence[entryValues.size()]);
    }
}



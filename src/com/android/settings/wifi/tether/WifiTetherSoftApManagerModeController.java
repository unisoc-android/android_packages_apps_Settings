package com.android.settings.wifi.tether;

import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.database.ContentObserver;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;

public class WifiTetherSoftApManagerModeController extends WifiTetherBasePreferenceController {

    private static final String PREF_KEY = "hotspot_mode";
    private final String[] mHotspotModeEntries;
    private static final int HOTSPOT_NARMAL_MODE = 0;
    private static final int HOTSPOT_WHITELIST_MODE = 1;
    private int mHotspotModeValue = 0;
    private boolean mSoftApModeChanged = false;
    public WifiTetherSoftApManagerModeController(Context context,
            OnTetherConfigUpdateListener listener) {
        super(context, listener);
        mHotspotModeEntries = mContext.getResources().getStringArray(R.array.hotspot_mode);
    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public void updateDisplay() {
        final ListPreference preference = (ListPreference) mPreference;
        mHotspotModeValue = mWifiManager.softApIsWhiteListEnabled() ? HOTSPOT_WHITELIST_MODE : HOTSPOT_NARMAL_MODE;
        preference.setSummary(getSummaryForHotspotModeType(mHotspotModeValue));
        preference.setValue(String.valueOf(mHotspotModeValue));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        mHotspotModeValue = Integer.parseInt((String) newValue);
        preference.setSummary(getSummaryForHotspotModeType(mHotspotModeValue));
        mWifiManager.softApSetClientWhiteListEnabled(mHotspotModeValue==1);
        mSoftApModeChanged = true;
        mListener.onTetherConfigUpdated();
        mSoftApModeChanged = false;
        return true;
    }

    @Override
    public boolean isAvailable() {
        return WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_WHITE_LIST;
    }

    public int getHotspotModeType() {
        return mHotspotModeValue;
    }

    public boolean isSoftApModeChanged() {
        return mSoftApModeChanged;
    }

    private String getSummaryForHotspotModeType(int type) {
        return mHotspotModeEntries[type];
    }
}


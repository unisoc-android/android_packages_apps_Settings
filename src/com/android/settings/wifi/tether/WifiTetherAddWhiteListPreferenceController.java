package com.android.settings.wifi.tether;

import android.content.Context;
import android.content.res.Resources;
import android.icu.text.ListFormatter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.util.Log;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.widget.HotspotAddWhiteListPreference;

public class WifiTetherAddWhiteListPreferenceController extends WifiTetherBasePreferenceController {

    private static final String PREF_KEY = "wifi_tether_add_whiltelist";

    public WifiTetherAddWhiteListPreferenceController(Context context,
            OnTetherConfigUpdateListener listener) {
        super(context, listener);
        Resources res = mContext.getResources();
    }

    @Override
    public void updateDisplay() {
       HotspotAddWhiteListPreference preference =
               (HotspotAddWhiteListPreference) mPreference;

    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = (Boolean)newValue;
        if (value) {
            mListener.onTetherConfigUpdated();
        }
        return true;
    }

}



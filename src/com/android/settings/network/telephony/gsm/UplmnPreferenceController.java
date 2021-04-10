
package com.android.settings.network.telephony.gsm;


import android.content.Context;
import android.content.Intent;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.network.telephony.TelephonyBasePreferenceController;
import com.android.settings.network.telephony.MobileNetworkUtils;

/**
 * UNISOC: FL0108090007 Preference controller for "UPLMN Preference"
 */
public class UplmnPreferenceController extends TelephonyBasePreferenceController {
    private Preference mPreference;


    public UplmnPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        return MobileNetworkUtils.isSupportUplmn(subId)? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (getPreferenceKey().equals(preference.getKey())) {
            // This activity runs in phone process, we must use intent to start
            final Intent intent = new Intent("android.uplmnsettings.action.startuplmnsettings");
            intent.putExtra("sub_id", mSubId);
            mContext.startActivity(intent);
            return true;
        }

        return false;
    }

    public void init(int subId) {
        mSubId = subId;
    }

    @VisibleForTesting
    void setPreference(Preference preference) {
        mPreference = preference;
    }

}


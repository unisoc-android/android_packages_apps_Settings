
package com.android.settings.network.telephony;

import android.content.Context;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.settings.R;

/**
 * Preference controller for "National Data Roaming"
 */
public class NationalRoamingPreferenceController extends TelephonyBasePreferenceController
        implements ListPreference.OnPreferenceChangeListener {

    private CarrierConfigManager mCarrierConfigManager;
    private PersistableBundle mPersistableBundle;

    public static final int NATIONAL_ROAMING_TYPE_DISABLED = 0;
    public static final int NATIONAL_ROAMING_TYPE_ALL_NETWORKS = 1;
    public static final int NATIONAL_ROAMING_TYPE_NATIONAL_ROAMING_ONLY= 2;

    public NationalRoamingPreferenceController(Context context, String key) {
        super(context, key);
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        int visible;
        if (mContext.getResources().getBoolean(com.android.internal.R.bool.national_data_roaming)) {
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                visible = AVAILABLE;
            } else {
                visible = AVAILABLE_UNSEARCHABLE;
            }
        } else {
            visible = CONDITIONALLY_UNAVAILABLE;
        }
        return visible;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        final ListPreference listPreference = (ListPreference) preference;
        final int roamingType = getNationalRoamingType();
        listPreference.setValue(Integer.toString(roamingType));
        listPreference.setSummary(getPreferredNationalRoamingTypeSummaryResId(roamingType));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object object) {
        final int roamingType = Integer.parseInt((String) object);
        final ListPreference listPreference = (ListPreference) preference;

            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.DATA_ROAMING + mSubId,
                    roamingType);
            listPreference.setValue(Integer.toString(roamingType));
            listPreference.setSummary(getPreferredNationalRoamingTypeSummaryResId(roamingType));
            return true;
    }

    public void init(int subId) {
        mSubId = subId;
    }

    private int getNationalRoamingType() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DATA_ROAMING + mSubId,0);
    }

    private int getPreferredNationalRoamingTypeSummaryResId(int roamingType) {
        switch (roamingType) {
            case NATIONAL_ROAMING_TYPE_DISABLED:
                return R.string.preferred_data_roaming_disable;
            case NATIONAL_ROAMING_TYPE_NATIONAL_ROAMING_ONLY:
                return R.string.preferred_data_roaming_national;
            case NATIONAL_ROAMING_TYPE_ALL_NETWORKS:
                return R.string.preferred_data_roaming_all_networks;
            default:
                return R.string.preferred_data_roaming_disable;
        }
    }
}


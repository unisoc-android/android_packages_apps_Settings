package com.android.settings.network.telephony;

import android.content.Context;
import android.content.Intent;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.settings.R;

import com.android.settings.smartcallforward.SmartDualSIMActivity;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;

/**
 * UNISOC: porting for bug1072778 smart dual Subscription
 */
public class SmartDualSubscriptionController extends TelephonyBasePreferenceController implements
        LifecycleObserver, OnStart, OnStop {


    private static final String LOG_TAG = "SmartDualSubscriptionController";
    protected static final String KEY = "smart_dual_sim";
    int mAvailabilityStatus = UNSUPPORTED_ON_DEVICE;
    private SubscriptionManager mSubscriptionManager;
    private Preference mPreference;
    private Intent mIntent;
    SmartDualSubscriptionsChangedListener mOnSubscriptionsChangeListener = null;

    public SmartDualSubscriptionController(Context context, String key) {
        super(context, key);
        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);

        //UNISOC: modify by bug1146882
        if (mContext.getResources().getBoolean(R.bool.config_show_smartdualsim)) {
            mAvailabilityStatus = DISABLED_DEPENDENT_SETTING;
        }
        Log.d(LOG_TAG,
                "SmartDualSubscriptionController mAvailabilityStatus: "
                        + mAvailabilityStatus);
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        return mAvailabilityStatus;
    }


    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setVisible(isAvailable());
        if (mAvailabilityStatus == DISABLED_DEPENDENT_SETTING) {
            preference.setEnabled(false);
        } else if (mAvailabilityStatus == AVAILABLE) {
            preference.setEnabled(true);
        }
    }

    class SmartDualSubscriptionsChangedListener
            extends SubscriptionManager.OnSubscriptionsChangedListener{

        @Override
        public void onSubscriptionsChanged() {
            refreshSmartDualSimStatus();
            updateState(mPreference);
        }
    }

    @Override
    public void onStart() {
        mIntent = new Intent();
        mIntent.setClass(mContext, SmartDualSIMActivity.class);
        if (mOnSubscriptionsChangeListener != null
                && mAvailabilityStatus != UNSUPPORTED_ON_DEVICE) {
            mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        }
    }

    @Override
    public void onStop() {
        if (mOnSubscriptionsChangeListener != null
                && mAvailabilityStatus != UNSUPPORTED_ON_DEVICE) {
            mSubscriptionManager
                    .removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        }
    }

    public void init(int subId) {
        mSubId = subId;
        mOnSubscriptionsChangeListener = new SmartDualSubscriptionsChangedListener();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            mContext.startActivity(mIntent);
            return true;
        }
        return false;
    }

    /* UNISOC: add for new featrue:Smart Dual SIM @{ */
    private void refreshSmartDualSimStatus() {
        if (mAvailabilityStatus != UNSUPPORTED_ON_DEVICE) {
            List<SubscriptionInfo> availableSubInfoList = getActiveSubInfoList();
            if ((availableSubInfoList != null)
                    && (availableSubInfoList.size() > 1)) {
                mAvailabilityStatus = AVAILABLE;
            } else {
                mAvailabilityStatus = DISABLED_DEPENDENT_SETTING;
            }
        }
        Log.d(LOG_TAG, "refreshSmartDualSimStatus mAvailabilityStatus: " + mAvailabilityStatus);
    }

    private List<SubscriptionInfo> getActiveSubInfoList() {
        if (mSubscriptionManager == null) {
            return new ArrayList<SubscriptionInfo>();
        }
        List<SubscriptionInfo> availableSubInfoList = mSubscriptionManager
                .getActiveSubscriptionInfoList();
        if (availableSubInfoList == null) {
            return new ArrayList<SubscriptionInfo>();
        }
        Iterator<SubscriptionInfo> iterator = availableSubInfoList.iterator();
        TelephonyManager telephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        while (iterator.hasNext()) {
            SubscriptionInfo subInfo = iterator.next();
            int phoneId = subInfo.getSimSlotIndex();
            boolean isSimReady = telephonyManager
                    .getSimState(phoneId) == TelephonyManager.SIM_STATE_READY;
            if (!isSimReady) {
                iterator.remove();
            }
        }
        return availableSubInfoList;
    }
    /* @} */
}

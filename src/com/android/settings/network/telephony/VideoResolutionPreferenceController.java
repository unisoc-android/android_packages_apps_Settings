
package com.android.settings.network.telephony;

import android.content.Context;
import android.os.PersistableBundle;
import android.os.Looper;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import com.android.ims.ImsManager;
import com.android.ims.ImsConfig;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import android.util.Log;

/**
 * Preference controller for "Preferred network mode"
 */
public class VideoResolutionPreferenceController extends TelephonyBasePreferenceController implements
        LifecycleObserver, OnStart, OnStop,
        ListPreference.OnPreferenceChangeListener,
        Enhanced4gBasePreferenceController.On4gLteUpdateListener {

    private static final String LOG_TAG = "VideoResolutionPreferenceController";
    private Preference mPreference;
    public static final String VT_RESOLUTION = "vt_resolution";
    private CarrierConfigManager mCarrierConfigManager;
    private PersistableBundle mCarrierConfig;
    private TelephonyManager mTelephonyManager;
    ImsManager mImsManager;
    ImsConfig mImsConfig;
    private PhoneCallStateListener mPhoneStateListener;

    public VideoResolutionPreferenceController(Context context, String key) {
        super(context, key);
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mPhoneStateListener = new PhoneCallStateListener(Looper.getMainLooper());
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        init(subId);
        final boolean isVisible = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && mImsManager != null
                && mCarrierConfig != null
                && mImsManager.isVtEnabledByPlatform()
                && mImsManager.isVtProvisionedOnDevice()
                && MobileNetworkUtils.isImsServiceStateReady(mImsManager)
                && (mCarrierConfig.getBoolean(CarrierConfigManager.KEY_IGNORE_DATA_ENABLED_CHANGED_FOR_VIDEO_CALLS)
                        || mTelephonyManager.isDataEnabled());
        return isVisible
                ? (isVideoResolutionPrefEnabled() ? AVAILABLE : AVAILABLE_UNSEARCHABLE)
                        : CONDITIONALLY_UNAVAILABLE;
    }

    private boolean isVideoResolutionPrefEnabled() {
        return mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && mImsManager != null
                && mImsManager.isEnhanced4gLteModeSettingEnabledByUser()
                && mTelephonyManager.getCallState(mSubId) == TelephonyManager.CALL_STATE_IDLE
                && mImsManager.isNonTtyOrTtyOnVolteEnabled()
                && mCarrierConfig.getBoolean(
                CarrierConfigManagerEx.KEY_EDITABLE_VT_RESOLUTION_BOOL);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (!(preference instanceof ListPreference)) {
            return;
        }

        final ListPreference listPreference = (ListPreference) preference;
        getVideoQualityFromPreference(listPreference);
        if (isAvailable()) {
            listPreference.setEnabled(isVideoResolutionPrefEnabled());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object object) {
        if (!(preference instanceof ListPreference)) {
            return false;
        }

        final int videoQuality = Integer.parseInt((String) object);
        final ListPreference listPreference = (ListPreference) preference;
        try {
            mImsConfig.setConfig(ImsConfig.ConfigConstants.VIDEO_QUALITY, videoQuality + 1);
        } catch (Exception ie) {

        }
        listPreference.setValueIndex(videoQuality);
        listPreference.setSummary(listPreference.getEntry());
        return true;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
        mPhoneStateListener.register(mSubId);
    }

    @Override
    public void onStop() {
        mPhoneStateListener.unregister();
    }

    @Override
    public void on4gLteUpdated() {
        updateState(mPreference);
    }

    private class PhoneCallStateListener extends PhoneStateListener {

        public PhoneCallStateListener(Looper looper) {
            super(looper);
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            updateState(mPreference);
        }

        public void register(int subId) {
            mSubId = subId;
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE);
        }

        public void unregister() {
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
        }
    }

    public VideoResolutionPreferenceController init (int subId) {
        if (mSubId == subId && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return this;
        }
        mSubId = subId;
        mCarrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mImsManager = ImsManager.getInstance(mContext, SubscriptionManager.getPhoneId(mSubId));
        }
        try {
            if (mImsManager != null) {
                mImsConfig = mImsManager.getConfigInterface();
            }
        } catch (Exception ie) {
            Log.d(LOG_TAG, "Get ImsConfig occour exception =" + ie);
        }

        return this;
    }

    private void getVideoQualityFromPreference(ListPreference preference) {
        try {
            if (mImsConfig == null) {
                Log.d(LOG_TAG, "getVideoQualityFromPreference mImsConfig is null");
                return;
            }
            int quality = mImsConfig.getConfigInt(ImsConfig.ConfigConstants.VIDEO_QUALITY);
            Log.d(LOG_TAG, "onGetVideoQuality quality = " + quality);
            preference.setValueIndex(quality - 1);
            preference.setSummary(preference.getEntry());
        } catch (Exception e) {
            Log.d(LOG_TAG, "getVideoQualityFromPreference exception : " + e.getMessage());
            e.printStackTrace();
        }
    }

}

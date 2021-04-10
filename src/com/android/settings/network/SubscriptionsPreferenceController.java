/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.network;

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import static com.android.settings.network.telephony.MobileNetworkUtils.NO_CELL_DATA_TYPE_ICON;

import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Looper;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.telecom.TelecomManager;
import android.util.ArraySet;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.collection.ArrayMap;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import com.android.internal.telephony.TeleUtils;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.network.telephony.BroadcastReceiverChanged;
import com.android.settings.network.telephony.DataConnectivityListener;
import com.android.settings.network.telephony.MobileNetworkActivity;
import com.android.settings.network.telephony.MobileNetworkUtils;
import com.android.settings.network.telephony.SignalStrengthListener;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.net.SignalStrengthUtil;
import com.android.settingslib.WirelessUtils;
import com.android.sprd.telephony.RadioInteractor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * This manages a set of Preferences it places into a PreferenceGroup owned by some parent
 * controller class - one for each available subscription. This controller is only considered
 * available if there are 2 or more subscriptions.
 */
public class SubscriptionsPreferenceController extends AbstractPreferenceController implements
        LifecycleObserver, SubscriptionsChangeListener.SubscriptionsChangeListenerClient,
        MobileDataEnabledListener.Client, DataConnectivityListener.Client,
        SignalStrengthListener.Callback, RealSimStateChangedListener.RealSimStateChangeListenerClient,
        BroadcastReceiverChanged.BroadcastReceiverChangedClient {
    private static final String TAG = "SubscriptionsPrefCntrlr";
    private UpdateListener mUpdateListener;
    private String mPreferenceGroupKey;
    private PreferenceGroup mPreferenceGroup;
    private SubscriptionManager mManager;
    private ConnectivityManager mConnectivityManager;
    private SubscriptionsChangeListener mSubscriptionsListener;
    private MobileDataEnabledListener mDataEnabledListener;
    private DataConnectivityListener mConnectivityListener;
    private SignalStrengthListener mSignalStrengthListener;

    // Map of subscription id to Preference
    private Map<Integer, SimPreference> mSubscriptionPreferences;
    private int mStartOrder;
    /* UNISOC:Improve the function of turning on and off the Sub {@*/
    private int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
    private RealSimStateChangedListener mRealSimStateChangedListener;
    private RadioBusyContentObserver mRadioBusyListener;
    private RadioInteractor mRadioInteractor;
    private TelephonyManagerEx mTeleMgrEx;
    protected FragmentManager mFragmentManager;
    private int[] mSignalLevel = new int[mPhoneCount];
    private boolean[] mSwitchHasChanged = new boolean[mPhoneCount];
    private boolean[] mIsChecked = new boolean[mPhoneCount];
    private SimSlotEnableDialogFragment mAlertDialogFragment;
    private BroadcastReceiverChanged mBroadcastReceiverClient;
    /* @} */
    /**
     * This interface lets a parent of this class know that some change happened - this could
     * either be because overall availability changed, or because we've added/removed/updated some
     * preferences.
     */
    public interface UpdateListener {
        void onChildrenUpdated();
    }

    /**
     * @param context            the context for the UI where we're placing these preferences
     * @param lifecycle          for listening to lifecycle events for the UI
     * @param updateListener     called to let our parent controller know that our availability has
     *                           changed, or that one or more of the preferences we've placed in the
     *                           PreferenceGroup has changed
     * @param preferenceGroupKey the key used to lookup the PreferenceGroup where Preferences will
     *                           be placed
     * @param startOrder         the order that should be given to the first Preference placed into
     *                           the PreferenceGroup; the second will use startOrder+1, third will
     *                           use startOrder+2, etc. - this is useful for when the parent wants
     *                           to have other preferences in the same PreferenceGroup and wants
     *                           a specific ordering relative to this controller's prefs.
     */
    public SubscriptionsPreferenceController(Context context, Lifecycle lifecycle,
            UpdateListener updateListener, String preferenceGroupKey, int startOrder) {
        super(context);
        mUpdateListener = updateListener;
        mPreferenceGroupKey = preferenceGroupKey;
        mStartOrder = startOrder;
        mManager = context.getSystemService(SubscriptionManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mSubscriptionPreferences = new ArrayMap<>();
        mSubscriptionsListener = new SubscriptionsChangeListener(context, this);
        mDataEnabledListener = new MobileDataEnabledListener(context, this);
        mConnectivityListener = new DataConnectivityListener(context, this);
        mSignalStrengthListener = new SignalStrengthListener(context, this);
        lifecycle.addObserver(this);
    }

    @OnLifecycleEvent(ON_RESUME)
    public void onResume() {
        mSubscriptionsListener.start();
        mDataEnabledListener.start(SubscriptionManager.getDefaultDataSubscriptionId());
        mConnectivityListener.start();
        mSignalStrengthListener.resume();
        mRealSimStateChangedListener.start();
        mRadioBusyListener.register(mPreferenceGroup.getContext());
        mBroadcastReceiverClient.start();
        update();
    }

    @OnLifecycleEvent(ON_PAUSE)
    public void onPause() {
        mSubscriptionsListener.stop();
        mDataEnabledListener.stop();
        mConnectivityListener.stop();
        mSignalStrengthListener.pause();
        mRealSimStateChangedListener.stop();
        mRadioBusyListener.unRegister(mPreferenceGroup.getContext());
        mBroadcastReceiverClient.stop();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mPreferenceGroup = screen.findPreference(mPreferenceGroupKey);
        update();
    }

    private void update() {
        if (mPreferenceGroup == null) {
            return;
        }
        /* UNISOC:Improve the function of turning on and off the Sub {@*/
        boolean isAirplaneModeOn = WirelessUtils.isAirplaneModeOn(mContext);
        if (isAirplaneModeOn || !isCallStateIdle() || !isAvailable() || isAnySimDisable()) {
            if (mAlertDialogFragment != null) {
                mAlertDialogFragment.dismissAllowingStateLoss();
            }
        }
        /* @} */

        if (!isAvailable()) {
            for (Preference pref : mSubscriptionPreferences.values()) {
                mPreferenceGroup.removePreference(pref);
            }
            mSubscriptionPreferences.clear();
            mSignalStrengthListener.updateSubscriptionIds(Collections.emptySet());
            mUpdateListener.onChildrenUpdated();
            return;
        }

        final Map<Integer, SimPreference> existingPrefs = mSubscriptionPreferences;
        mSubscriptionPreferences = new ArrayMap<>();

        int order = mStartOrder;
        final Set<Integer> activeSubIds = new ArraySet<>();
        final int dataDefaultSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        /* UNISOC:Improve the function of turning on and off the Sub {@*/
//        for (SubscriptionInfo info : SubscriptionUtil.getActiveSubscriptions(mManager)) {
        for (int phoneId = 0; phoneId < mPhoneCount; ++ phoneId) {
            SubscriptionInfo info = mManager.getActiveSubscriptionInfoForSimSlotIndex(phoneId);
            final int subId = (info != null ? info.getSubscriptionId() : (SubscriptionManager.INVALID_SUBSCRIPTION_ID - phoneId));
            activeSubIds.add(subId);
            SimPreference pref = existingPrefs.remove(subId);
            if (pref == null) {
                pref = new SimPreference(mPreferenceGroup.getContext(), info, phoneId);
                mPreferenceGroup.addPreference(pref);
            }
            if (subId != (SubscriptionManager.INVALID_SUBSCRIPTION_ID - phoneId)) {
                pref.setTitle(info.getDisplayName());
                final boolean isDefaultForData = (subId == dataDefaultSubId);
                pref.setSummary(getSummary(subId, isDefaultForData));
                setIcon(pref, subId, isDefaultForData);
                pref.setOnPreferenceClickListener(clickedPref -> {
                    final Intent intent = new Intent(mContext, MobileNetworkActivity.class);
                    intent.putExtra(Settings.EXTRA_SUB_ID, subId);
                    mContext.startActivity(intent);
                    return true;
                });
            }
            /* @} */

            pref.setOrder(order++);
            pref.updateSubInfo();

            mSubscriptionPreferences.put(subId, pref);
        }
        mSignalStrengthListener.updateSubscriptionIds(activeSubIds);

        // Remove any old preferences that no longer map to a subscription.
        for (Preference pref : existingPrefs.values()) {
            mPreferenceGroup.removePreference(pref);
        }
        mUpdateListener.onChildrenUpdated();
    }

    @VisibleForTesting
    boolean shouldInflateSignalStrength(int subId) {
        return SignalStrengthUtil.shouldInflateSignalStrength(mContext, subId);
    }

    @VisibleForTesting
    void setIcon(Preference pref, int subId, boolean isDefaultForData) {
        final TelephonyManager mgr = mContext.getSystemService(
                TelephonyManager.class).createForSubscriptionId(subId);
        final SignalStrength strength = mgr.getSignalStrength();
        int level = (strength == null) ? 0 : strength.getLevel();
        int numLevels = SignalStrength.NUM_SIGNAL_STRENGTH_BINS;
        if (shouldInflateSignalStrength(subId)) {
            level += 1;
            numLevels += 1;
        }
        final boolean showCutOut = !isDefaultForData || !mgr.isDataEnabled();
        pref.setIcon(getIcon(level, numLevels, showCutOut));
    }

    @VisibleForTesting
    Drawable getIcon(int level, int numLevels, boolean cutOut) {
        return MobileNetworkUtils.getSignalStrengthIcon(mContext, level, numLevels,
                NO_CELL_DATA_TYPE_ICON, cutOut);
    }

    private boolean activeNetworkIsCellular() {
        final Network activeNetwork = mConnectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        final NetworkCapabilities networkCapabilities = mConnectivityManager.getNetworkCapabilities(
                activeNetwork);
        if (networkCapabilities == null) {
            return false;
        }
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    /**
     * The summary can have either 1 or 2 lines depending on which services (calls, SMS, data) this
     * subscription is the default for.
     *
     * If this subscription is the default for calls and/or SMS, we add a line to show that.
     *
     * If this subscription is the default for data, we add a line with detail about
     * whether the data connection is active.
     *
     * If a subscription isn't the default for anything, we just say it is available.
     */
    protected String getSummary(int subId, boolean isDefaultForData) {
        final int callsDefaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        final int smsDefaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId();

        String line1 = getPrimaryTagDisplay(subId);
        if (subId == callsDefaultSubId && subId == smsDefaultSubId) {
            line1 += mContext.getString(R.string.default_for_calls_and_sms);
        } else if (subId == callsDefaultSubId) {
            line1 += mContext.getString(R.string.default_for_calls);
        } else if (subId == smsDefaultSubId) {
            line1 += mContext.getString(R.string.default_for_sms);
        }

        String line2 = null;
        if (isDefaultForData) {
            final TelephonyManager telMgrForSub = mContext.getSystemService(
                    TelephonyManager.class).createForSubscriptionId(subId);
            final boolean dataEnabled = telMgrForSub.isDataEnabled();
            if (dataEnabled && activeNetworkIsCellular()) {
                line2 = mContext.getString(R.string.mobile_data_active);
            } else if (!dataEnabled) {
                line2 = mContext.getString(R.string.mobile_data_off);
            } else {
                line2 = mContext.getString(R.string.default_for_mobile_data);
            }
        }

        if (line1 != null && line2 != null) {
            return String.join(System.lineSeparator(), line1, line2);
        } else if (line1 != null) {
            return line1;
        } else if (line2 != null) {
            return line2;
        } else {
            return mContext.getString(R.string.subscription_available);
        }
    }

    // UNISOC:Add feature for primary and secondary sub distinguishing identifier
    public String getPrimaryTagDisplay(int subId) {
        boolean isShowTag = mContext.getResources().getBoolean(R.bool.config_show_primary_sub_tag);
        if (isShowTag) {
            return SubscriptionManager.getDefaultDataSubscriptionId() == subId
                    ? mContext.getResources().getString(R.string.primary_sub_summary) : mContext.getResources().getString(R.string.secondary_sub_summary);
        }
        return "";
    }
    /**
     * @return true if there are at least 2 available subscriptions.
     */
    @Override
    public boolean isAvailable() {
        if (mSubscriptionsListener.isAirplaneModeOn()) {
            return false;
        }
        return SubscriptionUtil.getActiveSubscriptions(mManager).size() >= 2
                // UNISOC:Improve the function of turning on and off the Sub
                || getRealSimCount() > 1
                || isAnySimDisable();
    }

    @Override
    public String getPreferenceKey() {
        return null;
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled) {
        update();
    }

    @Override
    public void onSubscriptionsChanged() {
        // See if we need to change which sub id we're using to listen for enabled/disabled changes.
        int defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (defaultDataSubId != mDataEnabledListener.getSubId()) {
            mDataEnabledListener.stop();
            mDataEnabledListener.start(defaultDataSubId);
        }
        update();
    }

    @Override
    public void onMobileDataEnabledChange() {
        Log.d(TAG,"onMobileDataEnabledChange");
        update();
    }

    @Override
    public void onDataConnectivityChange() {
        Log.d(TAG,"onDataConnectivityChange");
        update();
    }

    @Override
    public void onSignalStrengthChanged() {
        // SignalStrength changed too frequently,reduce the frequency of UI refreshes
        boolean isSignalStrengthChanged = false;
        for (int i = 0;i < mPhoneCount ; i ++) {
            int subIds[] = mManager.getSubscriptionIds(i);
            if (subIds != null) {
                TelephonyManager mgr = mContext.getSystemService(
                        TelephonyManager.class).createForSubscriptionId(subIds[0]);
                SignalStrength strength = mgr.getSignalStrength();
                int level = (strength == null) ? 0 : strength.getLevel();
                if (mSignalLevel[i] != level) {
                    isSignalStrengthChanged = true;;
                }
                mSignalLevel[i] = level;
            }
        }
        Log.d(TAG,"onSignalStrengthChanged isSignalStrengthChanged =" + isSignalStrengthChanged);
        if (isSignalStrengthChanged) {
            update();
        }
    }

    /* UNISOC:Improve the function of turning on and off the Sub {@*/
    public SubscriptionsPreferenceController(Context context, Lifecycle lifecycle,
            UpdateListener updateListener, String preferenceGroupKey, int startOrder,
            FragmentManager fragmentManager) {
        super(context);
        mUpdateListener = updateListener;
        mPreferenceGroupKey = preferenceGroupKey;
        mStartOrder = startOrder;
        mManager = context.getSystemService(SubscriptionManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mSubscriptionPreferences = new ArrayMap<>();
        mSubscriptionsListener = new SubscriptionsChangeListener(context, this);
        mDataEnabledListener = new MobileDataEnabledListener(context, this);
        mConnectivityListener = new DataConnectivityListener(context, this);
        mSignalStrengthListener = new SignalStrengthListener(context, this);
        mRealSimStateChangedListener = new RealSimStateChangedListener(context,this);
        mBroadcastReceiverClient = new BroadcastReceiverChanged(context,this);
        mRadioBusyListener = new RadioBusyContentObserver(new Handler(Looper.getMainLooper()));
        mRadioBusyListener.setOnRadioBusyChangedListener(() -> update());
        mRadioInteractor = new RadioInteractor(context);
        mTeleMgrEx = TelephonyManagerEx.from(context);
        mFragmentManager = fragmentManager;
        lifecycle.addObserver(this);
    }

    @Override
    public void notifyRealSimStateChanged(int phoneId) {
        Log.d(TAG,"notifyRealSimStateChanged");
        update();
    }

    @Override
    public void onPhoneStateChanged() {
        Log.d(TAG,"onPhoneStateChanged");
        update();
    }

    @Override
    public void onCarrierConfigChanged(int phoneId) {}

    private int getRealSimCount() {
        int realSimCount = 0;
        for (int i = 0 ;i < mPhoneCount ; i++ ) {
            if (mRadioInteractor.getRealSimSatus(i) != 0) {
                realSimCount ++;
            }
        }
        return realSimCount;
    }

    private boolean isAnySimDisable() {
        for (int i = 0 ;i < mPhoneCount ; i++ ) {
            if (!mTeleMgrEx.isSimEnabled(i)
                    && mRadioInteractor.getRealSimSatus(i) != 0) {
                return true;
            }
        }
        return false;
    }

    private class SimPreference extends Preference implements SimSlotEnableDialogFragment.SimSlotEnableDialogFragmentListener {
        private SubscriptionInfo mSubInfoRecord;
        private int mSlotId;
        private Context mContext;
        private Switch mSwitch;
        boolean mIsSimExist;

        public SimPreference(Context context, SubscriptionInfo subInfoRecord, int slotId) {
            super(context);
            setLayoutResource(R.layout.sim_preference);

            mContext = context;
            mSubInfoRecord = subInfoRecord;
            mSlotId = slotId;
            setKey("sim" + mSlotId);
            mIsSimExist = mRadioInteractor.getRealSimSatus(mSlotId) != 0;
            Log.d(TAG, "SimPreference[" + slotId + "]: " + mIsSimExist);
            updateSubInfo();
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            final View divider = holder.findViewById(R.id.two_target_divider);
            final View widgetFrame = holder.findViewById(android.R.id.widget_frame);
            if (divider != null) {
                divider.setVisibility(!mIsSimExist ? View.GONE : View.VISIBLE);
            }
            if (widgetFrame != null) {
                widgetFrame.setVisibility(!mIsSimExist ? View.GONE : View.VISIBLE);
            }
            mSwitch = (Switch) holder.findViewById(R.id.switchWidget);
            if (mSwitch != null) {
                mSwitch.setOnClickListener(null);
                mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Log.d(TAG, "onCheckedChanged[" + mSlotId + "]: " + isChecked);
                        if(mAlertDialogFragment != null) return;
                        if (isChecked != mTeleMgrEx.isSimEnabled(mSlotId)) {
                            int simActiveCount = 0;
                            for (int i = 0; i < mPhoneCount; i++) {
                                boolean isSimExit = mRadioInteractor.getRealSimSatus(i) != 0;
                                if (mTeleMgrEx.isSimEnabled(i) && isSimExit) {
                                    simActiveCount++;
                                }
                            }
                            //UNISOC:modify for single card can not be disabled
                            if (!isChecked && simActiveCount < 2) {
                                mSwitch.setChecked(mTeleMgrEx.isSimEnabled(mSlotId));
                                Toast.makeText(mContext, R.string.cannot_disable_two_sim_card, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            mSwitchHasChanged[mSlotId] = true;
                            mIsChecked[mSlotId] = isChecked;
                            showAlertDialog(isChecked);
                        }
                    }
                });
            }
            updateSwitchState();
        }

        public void updateSubInfo() {
            mIsSimExist = mRadioInteractor.getRealSimSatus(mSlotId) != 0;
            if (mSubInfoRecord != null) {
                setEnabled(!TeleUtils.isRadioBusy(mContext));
            } else if (mIsSimExist && !mTeleMgrEx.isSimEnabled(mSlotId)) {
                setSummary(R.string.sim_disabled);
                setFragment(null);
                setEnabled(false);
            } else {
                setSummary(R.string.sim_slot_empty);
                setFragment(null);
                setEnabled(false);
            }
            updateSwitchState();
        }

        @Override
        public void onDialogDismiss(int phoneId) {
            Log.d(TAG, "onDialogDismiss");
            clearSwitchChanged(phoneId);
            resetAlertDialogFragment(null);
            update();
        }

        @Override
        public void onDialogAttach(InstrumentedDialogFragment dialog) {
            Log.d(TAG, "onDialogAttach");
            //UNISOC:Modify for bug1139807
            if (mAlertDialogFragment != null) {
                mAlertDialogFragment.dismissAllowingStateLoss();
                mAlertDialogFragment = null;
            }
            resetAlertDialogFragment(dialog);
        }

        private void updateSwitchState() {
            if (mSwitch != null) {
                boolean isRadioBusy = TeleUtils.isRadioBusy(mContext);
                boolean isAirplaneModeOn = WirelessUtils.isAirplaneModeOn(mContext);
                Log.d(TAG, "updateSwitchState[" + mSlotId + "]: radioBusy: " + isRadioBusy + ",isCallStateIdle:" + isCallStateIdle()
                        + " APM: " + isAirplaneModeOn +",isSimEnabled:"+mTeleMgrEx.isSimEnabled(mSlotId));
                if (mSwitchHasChanged[mSlotId]) {
                    mSwitch.setChecked(mIsChecked[mSlotId]);
                    mSwitchHasChanged[mSlotId] = false;
                } else {
                    mSwitch.setChecked(mTeleMgrEx.isSimEnabled(mSlotId));
                }
                mSwitch.setEnabled(
                        mIsSimExist && !isRadioBusy && !isAirplaneModeOn && isCallStateIdle());
            }
        }

        private void showAlertDialog(boolean onOff) {
            final SimSlotEnableDialogFragment dialogFragment = SimSlotEnableDialogFragment.newInstance(mSlotId, onOff);
            dialogFragment.setController(this);
            dialogFragment.show(mFragmentManager,"SimSlotEnableDialogFragment");
        }
    }

    boolean isCallStateIdle() {
        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager != null) {
            return !telecomManager.isInCall();
        }
        return true;
    }

    void resetAlertDialogFragment(InstrumentedDialogFragment dialogFragment) {
        mAlertDialogFragment = (SimSlotEnableDialogFragment)dialogFragment;
    }

    void clearSwitchChanged(int phoneId) {
        mSwitchHasChanged[phoneId] = false;
    }
    /* @} */
}

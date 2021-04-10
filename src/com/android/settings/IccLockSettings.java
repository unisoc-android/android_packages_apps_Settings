/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import android.app.settings.SettingsEnums;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.IccCardConstants;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import com.android.settings.search.actionbar.SearchMenuController;

/**
 * Implements the preference screen to enable/disable ICC lock and
 * also the dialogs to change the ICC PIN. In the former case, enabling/disabling
 * the ICC lock will prompt the user for the current PIN.
 * In the Change PIN case, it prompts the user for old pin, new pin and new pin
 * again before attempting to change it. Calls the SimCard interface to execute
 * these operations.
 *
 */
public class IccLockSettings extends SettingsPreferenceFragment
        implements EditPinPreference.OnPinEnteredListener {
    private static final String TAG = "IccLockSettings";
    private static final boolean DBG = false;

    private static final int OFF_MODE = 0;
    // State when enabling/disabling ICC lock
    private static final int ICC_LOCK_MODE = 1;
    // State when entering the old pin
    private static final int ICC_OLD_MODE = 2;
    // State when entering the new pin - first time
    private static final int ICC_NEW_MODE = 3;
    // State when entering the new pin - second time
    private static final int ICC_REENTER_MODE = 4;

    // Keys in xml file
    private static final String PIN_DIALOG = "sim_pin";
    private static final String PIN_TOGGLE = "sim_toggle";
    // Keys in icicle
    private static final String DIALOG_STATE = "dialogState";
    private static final String DIALOG_PIN = "dialogPin";
    private static final String DIALOG_ERROR = "dialogError";
    private static final String ENABLE_TO_STATE = "enableState";
    private static final String CURRENT_TAB = "currentTab";

    // Save and restore inputted PIN code when configuration changed
    // (ex. portrait<-->landscape) during change PIN code
    private static final String OLD_PINCODE = "oldPinCode";
    private static final String NEW_PINCODE = "newPinCode";

    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    // Which dialog to show next when popped up
    private int mDialogState = OFF_MODE;

    private String mPin;
    private String mOldPin;
    private String mNewPin;
    private String mError;
    // Are we trying to enable or disable ICC lock?
    private boolean mToState;

    private TabHost mTabHost;
    private TabWidget mTabWidget;
    private ListView mListView;

    private Phone mPhone;

    private EditPinPreference mPinDialog;
    private SwitchPreference mPinToggle;

    private Resources mRes;

    // For async handler to identify request type
    private static final int MSG_ENABLE_ICC_PIN_COMPLETE = 100;
    private static final int MSG_CHANGE_ICC_PIN_COMPLETE = 101;
    private static final int MSG_SIM_STATE_CHANGED = 102;

    // @see android.widget.Toast$TN
    private static final long LONG_DURATION_TIMEOUT = 7000;

    /*UNISOC: Feature porting @{ */
    private static final String PROPERTY_PIN_REMAINTIMES = "vendor.sim.pin.remaintimes";
    private static final int DEFAULT_REMAIN_TIMES = -1;

    private int[] mRemainTimes = null;
    private int[] mSimCardState = null;
    private int mPhoneCount ;
    private TelephonyManager mTelephonyManager;
    private int mCurrentTab;
    /*UNISOC: @} */

    // For replies from IccCard interface
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            /*UNISOC: BUG1130841 phone crash when the message return later @{ */
            if (getContext() == null) {
                return;
            }
            /*UNISOC: @} */
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case MSG_ENABLE_ICC_PIN_COMPLETE:
                    //iccLockChanged(ar.exception == null, msg.arg1, ar.exception);
                    /*UNISOC: Feature porting @{ */
                    iccLockChanged(ar.exception == null, msg.arg1, msg.arg2, ar.exception);
                    /*UNISOC: @} */
                    break;
                case MSG_CHANGE_ICC_PIN_COMPLETE:
                    iccPinChanged(ar.exception == null, msg.arg1);
                    break;
                case MSG_SIM_STATE_CHANGED:
                    /*UNISOC: Feature porting @{ */
                    int phoneId = msg.arg1;
                    int simState = msg.arg2;
                    boolean isCurrentSimChanged = false;
                    if (getCurrentTab() == phoneId && simState != mSimCardState[phoneId]) {
                        mSimCardState[phoneId] = simState;
                        isCurrentSimChanged = true;
                    }
                    if (simState == TelephonyManager.SIM_STATE_ABSENT){
                        mRemainTimes[phoneId] = DEFAULT_REMAIN_TIMES;
                    } else if (simState == TelephonyManager.SIM_STATE_LOADED){
                        if (isCurrentSimChanged && (mPinDialog != null
                                && mPinDialog.getDialog() != null
                                && mPinDialog.getDialog().isShowing())) {
                            mPinDialog.getDialog().dismiss();
                        }
                    }
                    /*UNISOC: @} */
                    updatePreferences();
                    /*UNISOC: Feature porting @{ */
                    updateTabName();
                    getRemainTimes();
                    /*UNISOC: @} */
                    break;
            }

            return;
        }
    };

    private final BroadcastReceiver mSimStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                //mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGED));
                /*UNISOC: Feature porting @{ */
                String simState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        SubscriptionManager.DEFAULT_PHONE_INDEX);
                Log.d(TAG, "simState : " + simState + " phoneId : " + phoneId);
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simState)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGED, phoneId,
                            TelephonyManager.SIM_STATE_ABSENT));
                } else if(IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(simState)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGED, phoneId,
                            TelephonyManager.SIM_STATE_LOADED));
                }
                /*UNISOC: @} */
            }
        }
    };

    // For top-level settings screen to query
    static boolean isIccLockEnabled() {
        return PhoneFactory.getDefaultPhone().getIccCard().getIccLockEnabled();
    }

    static String getSummary(Context context) {
        Resources res = context.getResources();
        String summary = isIccLockEnabled()
                ? res.getString(R.string.sim_lock_on)
                : res.getString(R.string.sim_lock_off);
        return summary;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        /*UNISOC: Feature porting, hide search icon @{ */
        final Bundle args = new Bundle();
        args.putBoolean(SearchMenuController.NEED_SEARCH_ICON_IN_ACTION_BAR, false);
        setArguments(args);
        /*UNISOC: @} */
        super.onCreate(savedInstanceState);

        if (Utils.isMonkeyRunning()) {
            finish();
            return;
        }
        /*UNISOC: Feature porting @{ */
        mTelephonyManager = TelephonyManager.from(getContext());
        mPhoneCount = mTelephonyManager.getPhoneCount();
        mRemainTimes = new int[mPhoneCount];
        mSimCardState = new int[mPhoneCount];
        for (int i = 0; i < mPhoneCount; i++) {
            mRemainTimes[i] = DEFAULT_REMAIN_TIMES;
            mSimCardState[i] = SubscriptionManager.from(getContext()).getSimStateForSlotIndex(i);
        }

        final IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        getContext().registerReceiver(mSimStateReceiver, filter);
        /*UNISOC: @} */
        addPreferencesFromResource(R.xml.sim_lock_settings);

        mPinDialog = (EditPinPreference) findPreference(PIN_DIALOG);
        mPinToggle = (SwitchPreference) findPreference(PIN_TOGGLE);
        if (savedInstanceState != null && savedInstanceState.containsKey(DIALOG_STATE)) {
            mDialogState = savedInstanceState.getInt(DIALOG_STATE);
            mPin = savedInstanceState.getString(DIALOG_PIN);
            mError = savedInstanceState.getString(DIALOG_ERROR);
            mToState = savedInstanceState.getBoolean(ENABLE_TO_STATE);

            // Restore inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    mOldPin = savedInstanceState.getString(OLD_PINCODE);
                    break;

                case ICC_REENTER_MODE:
                    mOldPin = savedInstanceState.getString(OLD_PINCODE);
                    mNewPin = savedInstanceState.getString(NEW_PINCODE);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        }

        mPinDialog.setOnPinEnteredListener(this);

        // Don't need any changes to be remembered
        getPreferenceScreen().setPersistent(false);

        mRes = getResources();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        final TelephonyManager tm =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        final int numSims = tm.getSimCount();
        if (numSims > 1) {
            View view = inflater.inflate(R.layout.icc_lock_tabs, container, false);
            final ViewGroup prefs_container = (ViewGroup) view.findViewById(R.id.prefs_container);
            Utils.prepareCustomPreferencesList(container, view, prefs_container, false);
            View prefs = super.onCreateView(inflater, prefs_container, savedInstanceState);
            prefs_container.addView(prefs);

            mTabHost = (TabHost) view.findViewById(android.R.id.tabhost);
            mTabWidget = (TabWidget) view.findViewById(android.R.id.tabs);
            mListView = (ListView) view.findViewById(android.R.id.list);

            mTabHost.setup();
            //mTabHost.setOnTabChangedListener(mTabListener);
            mTabHost.clearAllTabs();

            SubscriptionManager sm = SubscriptionManager.from(getContext());
            for (int i = 0; i < numSims; ++i) {
                final SubscriptionInfo subInfo = sm.getActiveSubscriptionInfoForSimSlotIndex(i);
                mTabHost.addTab(buildTabSpec(String.valueOf(i),
                        String.valueOf(subInfo == null
                            ? getContext().getString(R.string.sim_editor_title, i + 1)
                            : subInfo.getDisplayName())));
            }
            /*UNISOC: Feature porting  @{ */
            if (savedInstanceState != null && savedInstanceState.containsKey(CURRENT_TAB)) {
                mTabHost.setCurrentTabByTag(savedInstanceState.getString(CURRENT_TAB));
            }
            mCurrentTab = getCurrentTab();
            Log.d(TAG, "mTabHost onCreateView : " + mCurrentTab);
            mTabHost.setOnTabChangedListener(mTabListener);
            final SubscriptionInfo sir = sm.getActiveSubscriptionInfoForSimSlotIndex(
                    mCurrentTab);
            /*UNISOC: @} */

            mPhone = (sir == null) ? null
                    : PhoneFactory.getPhone(SubscriptionManager.getPhoneId(sir.getSubscriptionId()));

//          if (savedInstanceState != null && savedInstanceState.containsKey(CURRENT_TAB)) {
//              mTabHost.setCurrentTabByTag(savedInstanceState.getString(CURRENT_TAB));
//          }
            return view;
        } else {
            mPhone = PhoneFactory.getDefaultPhone();
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updatePreferences();
    }

    private void updatePreferences() {
//        if (mPinDialog != null) {
//            mPinDialog.setEnabled(mPhone != null);
//        }
//        if (mPinToggle != null) {
//            mPinToggle.setEnabled(mPhone != null);
//
//            if (mPhone != null) {
//                mPinToggle.setChecked(mPhone.getIccCard().getIccLockEnabled());
//            }
//        }
                /*UNISOC: Feature porting  @{ */
        if (Utils.isMonkeyRunning() || getContext() == null) {
            return;
        }
        mCurrentTab = getCurrentTab();
        final SubscriptionInfo subInfo = SubscriptionManager.from(getContext())
                .getActiveSubscriptionInfoForSimSlotIndex(mCurrentTab);
        mPhone = (subInfo == null) ? null
                : PhoneFactory.getPhone(
                SubscriptionManager.getPhoneId(subInfo.getSubscriptionId()));
        Log.d(TAG, "currentTab : " + mCurrentTab + " mPhone is " + mPhone);
        boolean pinNotAvailable = (mPhone == null
                || !mPhone.getIccCard().hasIccCard()
                || (SubscriptionManager.getSimStateForSlotIndex(mCurrentTab)!= TelephonyManager.SIM_STATE_LOADED
                && mTelephonyManager.getSimState(mCurrentTab) != TelephonyManager.SIM_STATE_PERM_DISABLED));;

        mPinDialog.setEnabled(!pinNotAvailable);
        mPinToggle.setEnabled(!pinNotAvailable);

        if (pinNotAvailable) {
            mPinToggle.setChecked(false);
        }
        Log.d(TAG, "isDialogOpen : " + mPinDialog.isDialogOpen() + "; pinNotAvailable : "
                + pinNotAvailable + "; sim state : " + mTelephonyManager.getSimState(mCurrentTab));
        if (mPinDialog.isDialogOpen() && pinNotAvailable) {
            mPinDialog.getDialog().dismiss();
        }
        if (mPhone != null) {
            mPinToggle.setChecked(mPhone.getIccCard().getIccLockEnabled());
        }
        /*UNISOC: @} */
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.ICC_LOCK;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
        // ACTION_SIM_STATE_CHANGED is sticky, so we'll receive current state after this call,
        // which will call updatePreferences().
        //final IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        //getContext().registerReceiver(mSimStateReceiver, filter);
        /*UNISOC: Feature porting @{ */
        SubscriptionManager.from(
                getContext()).addOnSubscriptionsChangedListener(mSubscriptionListener);
        getRemainTimes();
        /*UNISOC: @} */
        if (mDialogState != OFF_MODE) {
            showPinDialog();
        } else {
            // Prep for standard click on "Change PIN"
            resetDialogState();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //getContext().unregisterReceiver(mSimStateReceiver);
        /*UNISOC: Feature porting @{ */
        SubscriptionManager.from(getContext()).removeOnSubscriptionsChangedListener(
                mSubscriptionListener);
        /*UNISOC: @} */
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_icc_lock;
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        // Need to store this state for slider open/close
        // There is one case where the dialog is popped up by the preference
        // framework. In that case, let the preference framework store the
        // dialog state. In other cases, where this activity manually launches
        // the dialog, store the state of the dialog.
        if (mPinDialog.isDialogOpen()) {
            out.putInt(DIALOG_STATE, mDialogState);
            out.putString(DIALOG_PIN, mPinDialog.getEditText().getText().toString());
            out.putString(DIALOG_ERROR, mError);
            out.putBoolean(ENABLE_TO_STATE, mToState);

            // Save inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    break;

                case ICC_REENTER_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    out.putString(NEW_PINCODE, mNewPin);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        } else {
            super.onSaveInstanceState(out);
        }

        /*UNISOC: Feature porting @{ */
        if (mPhoneCount > 1 && mTabHost != null && mTabHost.getCurrentTabTag() != null) {
            out.putString(CURRENT_TAB, mTabHost.getCurrentTabTag());
        }
        /*UNISOC: @} */
    }

    private void showPinDialog() {
        if (mDialogState == OFF_MODE) {
            return;
        }
        /*UNISOC: Feature porting @{ */
        if (!hasActiveSub()) {
            Log.d(TAG, "Do not show pin dialog because no active sub.");
            return;
        }
        /*UNISOC: @} */
        setDialogValues();
        /*UNISOC: BUG1164082 @{ */
        if (mPhone == null || mRemainTimes[mPhone.getPhoneId()] <= 0) {
            resetDialogState();
            Log.d(TAG, "Do not show pin dialog because remian time is 0.");
            return;
        }
        /*UNISOC: @} */
        mPinDialog.showPinDialog();

        final EditText editText = mPinDialog.getEditText();
        /*UNISOC: Feature porting @{ */
        if (editText != null) {
            editText.setFocusable(true);
            editText.setFocusableInTouchMode(true);
            editText.requestFocus();
        }
        /*UNISOC: @} */
        if (!TextUtils.isEmpty(mPin) && editText != null) {
            editText.setSelection(mPin.length());
        }
    }

    private void setDialogValues() {
        mPinDialog.setText(mPin);
        String message = "";
        /*UNISOC: Feature porting @{ */
        Log.d(TAG, "mDialogState = " + mDialogState);
        /*UNISOC: @} */
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                message = mRes.getString(R.string.sim_enter_pin);
                mPinDialog.setDialogTitle(mToState
                        ? mRes.getString(R.string.sim_enable_sim_lock)
                        : mRes.getString(R.string.sim_disable_sim_lock));
                break;
            case ICC_OLD_MODE:
                message = mRes.getString(R.string.sim_enter_old);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
            case ICC_NEW_MODE:
                message = mRes.getString(R.string.sim_enter_new);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
            case ICC_REENTER_MODE:
                message = mRes.getString(R.string.sim_reenter_new);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
        }
        /*UNISOC: Feature porting @{ */
        int remainTimes = getRemainTimes();
        if (remainTimes >= 0
                && mDialogState != ICC_NEW_MODE && mDialogState != ICC_REENTER_MODE) {
            message += mRes.getString((com.android.settings.R.string.
                    attempts_remaining_times), remainTimes);
        }
        /*UNISOC: @} */
        if (mError != null) {
            message = mError + "\n" + message;
            mError = null;
        }
        mPinDialog.setDialogMessage(message);
    }

    @Override
    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (!positiveResult) {
            resetDialogState();
            return;
        }

        mPin = preference.getText();
        if (!reasonablePin(mPin)) {
            // inject error message and display dialog again
            mError = mRes.getString(R.string.sim_bad_pin);
            /*UNISOC: Feature porting @{ */
            mPin = null;
            /*UNISOC: @} */
            showPinDialog();
            return;
        }
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                tryChangeIccLockState();
                break;
            case ICC_OLD_MODE:
                mOldPin = mPin;
                mDialogState = ICC_NEW_MODE;
                mError = null;
                mPin = null;
                showPinDialog();
                break;
            case ICC_NEW_MODE:
                mNewPin = mPin;
                mDialogState = ICC_REENTER_MODE;
                mPin = null;
                showPinDialog();
                break;
            case ICC_REENTER_MODE:
                if (!mPin.equals(mNewPin)) {
                    mError = mRes.getString(R.string.sim_pins_dont_match);
                    mDialogState = ICC_NEW_MODE;
                    mPin = null;
                    showPinDialog();
                } else {
                    mError = null;
                    tryChangePin();
                }
                break;
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mPinToggle) {
            // Get the new, preferred state
            mToState = mPinToggle.isChecked();
            // Flip it back and pop up pin dialog
            mPinToggle.setChecked(!mToState);
            /*UNISOC: Feature porting @{ */
            if (mDialogState != OFF_MODE) {
                Log.d(TAG, "Wait for change sim pin done.");
                return true;
            }
            mPin = null;
            /*UNISOC: @} */
            mDialogState = ICC_LOCK_MODE;
            showPinDialog();
        } else if (preference == mPinDialog) {
            /*UNISOC: Feature porting @{ */
            if (mDialogState != OFF_MODE) {
                Log.d(TAG, "Wait for enable/disable pin lock done.");
                return false;
            }
            /*UNISOC: @} */
            mDialogState = ICC_OLD_MODE;
            /*UNISOC: Feature porting @{ */
            mPin = null;
            setDialogValues();
            /*UNISOC: @} */
            return false;
        }
        return true;
    }

    private void tryChangeIccLockState() {
        // Try to change icc lock. If it succeeds, toggle the lock state and
        // reset dialog state. Else inject error message and show dialog again.
        // Message callback = Message.obtain(mHandler, MSG_ENABLE_ICC_PIN_COMPLETE);
        // mPhone.getIccCard().setIccLockEnabled(mToState, mPin, callback);
        /*UNISOC: Feature porting @{ */
        Message callback = Message.obtain(mHandler, MSG_ENABLE_ICC_PIN_COMPLETE, -1, mCurrentTab);
        if (mPhone == null) {
            resetDialogState();
            return;
        }
        mPhone.getIccCard().setIccLockEnabled(mToState, mPin, callback);
        /*UNISOC: @} */
        // Disable the setting till the response is received.
        mPinToggle.setEnabled(false);
    }

    private void iccLockChanged(boolean success, int attemptsRemaining, int currentTab,
                                Throwable exception) {
        if (success) {
            //BUG: 1245893
            Log.d(TAG, " mToState:" + mToState);
            if (currentTab == mCurrentTab) {
                mPinToggle.setChecked(mToState);
            }
            /*UNISOC: Feature porting @{ */
            if (attemptsRemaining < 0 && getContext() != null) {
                if (mToState) {
                    Toast.makeText(getContext(), mRes.getString(R.string.icc_pin_enabled,
                            currentTab + 1), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), mRes.getString(R.string.icc_pin_disabled,
                            currentTab + 1), Toast.LENGTH_LONG).show();
                }
            }
            /*UNISOC: @} */
        } else {
            if (exception instanceof CommandException) {
                CommandException.Error err = ((CommandException)(exception)).getCommandError();
                if (err == CommandException.Error.PASSWORD_INCORRECT) {
                    createCustomTextToast(getPinPasswordErrorMessage(attemptsRemaining));
                } else {
                    if (mToState) {
                        Toast.makeText(getContext(), mRes.getString
                               (R.string.sim_pin_enable_failed), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), mRes.getString
                               (R.string.sim_pin_disable_failed), Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
        /*UNISOC: Feature porting @{ */
        if (PhoneFactory.getPhone(currentTab) == null) {
            resetDialogState();
            return;
        }
        mRemainTimes[currentTab] = attemptsRemaining;
        /*UNISOC: @} */
        //BUG: 1245893
        if (currentTab == mCurrentTab) {
            mPinToggle.setEnabled(true);
            resetDialogState();
        }
    }

    private void createCustomTextToast(CharSequence errorMessage) {
        // Cannot overlay Toast on PUK unlock screen.
        // The window type of Toast is set by NotificationManagerService.
        // It can't be overwritten by LayoutParams.type.
        // Ovarlay a custom window with LayoutParams (TYPE_STATUS_BAR_PANEL) on PUK unlock screen.
        View v = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(com.android.internal.R.layout.transient_notification, null);
        TextView tv = (TextView) v.findViewById(com.android.internal.R.id.message);
        tv.setText(errorMessage);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        final Configuration config = v.getContext().getResources().getConfiguration();
        final int gravity = Gravity.getAbsoluteGravity(
                getContext().getResources().getInteger(
                        com.android.internal.R.integer.config_toastDefaultGravity),
                config.getLayoutDirection());
        params.gravity = gravity;
        if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.FILL_HORIZONTAL) {
            params.horizontalWeight = 1.0f;
        }
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.FILL_VERTICAL) {
            params.verticalWeight = 1.0f;
        }
        params.y = getContext().getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.toast_y_offset);

        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.format = PixelFormat.TRANSLUCENT;
        params.windowAnimations = com.android.internal.R.style.Animation_Toast;
        params.type = WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
        params.setTitle(errorMessage);
        params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        wm.addView(v, params);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                wm.removeViewImmediate(v);
            }
        }, LONG_DURATION_TIMEOUT);
    }

    private void iccPinChanged(boolean success, int attemptsRemaining) {
        if (!success) {
            createCustomTextToast(getPinPasswordErrorMessage(attemptsRemaining));
        } else {
            Toast.makeText(getContext(), mRes.getString(R.string.sim_change_succeeded),
                    Toast.LENGTH_SHORT)
                    .show();

        }
        /*UNISOC: Feature porting @{ */
        if (mPhone == null) {
            resetDialogState();
            return;
        }
        mRemainTimes[mPhone.getPhoneId()] = attemptsRemaining;
        /*UNISOC: @} */
        resetDialogState();
    }

    private void tryChangePin() {
        Message callback = Message.obtain(mHandler, MSG_CHANGE_ICC_PIN_COMPLETE);
        /*UNISOC: Feature porting @{ */
        if (mPhone == null) {
            resetDialogState();
            return;
        }
        /*UNISOC: @} */
        mPhone.getIccCard().changeIccLockPassword(mOldPin,
                mNewPin, callback);
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = mRes.getString(R.string.wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = mRes
                    .getQuantityString(R.plurals.wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = mRes.getString(R.string.pin_failed);
        }
        Log.d(TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    private boolean reasonablePin(String pin) {
        if (pin == null || pin.length() < MIN_PIN_LENGTH || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }

    private void resetDialogState() {
        mError = null;
        mDialogState = ICC_OLD_MODE; // Default for when Change PIN is clicked
        mPin = "";
        setDialogValues();
        mDialogState = OFF_MODE;
    }

    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        @Override
        public void onTabChanged(String tabId) {
            /*UNISOC: Feature porting @{ */
            Log.d(TAG, "onTabChanged tabId = " + tabId);
            mCurrentTab = Integer.parseInt(tabId);
            /*UNISOC: @} */
            final int slotId = Integer.parseInt(tabId);
            final SubscriptionInfo sir = SubscriptionManager.from(getActivity().getBaseContext())
                    .getActiveSubscriptionInfoForSimSlotIndex(slotId);

            mPhone = (sir == null) ? null
                : PhoneFactory.getPhone(SubscriptionManager.getPhoneId(sir.getSubscriptionId()));
            /*UNISOC: Feature porting @{ */
            if ((mPinDialog != null) && (mPinDialog.getDialog() != null)
                    && mPinDialog.getDialog().isShowing()) {
                Log.d(TAG, "onTabChanged dismiss old dialog");
                mPinDialog.getDialog().dismiss();
            }
            resetDialogState();
            /*UNISOC: @} */
            // The User has changed tab; update the body.
            updatePreferences();
        }
    };

    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        @Override
        public View createTabContent(String tag) {
            return new View(mTabHost.getContext());
        }
    };

    private TabSpec buildTabSpec(String tag, String title) {
        return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                mEmptyTabContent);
    }

    /*UNISOC: Feature porting @{ */
    private int getCurrentTab() {
        int currentTab = 0;
        if (mPhoneCount > 1 && mTabHost != null && mTabHost.getCurrentTabTag() != null) {
            try {
                currentTab = Integer.parseInt(mTabHost.getCurrentTabTag());
            } catch (NumberFormatException ex) {
                ex.printStackTrace();
            }
        }
        return currentTab;
    }

    private boolean hasActiveSub() {
        if (getContext() == null) {
            return false;
        }
        boolean result = SubscriptionManager.from(getContext())
                .getActiveSubscriptionInfoForSimSlotIndex(mCurrentTab) != null;
        Log.d(TAG, "hasActiveSub : " + result + " currentTab : " + mCurrentTab);
        return result;
    }

    public void onDestroy() {
        super.onDestroy();
        if (!Utils.isMonkeyRunning() && mSimStateReceiver != null) {
            getContext().unregisterReceiver(mSimStateReceiver);
        }
    }

    private void updateTabName() {
        if (getContext() == null) {
            return;
        }
        if (mPhoneCount <= 1) {
            return;
        }
        for (int i = 0; i < mPhoneCount; ++i) {
            final SubscriptionInfo subInfo = SubscriptionManager.from(getContext())
                    .getActiveSubscriptionInfoForSimSlotIndex(i);
            TextView tabTitle = (TextView) mTabHost.getTabWidget().getChildTabViewAt(i)
                    .findViewById(android.R.id.title);
            if (tabTitle != null) {
                tabTitle.setText(String.valueOf(subInfo == null
                        ? getContext().getString(R.string.sim_editor_title, i + 1)
                        : subInfo.getDisplayName()));
                tabTitle.setSingleLine();
                tabTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            }
        }
    }

    private OnSubscriptionsChangedListener mSubscriptionListener =
            new OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    Log.d(TAG, "onSubscriptionsChanged updateTabName");
                    updateTabName();
                }
            };

    private int getRemainTimes() {
        Log.d(TAG, "getRemainTimes start");
        if (getContext() == null || mPhone == null) {
            return DEFAULT_REMAIN_TIMES;
        }
        if (mRemainTimes[mPhone.getPhoneId()] > 0) {
            return mRemainTimes[mPhone.getPhoneId()];
        }
        int remianTimes = DEFAULT_REMAIN_TIMES;
        // Sending empty PIN here to query the number of remaining PIN attempts
        if (SubscriptionManager.isValidSubscriptionId(mPhone.getSubId())) {
            String propertyValue = TelephonyManager.from(getContext())
                    .getTelephonyProperty(mPhone.getPhoneId(), PROPERTY_PIN_REMAINTIMES, "");
            if (!TextUtils.isEmpty(propertyValue)) {
                try {
                    remianTimes = Integer.valueOf(propertyValue);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.d(TAG, "getRemainTimes end remianTimes: " + remianTimes);
        mRemainTimes[mPhone.getPhoneId()] = remianTimes;
        return remianTimes;
    }
    /*UNISOC: @} */
}

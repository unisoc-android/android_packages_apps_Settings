package com.android.settings.smartcallforward;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.widget.SwitchBar;

import static com.android.internal.telephony.PhoneConstants.SUB1;
import static com.android.internal.telephony.PhoneConstants.SUB2;

public class SmartDualSIMActivity extends Activity implements SwitchBar.OnSwitchChangeListener {

    private static final String TAG = "SmartDualSIMActivity";
    private static final boolean DBG = true;

    public static final String SAMRTCALLFORWARDINGDIALOG_ON = "smartcallforwardingdialog_on";
    public static final String SAMRTCALLFORWARD_SIM_NUM = "smartcallforward_sim_number";
    private Context mContext;


    private SwitchBar mSwitchBar;
    private EditText[] mSimEdit = new EditText[2];
    private String[] mSimNumbers = new String[2];
    private String[] mCFNRNumbers = new String[2];
    private Transaction mTransaction;
    private SharedPreferences mPrefs;
    public static String[] mCountryCode = null;
    private String[] mNumbersInSimCard = new String[2];

    private boolean mShowWarningDialog;
    private boolean mIsForeground = false;
    private boolean mFirstResume;
    private boolean mCallforwardStatus;
    private boolean mCFUEnabled;
    private boolean mCallWaitingOnSIM1;
    private boolean mCallWaitingOnSIM2;
    private boolean mSIM1CFNREnabled;
    private boolean mSIM2CFNREnabled;
    private boolean mSIM1CFNRCNeedUpdate;
    private boolean mSIM2CFNRCNeedUpdate;

    private static final int OPERATION_DELAY_TIME = 500;
    private static final int MESSAGE_GET_CFNRC = 10;
    private static final int MESSAGE_GET_CFU = 11;
    private static final int MESSAGE_GET_CALL_WAITING = 12;
    private static final int MESSAGE_UPDATE_UI = 13;
    private static final int DIALOG_BUSY_SETTING = 100;
    private static final int DIALOG_NO_SIM_NUMBER = 101;
    private static final int DIALOG_READING_SETTINGS = 102;
    private static final int DIALOG_NOTIFY_CFU_IS_ON = 103;
    private static final int DIALOG_SHOW_PROMPT = 104;
    private static final int DIALOG_ERROR_HAPPEN = 105;
    private static final int ERROR_EXCEPTION = 200;
    private static final int ERROR_RESPONSE = 201;
    private static final int ERROR_RADIO_OFF = 202;
    private static final int FDN_CHECK_FAILURE = 203;
    private static final int SCF_ZERO = 0;
    private static final int SCF_DEF = 1;
    private static final int DIALOG_LEFT = 18;
    private static final int DIALOG_TOP = 20;
    private static final int DIALOG_RIGHT = 0;
    private static final int DIALOG_BOTTOM = 0;
    private static final int FLAG_QUERY_CFNRC = 1;
    private static final int EVENT_QUERY = 100;

    private Transaction.Callback mCallback = new Transaction.Callback() {
        @Override
        public void onComplete(Transaction t) {
            if (mSwitchBar.isChecked()) {
                if (mPrefs != null) {
                    SharedPreferences.Editor editor = mPrefs.edit();
                    editor.putString(PhoneFactory.getPhone(SUB1).getSubscriberId(),
                            mSimNumbers[SUB1]);
                    editor.putString(PhoneFactory.getPhone(SUB2).getSubscriberId(),
                            mSimNumbers[SUB2]);
                    editor.apply();
                }
                mSimEdit[0].setEnabled(false);
                mSimEdit[1].setEnabled(false);
            } else {
                if (mPrefs != null) {
                    SharedPreferences.Editor editor = mPrefs.edit();
                    editor.putString(PhoneFactory.getPhone(SUB1).getSubscriberId(), null);
                    editor.putString(PhoneFactory.getPhone(SUB2).getSubscriberId(), null);
                    editor.apply();
                }
                if (TextUtils.isEmpty(mNumbersInSimCard[SUB1])) {
                    mSimEdit[SUB1].setEnabled(true);
                }
                if (TextUtils.isEmpty(mNumbersInSimCard[SUB2])) {
                    mSimEdit[SUB2].setEnabled(true);
                }
            }
            removeDialog(DIALOG_BUSY_SETTING);
            Log.d(TAG, "onComplete(" + t + ")");
        }

        @Override
        public void onError(Transaction t) {
            removeDialog(DIALOG_BUSY_SETTING);
            if (t == mTransaction) {
                setChecked(!mSwitchBar.isChecked());
                if (t.getLastException().exception != null) {
                    onException(t.getLastException().exception);
                } else {
                    showDialogIfForeground(ERROR_EXCEPTION);
                }
            }
            Log.d(TAG, "onError(" + t + ")", t.getLastException());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.smart_dual_sim_setting);
        mContext = this;
        mFirstResume = true;
        final Resources resources = getResources();
        if (mCountryCode == null) {
            mCountryCode = resources.getStringArray(R.array.country_codes);
        }
        mShowWarningDialog = (Settings.Global.getInt(
                this.getContentResolver(), SAMRTCALLFORWARDINGDIALOG_ON, SCF_DEF) == SCF_DEF);
        mSwitchBar = (SwitchBar) findViewById(R.id.switch_bar);
        mSwitchBar.show();
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        TextView sim1NumberText = (TextView) findViewById(R.id.sim1_number_text);
        sim1NumberText.setText(getResources().getString(R.string.number_sim, 1));
        TextView sim2NumberText = (TextView) findViewById(R.id.sim2_number_text);
        sim2NumberText.setText(getResources().getString(R.string.number_sim, 2));
        mSimEdit[0] = (EditText) findViewById(R.id.sim1_number);
        mSimEdit[1] = (EditText) findViewById(R.id.sim2_number);
        mPrefs = mContext.getSharedPreferences(SAMRTCALLFORWARD_SIM_NUM,
                mContext.MODE_PRIVATE);

        initSimNumber(SUB1);
        initSimNumber(SUB2);
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    private void initSimNumber(int phoneId) {
        if (phoneId >= 0 && phoneId < 2) {
            String number = normalizeCFNRNumber(PhoneFactory.getPhone(phoneId).getLine1Number());
            if (!TextUtils.isEmpty(number)) {
                mSimNumbers[phoneId] = number;
                mNumbersInSimCard[phoneId] = number;
                mSimEdit[phoneId].setText(number);
                mSimEdit[phoneId].setEnabled(false);
            } else {
                if (mPrefs != null) {
                    mSimNumbers[phoneId] = mPrefs.getString(
                            PhoneFactory.getPhone(phoneId).getSubscriberId(), null);
                }
                if (!TextUtils.isEmpty(mSimNumbers[phoneId])) {
                    mSimEdit[phoneId].setText(mSimNumbers[phoneId]);
                    mSimEdit[phoneId].setEnabled(false);
                }
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_READING_SETTINGS || id == DIALOG_BUSY_SETTING) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle(getText(R.string.smart_dual_sim_title));
            dialog.setIndeterminate(true);
            switch(id) {
                case DIALOG_READING_SETTINGS:
                    dialog.setCancelable(true);
                    dialog.setOnCancelListener(new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            finish();
                        }
                    });
                    dialog.setMessage(getText(R.string.reading_settings));
                    return dialog;
                case DIALOG_BUSY_SETTING:
                    dialog.setCancelable(false);
                    dialog.setMessage(getText(R.string.updating_settings));
                    return dialog;
            }
        }
        if (id == DIALOG_SHOW_PROMPT) {
            LayoutInflater inflater = this.getLayoutInflater();
            View view = inflater.inflate(R.layout.dialog_smart_callforwarding, null);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.show_alert_dialog);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mShowWarningDialog = !isChecked;
                }
            });
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.smart_dual_sim_title)
                    .setMessage(this.getResources()
                            .getString(R.string.smart_dual_sim_warning_message))
                    .setView(view, DIALOG_LEFT, DIALOG_TOP, DIALOG_RIGHT, DIALOG_BOTTOM)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Settings.Global.putInt(mContext.getContentResolver(),
                                    SAMRTCALLFORWARDINGDIALOG_ON,
                                    mShowWarningDialog ? SCF_DEF : SCF_ZERO);
                            init();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            finish();
                        }
                    })
                    .setOnCancelListener(new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            finish();
                        }

                    });
            AlertDialog dialog = builder.create();
            return dialog;
        } else if (id == ERROR_RESPONSE || id == ERROR_RADIO_OFF || id == ERROR_EXCEPTION
                || id == FDN_CHECK_FAILURE || id == DIALOG_NOTIFY_CFU_IS_ON) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            int msgId;
            switch (id) {
                case ERROR_RESPONSE:
                    msgId = R.string.response_error;
                    break;
                case ERROR_RADIO_OFF:
                    msgId = R.string.radio_off_error;
                    break;
                case FDN_CHECK_FAILURE:
                    msgId = R.string.fdn_check_failure;
                    break;
                case DIALOG_NOTIFY_CFU_IS_ON:
                    msgId = R.string.cfu_is_on;
                    break;
                case ERROR_EXCEPTION:
                default:
                    msgId = R.string.exception_error;
                    break;
            }

            builder.setTitle(R.string.smart_dual_sim_title);
            builder.setMessage(msgId);
            builder.setCancelable(true);
            OnClickListener clickListener = new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            };
            builder.setPositiveButton(android.R.string.ok, clickListener);
            AlertDialog dialog = builder.create();
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            return dialog;
        } else if (id == DIALOG_NO_SIM_NUMBER) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.smart_dual_sim_title);
            StringBuilder sb = new StringBuilder();
            sb.append(getString(R.string.smart_dual_sim_number_null));
            sb.append(getString(R.string.smart_dual_sim_hint));
            builder.setMessage(sb.toString());
            builder.setCancelable(true);
            OnClickListener clickListener = new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                    setChecked(false);
                }
            };
            builder.setPositiveButton(android.R.string.ok, clickListener);
            builder.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    setChecked(false);
                }

            });
            AlertDialog dialog = builder.create();
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            return dialog;
        } else {
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSwitchBar.addOnSwitchChangeListener(this);
        mIsForeground = true;
        if (mFirstResume) {
            if (mShowWarningDialog) {
                showDialogIfForeground(DIALOG_SHOW_PROMPT);
            } else {
                init();
            }
        }
        mFirstResume = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSwitchBar.removeOnSwitchChangeListener(this);
        mIsForeground = false;
    }

    private void showDialogIfForeground(int id) {
        if (mIsForeground) {
            showDialog(id);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSwitchBar.hide();
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        doSmartForward(isChecked);
    }

    private void doSmartForward(boolean enable) {
        boolean numberIsEmpty = false;
        for (int i = 0; i < 2; i++) {
            if (TextUtils.isEmpty(mSimNumbers[i])) {
                mSimNumbers[i] = mSimEdit[i].getText().toString();
                if (TextUtils.isEmpty(mSimNumbers[i])) {
                    numberIsEmpty = true;
                    break;
                }
            }
        }
        if (enable) {
            if (numberIsEmpty) {
                Log.e(TAG, ">>>>> no sim number!");
                showDialogIfForeground(DIALOG_NO_SIM_NUMBER);
            } else {
                mTransaction = Transaction.newTransaction(false);
                if (!mCallWaitingOnSIM1) {
                    mTransaction.setCallWaiting(SUB1, true);
                }
                if (!mCallWaitingOnSIM2) {
                    mTransaction.setCallWaiting(SUB2, true);
                }
                mTransaction.setCallForward(SUB1, true, mSimNumbers[1])
                        .setCallForward(SUB2, true, mSimNumbers[0]);
                mTransaction.dump();
                mTransaction.setCallback(mCallback);
                mTransaction.commit();
                showDialogIfForeground(DIALOG_BUSY_SETTING);
            }
        } else {
            mTransaction = Transaction.newTransaction(true);
            mTransaction.setCallForward(SUB1, false, mSimNumbers[1])
                    .setCallForward(SUB2, false, mSimNumbers[0]);
            mTransaction.dump();
            mTransaction.setCallback(mCallback);
            mTransaction.commit();
            showDialogIfForeground(DIALOG_BUSY_SETTING);
        }
    }

    private void init() {
        mSwitchBar.setEnabled(false);
        showDialogIfForeground(DIALOG_READING_SETTINGS);
        queryAllState(SUB1);
    }

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (DBG) {
                Log.d(TAG, "mHandler event:" + msg.what + ", arg1:" + msg.arg1 +
                        ", arg2:" + msg.arg2);
            }

            AsyncResult result = (AsyncResult) msg.obj;
            switch (msg.what) {
                case MESSAGE_GET_CFU:
                    handleGetCFUResponse(result, msg.arg1);
                    break;
                case MESSAGE_GET_CALL_WAITING:
                    handleGetCallWaitingResponse(result, msg.arg1);
                    break;
                case MESSAGE_UPDATE_UI:
                    updateScreen();
                    break;
                case MESSAGE_GET_CFNRC:
                    handleGetCFNRCResponse(result, msg.arg1, msg.arg2);
                    break;
                default:
                    break;
            }
        }
    };

    private void handleGetCFUResponse(AsyncResult ar, int phoneId) {
        if (ar.exception != null) {
            if (DBG) {
                Log.d(TAG, "handleGetCFUResponse: ar.exception = " + ar.exception);
            }
            if (ar.exception instanceof CommandException) {
                onException((CommandException) ar.exception);
            } else {
                onError(ERROR_EXCEPTION);
            }
        } else if (ar.userObj instanceof Throwable) {
            onError(ERROR_RESPONSE);
        } else {
            final CallForwardInfo cfInfoArray[] = (CallForwardInfo[]) ar.result;
            if (cfInfoArray == null) {
                if (DBG) {
                    Log.d(TAG, "handleGetCFUResponse: cfInfoArray.length == 0");
                }
                onError(ERROR_RESPONSE);
            } else {
                int length = cfInfoArray.length;
                for (int i = 0; i < length; i++) {
                    if (DBG) {
                        CallForwardInfo c = cfInfoArray[i];
                        Log.d(TAG, "handleGetCFUResponse, cfInfoArray[" + i + "] reason:"
                                + c.reason + ", status:" + c.status + ", serviceClass:"
                                + c.serviceClass + ", number:" + c.number);
                    }
                    if (true) {
                        if ((phoneId == SUB2 && !mCFUEnabled) || (phoneId == SUB1)) {
                            mCFUEnabled = cfInfoArray[i].status ==
                                    CommandsInterface.CF_ACTION_ENABLE;
                        }
                        if (mCFUEnabled) {
                            processStopDialog();
                            showDialogIfForeground(DIALOG_NOTIFY_CFU_IS_ON);
                            return;
                        }
                    }
                }
                PhoneFactory.getPhone(phoneId).getCallWaiting(
                        mHandler.obtainMessage(MESSAGE_GET_CALL_WAITING,
                                phoneId, MESSAGE_GET_CALL_WAITING));
            }
        }
    }

    private void handleGetCallWaitingResponse(AsyncResult ar, int phoneId) {
        if (ar.exception != null) {
            if (ar.exception instanceof CommandException) {
                onException((CommandException) ar.exception);
            } else {
                onError(ERROR_EXCEPTION);
            }
        } else if (ar.userObj instanceof Throwable) {
            onError(ERROR_RESPONSE);
        } else {
            if (DBG) {
                Log.d(TAG, "handleGetCallWaitingResponse: CW state successfully queried." +
                        "phoneId = " + phoneId);
            }
            int[] cwArray = (int[]) ar.result;
            if (phoneId == SUB1) {
                mCallWaitingOnSIM1 = ((cwArray[0] == 1) && ((cwArray[1] & 0x01) == 0x01));
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        queryAllState(SUB2);
                    }
                }, OPERATION_DELAY_TIME);

            } else if (phoneId == SUB2) {
                mCallWaitingOnSIM2 = ((cwArray[0] == 1) && ((cwArray[1] & 0x01) == 0x01));
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        PhoneFactory.getPhone(SUB1).getCallForwardingOption(
                                CommandsInterface.CF_REASON_NOT_REACHABLE,
                                mHandler.obtainMessage(MESSAGE_GET_CFNRC, SUB1));
                    }
                }, OPERATION_DELAY_TIME);
            }
        }
    }

    private void handleGetCFNRCResponse(AsyncResult ar, int phoneId, int flag) {
        if (DBG) {
            Log.d(TAG, "handleGetCFResponse: done arg = " + phoneId);
        }
        if (ar.exception != null) {
            if (DBG) {
                Log.d(TAG, "handleGetCFResponse: ar.exception = " + ar.exception);
            }
            if (ar.exception instanceof CommandException) {
                onException((CommandException) ar.exception);
            } else {
                onError(ERROR_EXCEPTION);
            }
        } else if (ar.userObj instanceof Throwable) {
            onError(ERROR_RESPONSE);
        } else {
            final CallForwardInfo cfInfoArray[] = (CallForwardInfo[]) ar.result;
            if (cfInfoArray == null) {
                if (DBG) {
                    Log.d(TAG, "handleGetCFResponse: cfInfoArray.length == 0");
                }
                onError(ERROR_RESPONSE);
            } else {
                int length = cfInfoArray.length;
                for (int i = 0; i < length; i++) {
                    if (DBG) {
                        CallForwardInfo c = cfInfoArray[i];
                        if (DBG) {
                            Log.d(TAG, "handleGetCFResponse, cfInfoArray[" + i + "] reason:"
                                    + c.reason + ", status:" + c.status + ", serviceClass:"
                                    + c.serviceClass + ", number:" + c.number);
                        }
                    }
                    if ((CommandsInterface.SERVICE_CLASS_VOICE & cfInfoArray[i].serviceClass)
                            != 0) {
                        processCFResponse(cfInfoArray[i], phoneId, flag);
                    }
                }
            }
        }
    }

    private void processCFResponse(CallForwardInfo info, int phoneId, int flag) {
        if (DBG) {
            Log.d(TAG, "processCFResponse: info.reason = " + info.reason
                    + ", info.status = " + info.status
                    + ", phoneId = " + phoneId
                    + ", flag = " + flag);
        }
        mCFNRNumbers[phoneId] = normalizeCFNRNumber(info.number);

        if (phoneId == SUB1) {
            mSIM1CFNREnabled = info.status == CommandsInterface.CF_ACTION_ENABLE;
            mSIM1CFNRCNeedUpdate = !(mSIM1CFNREnabled && (TextUtils.equals(mCFNRNumbers[SUB1],
                    mSimNumbers[SUB2])));
        } else if (phoneId == SUB2) {
            mSIM2CFNREnabled = info.status == CommandsInterface.CF_ACTION_ENABLE;
            mSIM2CFNRCNeedUpdate = !(mSIM2CFNREnabled && (TextUtils.equals(mCFNRNumbers[SUB2],
                    mSimNumbers[SUB1])));
        } else {
            if (DBG) Log.d(TAG, "processCFResponse, wrong sim sub");
        }

        if (phoneId == SUB1) {
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    PhoneFactory.getPhone(SUB2).getCallForwardingOption(
                            CommandsInterface.CF_REASON_NOT_REACHABLE,
                            mHandler.obtainMessage(MESSAGE_GET_CFNRC, SUB2,
                                    FLAG_QUERY_CFNRC));
                }
            }, OPERATION_DELAY_TIME);
        } else {
            processStopDialog();

            if (DBG) {
                Log.d(TAG, "processCFResponse, mCFUEnabled:" + mCFUEnabled
                        + ", mSIM1CFNREnabled:" + mSIM1CFNREnabled
                        + ", mCallWaitingOnSIM1:" + mCallWaitingOnSIM1
                        + ", mSIM2CFNREnabled:" + mSIM2CFNREnabled
                        + ", mCallWaitingOnSIM2:" + mCallWaitingOnSIM2
                        + ", SUB1 CFNRC Number:" + mCFNRNumbers[0]
                        + ", SUB2 CFNRC Number:" + mCFNRNumbers[1]);
            }
        }
    }

    public void onException(CommandException exception) {
        if (exception.getCommandError() == CommandException.Error.FDN_CHECK_FAILURE) {
            onError(FDN_CHECK_FAILURE);
        } else if (exception.getCommandError() == CommandException.Error.RADIO_NOT_AVAILABLE) {
            onError(ERROR_RADIO_OFF);
        } else {
            onError(ERROR_EXCEPTION);
        }
    }

    private void onError(int error) {
        if (DBG) {
          Log.d(TAG, "onError, error=" + error);
        }
        showDialogIfForeground(error);

        processStopDialog();
    }

    private void processStopDialog() {
        onFinished();
        mHandler.sendEmptyMessage(MESSAGE_UPDATE_UI);
    }

    private void onFinished() {
        removeDialog(DIALOG_READING_SETTINGS);
        removeDialog(DIALOG_BUSY_SETTING);
    }

    private void updateScreen() {
        if (mCFUEnabled) {
            mSwitchBar.setEnabled(false);
            mSimEdit[0].setEnabled(false);
            mSimEdit[1].setEnabled(false);
        } else {
            mSwitchBar.setEnabled(true);
        }
        refreshCallforwardStatus();
        setChecked(mCallforwardStatus);
    }

    private void refreshCallforwardStatus() {
        mSIM1CFNRCNeedUpdate = !(mSIM1CFNREnabled && (TextUtils.equals(mCFNRNumbers[SUB1],
                mSimNumbers[SUB2])));
        mSIM2CFNRCNeedUpdate = !(mSIM2CFNREnabled && (TextUtils.equals(mCFNRNumbers[SUB2],
                mSimNumbers[SUB1])));

        mCallforwardStatus = (!mCFUEnabled) && mCallWaitingOnSIM1 && mCallWaitingOnSIM2
                && !mSIM1CFNRCNeedUpdate && !mSIM2CFNRCNeedUpdate
                && !TextUtils.isEmpty(mSimNumbers[SUB1]) && !TextUtils.isEmpty(mSimNumbers[SUB2]);

        if (DBG) {
            Log.d(TAG, "refreshCallforwardStatus, mSIM1CFNREnabled:" + mSIM1CFNREnabled
                    + ", mCallWaitingOnSIM1:" + mCallWaitingOnSIM1 + ", mSIM2CFNREnabled:"
                    + mSIM2CFNREnabled + ", mCallWaitingOnSIM2:" + mCallWaitingOnSIM2
                    + ", mSIM1CFNRCNeedUpdate:" + mSIM1CFNRCNeedUpdate + ", mSIM2CFNRCNeedUpdate:"
                    + mSIM2CFNRCNeedUpdate + ", smartCFEnabled:"
                    + mCallforwardStatus + ", mSimNumbers[SUB1]:"
                    + mSimNumbers[SUB1] + ", mSimNumbers[SUB2]:"
                    + mSimNumbers[SUB2] + ", mCallforwardStatus:"
                    + mCallforwardStatus);
        }
    }

    private void setChecked(boolean isChecked) {
        if (isResumed() && isChecked != mSwitchBar.isChecked()) {
            mSwitchBar.removeOnSwitchChangeListener(this);
            mSwitchBar.setChecked(isChecked);
            mSwitchBar.addOnSwitchChangeListener(this);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public String normalizeCFNRNumber(String number) {
        if (mCountryCode == null || mCountryCode.length <= 0
                || TextUtils.isEmpty(number)) {
            return number;
        }

        String normalizedNumber = number;
        int count = mCountryCode.length;
        for (int i = 0; i < count; i++) {
            if (mCountryCode[i] != null
                    && number.length() > mCountryCode[i].length()
                    && number.startsWith(mCountryCode[i])) {
                normalizedNumber = number.substring(mCountryCode[i].length());
                break;
            }
        }
        return normalizedNumber;
    }

    private final Handler mQueryHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_QUERY:
                    int phoneId = msg.arg1;
                    Log.d(TAG, "SUB" + phoneId + " after query all callforward, query cfu.");
                    PhoneFactory.getPhone(phoneId).getCallForwardingOption(
                            CommandsInterface.CF_REASON_UNCONDITIONAL,
                            mHandler.obtainMessage(MESSAGE_GET_CFU, phoneId, MESSAGE_GET_CFU));
                    break;
                default:
                    break;
            }
        }
    };

    private void queryAllState(int phoneId) {
        PhoneFactory.getPhone(phoneId).getCallForwardingOption(CommandsInterface.CF_REASON_ALL,
                mQueryHandler.obtainMessage(EVENT_QUERY, phoneId, EVENT_QUERY));
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                    finish();
                }
            }
        }
    };
}

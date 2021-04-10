
package com.android.settings.network.telephony;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.sprd.telephony.RadioInteractor;

/**
 * A dialog fragment that asks the user if they are sure they want to turn on ps data
 * off to prevent transport via PDN connections in 3GPP access networks of all data packets
 * except IP packets required by 3GPP PS Data Off Exempt Services
 */
public class PsDataOffDialogFragment extends InstrumentedDialogFragment implements OnClickListener {

    public interface PsDataOffDialogListener {
        void onDialogDismiss(InstrumentedDialogFragment dialog);
    }

    private static final String LOG_TAG = "PsDataOffDialogFragment";
    private static final String DEBUG_TEST = "persist.radio.psdataoff.debug";

    public static final String SUB_ID_KEY = "sub_id_key";
    public static final String PS_DATA_OFF_ENABLED = "persist.radio.ps.data.off";
    public static final String DEVICE_MANAGEMENT_OVER_PS_STRING = "Device management over ps service";
    public static final String MANAGEMENT_OF_USIM_FILES_OVER_PS_STRING = "Management of USIM files over ps service";
    public static final String ALL_AP_SERVICE_STRING = "Device management and Management of USIM files over ps service";
    public static final String NONE_AP_SERVICE_STRING = "None AP service";

    public static final int NONE_AP_SERVICE_INT = 0;
    public static final int MANAGEMENT_OF_USIM_FILES_OVER_PS_INT = 1;
    public static final int DEVICE_MANAGEMENT_OVER_PS_INT = 2;
    public static final int ALL_AP_SERVICE_INT = 3;

    private int mSubId;
    private int mPhoneId;
    private int mExceptService;
    private PsDataOffDialogListener mListener;
    private Context mContext;
    private CharSequence[] mExceptServiceNames;

    public static PsDataOffDialogFragment newInstance(int subId) {
        final PsDataOffDialogFragment dialogFragment = new PsDataOffDialogFragment();
        Bundle args = new Bundle();
        args.putInt(SUB_ID_KEY, subId);
        dialogFragment.setArguments(args);

        return dialogFragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        mSubId = args.getInt(SUB_ID_KEY);
        mPhoneId = SubscriptionManager.getPhoneId(mSubId);
        mContext = context;

        mExceptService = getExceptServices(mPhoneId, context);
        Log.d(LOG_TAG, "mExceptService is " + mExceptService);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        int title = android.R.string.dialog_alert_title;
        int message = R.string.ps_data_off_dialog;
        String exceptServiceName = "";
        mExceptServiceNames = getResources()
                .getTextArray(R.array.except_service_name);
        if (mExceptServiceNames != null && mExceptServiceNames.length > mExceptService) {
            exceptServiceName = mExceptServiceNames[mExceptService].toString();
        }
        String msg = getResources().getString(message) + exceptServiceName;
        builder.setMessage(msg)
                .setTitle(title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.yes, this)
                .setNegativeButton(android.R.string.no, this);
        return builder.create();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PS_DATA_OFF_DIALOG;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // let the host know that the positive button has been clicked
        if (which == dialog.BUTTON_POSITIVE) {
            setPsDataOff(mPhoneId, true, mExceptService, mContext);
        }
    }

    public void setController(PsDataOffPreferenceController psDataOffPreferenceController){
        mListener = (PsDataOffDialogListener) psDataOffPreferenceController;
    }

    private static void setPsDataOff(int phoneId, boolean onOff, int exceptService, Context context) {
        RadioInteractor radioInteractor = new RadioInteractor(context);

        if (radioInteractor != null) {
            radioInteractor.setPsDataOff(phoneId, onOff, exceptService);
        }
        if (onOff) {
            TelephonyManager.setTelephonyProperty(phoneId, PS_DATA_OFF_ENABLED, Integer.toString(exceptService));
        } else {
            TelephonyManager.setTelephonyProperty(phoneId, PS_DATA_OFF_ENABLED, "-1");
        }
    }

    public static int getExceptServices (int phoneId, Context context) {
        int exceptServiceState = NONE_AP_SERVICE_INT;
        TelephonyManager telephonyManager = TelephonyManager.from(context);
        TelephonyManagerEx telephonyManagerEx = TelephonyManagerEx.from(context);

        ServiceState dataRegState = telephonyManager.getServiceStateForSubscriber(phoneId);
        if (dataRegState != null && SubscriptionManager.isValidPhoneId(phoneId)) {
            if (dataRegState.getDataRoaming()) {
                exceptServiceState = telephonyManagerEx.getRomingExceptService(phoneId);
            } else if (dataRegState.getDataRegState() == ServiceState.STATE_IN_SERVICE){
                exceptServiceState = telephonyManagerEx.getHomeExceptService(phoneId);
            }
        }
        return exceptServiceState;

    }

    public String getExceptServicesString(int exceptService) {
        switch(exceptService) {
        case MANAGEMENT_OF_USIM_FILES_OVER_PS_INT:
            return MANAGEMENT_OF_USIM_FILES_OVER_PS_STRING;
        case DEVICE_MANAGEMENT_OVER_PS_INT:
            return DEVICE_MANAGEMENT_OVER_PS_STRING;
        case ALL_AP_SERVICE_INT:
            return ALL_AP_SERVICE_STRING;
        default:
            return NONE_AP_SERVICE_STRING;
        }
    }

    public void onDismiss(DialogInterface dialog) {
        if (mListener != null) {
            mListener.onDialogDismiss(this);
        }
        super.onDismiss(dialog);
    }

    public static class BootCompletedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(LOG_TAG, "action: " + action);

            if (SystemProperties.getBoolean(DEBUG_TEST, false) &&
                    TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED.equals(action)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, -1);
                int state = intent.getIntExtra(TelephonyManager.EXTRA_SIM_STATE,
                        TelephonyManager.SIM_STATE_UNKNOWN);
                int exceptService = getExceptServices(phoneId, context);
                String psDataOffState = TelephonyManager.getTelephonyProperty(phoneId, PS_DATA_OFF_ENABLED, "-1");

                if (state == TelephonyManager.SIM_STATE_LOADED) {
                    Log.d(LOG_TAG, "exceptService is " + exceptService);
                    if ("-1".equals(psDataOffState)) {
                        setPsDataOff(phoneId, false, -1, context);
                    } else {
                        setPsDataOff(phoneId, true, exceptService, context);
                    }
                }
            }
        }
    }
}

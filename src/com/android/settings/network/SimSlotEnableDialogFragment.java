package com.android.settings.network;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telephony.TelephonyManagerEx;
import android.telephony.SubscriptionManager;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import android.app.settings.SettingsEnums;
import android.widget.Toast;

import java.util.List;

public class SimSlotEnableDialogFragment extends InstrumentedDialogFragment {

    private static final String TAG = "SimSlotEnableDialogFragment";
    private static final String EXTRAS_PHONE_ID = "phone_id";
    private static final String EXTRAS_ON_OFF = "on_off";

    private int mPhoneId;
    private boolean mOnOff;
    private SimSlotEnableDialogFragmentListener mListener;

    public interface SimSlotEnableDialogFragmentListener {
        void onDialogDismiss(int phoneId);
        void onDialogAttach(InstrumentedDialogFragment dialog);
    }

    public static SimSlotEnableDialogFragment newInstance( int phoneId, boolean onOff) {
        final SimSlotEnableDialogFragment dialogFragment = new SimSlotEnableDialogFragment();

        Bundle args = new Bundle();
        args.putInt(EXTRAS_PHONE_ID, phoneId);
        args.putBoolean(EXTRAS_ON_OFF, onOff);
        dialogFragment.setArguments(args);
        return dialogFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void onAttach(Context context) {
        if (mListener != null) {
            mListener.onDialogAttach(this);
        }
        super.onAttach(context);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle bundle = getArguments();
        final Context context = getContext();

        mPhoneId = bundle.getInt(EXTRAS_PHONE_ID);
        mOnOff = bundle.getBoolean(EXTRAS_ON_OFF);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.proxy_error);
        builder.setMessage(R.string.sim_disable_prompt);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (context != null) {
                    TelephonyManagerEx tmEx = TelephonyManagerEx.from(context);
                    Log.d(TAG, "Try to set SIM enabled[" + mPhoneId + "]: " + mOnOff);
                    if (SubscriptionManager.from(context).getActiveSubscriptionInfoCount() < 2 && !mOnOff) {
                        Toast.makeText(context, R.string.cannot_disable_two_sim_card, Toast.LENGTH_SHORT).show();
                    } else if (tmEx.isSimEnabled(mPhoneId) != mOnOff) {
                        tmEx.setSimEnabled(mPhoneId, mOnOff);
                    }
                } else {
                    Log.d(TAG, "No host, dialog is out of date.");
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mListener != null) {
            mListener.onDialogDismiss(mPhoneId);
        }
        super.onDismiss(dialog);
    }

    public void setController(Preference preference) {
        mListener = (SimSlotEnableDialogFragmentListener) preference;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.MOBILE_SIM_ENABLE_DIALOG;
    }
}

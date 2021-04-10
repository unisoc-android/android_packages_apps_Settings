
package com.android.settings.network.telephony;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.telephony.TelephonyManager;

import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;

/**
 * A dialog fragment that asks the user if they are sure they want to turn off mobile data
 * always online to reduce power consumption when screen off
 */
public class MobileDataAlwaysOnlineDialogFragment extends InstrumentedDialogFragment implements OnClickListener {

    public interface MobileDataAlwaysOnlineDialogListener {
        void onDialogDismiss(InstrumentedDialogFragment dialog);
    }

    public static final String SUB_ID_KEY = "sub_id_key";

    private int mSubId;
    private MobileDataAlwaysOnlineDialogListener mListener;

    public static MobileDataAlwaysOnlineDialogFragment newInstance(int subId) {
        final MobileDataAlwaysOnlineDialogFragment dialogFragment = new MobileDataAlwaysOnlineDialogFragment();
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
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        int title = android.R.string.dialog_alert_title;
        int message = R.string.mobile_data_always_online_dialog;
        builder.setMessage(getResources().getString(message))
                .setTitle(title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.yes, this)
                .setNegativeButton(android.R.string.no, this);
        return builder.create();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.MOBILE_DATA_ALWAYS_ONLINE_DIALOG;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // let the host know that the positive button has been clicked
        if (which == dialog.BUTTON_POSITIVE) {
            setMobileDataAlwaysOnline(mSubId, false);
        }
    }

    public void setController(MobileDataAlwaysOnlinePreferenceController mobileDataAlwaysOnlineDialogListener){
        mListener = (MobileDataAlwaysOnlineDialogListener) mobileDataAlwaysOnlineDialogListener;
    }

    private void setMobileDataAlwaysOnline(int subId, boolean onOff) {
        Settings.Global.putInt(getContext().getContentResolver(),
                SettingsEx.GlobalEx.MOBILE_DATA_ALWAYS_ONLINE + subId,onOff ? 1 : 0);
    }

    public void onDismiss(DialogInterface dialog) {
        if (mListener != null) {
            mListener.onDialogDismiss(this);
        }
        super.onDismiss(dialog);
    }
}

package com.android.settings.network.telephony;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.settings.R;
import com.android.settings.network.telephony.gsm.AutoSelectPreferenceController;
import com.android.settings.network.telephony.gsm.OpenNetworkSelectPagePreferenceController;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;

public class NetworkSelectWarningDialogFragment extends InstrumentedDialogFragment implements OnClickListener {
    public static final String DIALOG_TAG = "NetworkSelectWarningDialog";
    private static final String TAG = "NetworkSelectWarning";
    private static final String KEY_SUBID = "subid";
    private static Fragment mParentFragment;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private WarningDialogListener mAutoSelectListener;
    private WarningDialogListener mOpenNetworkListener;

    public static NetworkSelectWarningDialogFragment newInstance(Fragment parentFragment,
            int subId) {
        mParentFragment = parentFragment;
        final NetworkSelectWarningDialogFragment fragment = new NetworkSelectWarningDialogFragment();
        Bundle args = new Bundle();
        args.putInt(KEY_SUBID, subId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        mSubId = args.getInt(KEY_SUBID);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(R.string.dialog_network_selection_message)
                .setPositiveButton(android.R.string.yes, this)
                .setNegativeButton(android.R.string.no, this);
        return builder.create();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.MOBILE_NETWORK_SELECT_DIALOG;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == dialog.BUTTON_POSITIVE) {
            /* UNISOC: Bug929604 Not allowed selecting operator during a call @{ */
            if (!MobileNetworkUtils.isNetworkSelectEnabled(getContext())) {
                return;
            }
            /* @} */
            Log.d(TAG, "launch  NetworkSelectSettings for " + mSubId);
            final Bundle bundle = new Bundle();
            bundle.putInt(Settings.EXTRA_SUB_ID, mSubId);
            new SubSettingLauncher(getContext())
                    .setDestination(NetworkSelectSettings.class.getName())
                    .setSourceMetricsCategory(
                            SettingsEnums.MOBILE_NETWORK_SELECT)
                    .setTitleRes(R.string.choose_network_title)
                    .setArguments(bundle)
                    .setResultListener(
                            mParentFragment,
                            MobileNetworkSettings.REQUEST_NETWORK_SELECTION_MANUALLY_DONE)
                    .launch();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mAutoSelectListener != null) {
            mAutoSelectListener.onDialogDismiss(this);
        }
        if (mOpenNetworkListener != null) {
            mOpenNetworkListener.onDialogDismiss(this);
        }
    }

    public static void setParentFragment(Fragment parentFragment) {
        mParentFragment = parentFragment;
    }

    public void registerForAutoSelect(AutoSelectPreferenceController controller) {
        mAutoSelectListener = (WarningDialogListener) controller;
    }

    public void registerForOpenNetwork(OpenNetworkSelectPagePreferenceController controller) {
        mOpenNetworkListener = (WarningDialogListener) controller;
    }

    public interface WarningDialogListener {
        void onDialogDismiss(InstrumentedDialogFragment dialog);
    }
}

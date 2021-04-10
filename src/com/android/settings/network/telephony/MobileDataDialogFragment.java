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

package com.android.settings.network.telephony;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.android.ims.internal.ImsManagerEx;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
// UNISOC:Bug 1128242 hot plug out sim card dismiss switch data dialog
import com.android.settings.network.SimStateChangeListener;

import java.util.List;

/**
 * Dialog Fragment to show dialog for "mobile data"
 *
 * 1. When user want to disable data in single sim case, show dialog to confirm
 * 2. When user want to enable data in multiple sim case, show dialog to confirm to disable other
 * sim
 */
public class MobileDataDialogFragment extends InstrumentedDialogFragment implements
        DialogInterface.OnClickListener, SimStateChangeListener.SimStateChangeListenerClient {

    public static final int TYPE_DISABLE_DIALOG = 0;
    public static final int TYPE_MULTI_SIM_DIALOG = 1;
    public static final int TYPE_ATTENTION_CHANGE_DIALOG =2;
    private static final String TAG = "MobileDataDialogFragment";

    private static final String ARG_DIALOG_TYPE = "dialog_type";
    private static final String ARG_SUB_ID = "subId";

    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private int mType;
    private int mSubId;
    // UNISOC:Bug 1128242 hot plug out sim card dismiss switch data dialog
    private SimStateChangeListener mSimStateChangeListener;

    public static MobileDataDialogFragment newInstance(int type, int subId) {
        final MobileDataDialogFragment dialogFragment = new MobileDataDialogFragment();

        Bundle args = new Bundle();
        args.putInt(ARG_DIALOG_TYPE, type);
        args.putInt(ARG_SUB_ID, subId);
        dialogFragment.setArguments(args);

        return dialogFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTelephonyManager = TelephonyManager.from(getContext()).createForSubscriptionId(mSubId);
        mSubscriptionManager = getContext().getSystemService(SubscriptionManager.class);
        // UNISOC:Bug 1128242 hot plug out sim card dismiss switch data dialog
        mSimStateChangeListener = new SimStateChangeListener(getContext(), this);
        mSimStateChangeListener.start();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle bundle = getArguments();
        final Context context = getContext();

        mType = bundle.getInt(ARG_DIALOG_TYPE);
        mSubId = bundle.getInt(ARG_SUB_ID);

        switch (mType) {
            case TYPE_DISABLE_DIALOG:
                return new AlertDialog.Builder(context)
                        .setMessage(R.string.data_usage_disable_mobile)
                        .setPositiveButton(android.R.string.ok, this)
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
            case TYPE_MULTI_SIM_DIALOG:
                final SubscriptionInfo currentSubInfo =
                        mSubscriptionManager.getActiveSubscriptionInfo(mSubId);
                final SubscriptionInfo nextSubInfo =
                        mSubscriptionManager.getDefaultDataSubscriptionInfo();

                final String previousName = (nextSubInfo == null)
                        ? getContext().getResources().getString(
                        R.string.sim_selection_required_pref)
                        : nextSubInfo.getDisplayName().toString();

                final String newName = (currentSubInfo == null)
                        ? getContext().getResources().getString(
                        R.string.sim_selection_required_pref)
                        : currentSubInfo.getDisplayName().toString();

                return new AlertDialog.Builder(context)
                        .setTitle(context.getString(R.string.sim_change_data_title, newName))
                        .setMessage(context.getString(R.string.sim_change_data_message,
                                newName, previousName))
                        .setPositiveButton(
                                context.getString(R.string.sim_change_data_ok, newName),
                                this)
                        .setNegativeButton(R.string.cancel, null)
                        .create();
            /* UNISOC:Add for Reliance custom set default data sub need pop dialog @{ */
            case TYPE_ATTENTION_CHANGE_DIALOG:
                int phoneId = mSubscriptionManager.getSlotIndex(mSubId);
                String title = getContext().getResources().getString(R.string.confirm_data_dialog_title, phoneId + 1);
                return new AlertDialog.Builder(context).setTitle(title)
                        .setMessage(context.getString(R.string.confirm_data_dialog_message))
                        .setPositiveButton(android.R.string.ok, this)
                        .setNegativeButton(android.R.string.cancel,null)
                        .create();
            /* @} */
            default:
                throw new IllegalArgumentException("unknown type " + mType);
        }
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.MOBILE_DATA_DIALOG;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (mType) {
            case TYPE_DISABLE_DIALOG:
                MobileNetworkUtils.setMobileDataEnabled(getContext(), mSubId, false /* enabled */,
                        false /* disableOtherSubscriptions */);
                break;
            case TYPE_MULTI_SIM_DIALOG:
                //UNISOC:Bug 782772 Do not allow to switch data on non L+L verson
                //UNISOC:Do not allow to switch data for DSDS feature during call
                if (isPhoneStateInCall()) {
                    Toast.makeText(getContext(),
                            getContext().getResources().getString(R.string.do_not_switch_default_data_subscription),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                mSubscriptionManager.setDefaultDataSubId(mSubId);
                MobileNetworkUtils.setMobileDataEnabled(getContext(), mSubId, true /* enabled */,
                        true /* disableOtherSubscriptions */);
                break;
            /* UNISOC:Add for Reliance custom set default data sub need pop dialog @{ */
            case TYPE_ATTENTION_CHANGE_DIALOG:
                mSubscriptionManager.setDefaultDataSubId(mSubId);
                MobileNetworkUtils.setMobileDataEnabled(getContext(), mSubId, true /* enabled */,
                        true /* disableOtherSubscriptions */);
                break;
            /* @} */
            default:
                throw new IllegalArgumentException("unknown type " + mType);
        }
    }

    /** UNISOC:Bug 1128242 hot plug out sim card dismiss switch data dialog @{ */
    @Override
    public void onDestroy() {
        mSimStateChangeListener.stop();
        super.onDestroy();
    }
    /** @} */

    /* UNISOC:Bug 782772 Do not allow to switch data on non L+L verson @{ */
    private boolean isPhoneStateInCall() {
        final List<SubscriptionInfo> subInfoList =
                mSubscriptionManager.getActiveSubscriptionInfoList(true);
        if (subInfoList != null) {
            for (SubscriptionInfo subInfo : subInfoList) {
                final int callState = mTelephonyManager.getCallStateForSlot(subInfo.getSimSlotIndex());
                if (callState != TelephonyManager.CALL_STATE_IDLE) {
                    return true;
                }
            }
        }
        return false;
    }
    /* @} */

    /** UNISOC:Bug 1128242 hot plug out sim card dismiss switch data dialog @{ */
    @Override
    public void onSimAbsent(int phoneId) {
        Log.d(TAG, "MobileDataDialogFragment: onSimAbsent phoneId="+ phoneId);
        Dialog dialog = getDialog();
        if (dialog != null){
            dialog.dismiss();
        } else {
            Log.d(TAG, "MobileDataDialogFragment: dialog is null");
        }
    }
    /** @} */
}

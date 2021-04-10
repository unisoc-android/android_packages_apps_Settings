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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.network.MobileDataContentObserver;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.List;
/**
 * Preference controller for "Mobile data"
 */
public class MobileDataPreferenceController extends TelephonyTogglePreferenceController
        implements LifecycleObserver, OnStart, OnStop {

    private static final String DIALOG_TAG = "MobileDataDialog";
    private static final String LOG_TAG = "MobileDataPreferenceController";

    private SwitchPreference mPreference;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private MobileDataContentObserver mDataContentObserver;
    private FragmentManager mFragmentManager;
    @VisibleForTesting
    int mDialogType;
    @VisibleForTesting
    boolean mNeedDialog;

    private String[] mIccIds;
    private int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();

    // UNISOC: add for Bug 1164048
    private MobileDataDialogFragment mDialogFragment = null;

    public MobileDataPreferenceController(Context context, String key) {
        super(context, key);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mDataContentObserver = new MobileDataContentObserver(new Handler(Looper.getMainLooper()));
        mDataContentObserver.setOnMobileDataChangedListener(() -> updateState(mPreference));
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        return subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ? AVAILABLE
                : DISABLED_DEPENDENT_SETTING;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mDataContentObserver.register(mContext, mSubId);
        }
    }

    @Override
    public void onStop() {
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mDataContentObserver.unRegister(mContext);
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            if (mNeedDialog) {
                showDialog(mDialogType);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        mNeedDialog = isDialogNeeded();

        if (!mNeedDialog) {
            // Update data directly if we don't need dialog
            MobileNetworkUtils.setMobileDataEnabled(mContext, mSubId, isChecked, false);
            return true;
        }

        return false;
    }

    @Override
    public boolean isChecked() {
        return mTelephonyManager.isDataEnabled();
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (isOpportunistic()) {
            preference.setEnabled(false);
            preference.setSummary(R.string.mobile_data_settings_summary_auto_switch);
        } else {
            //UNISOC:disable switch default data sim function on specific version
            preference.setEnabled(canSetDefaultDataSubId(mSubId));
            preference.setSummary(R.string.mobile_data_settings_summary);
        }
    }

    private boolean isOpportunistic() {
        SubscriptionInfo info = mSubscriptionManager.getActiveSubscriptionInfo(mSubId);
        return info != null && info.isOpportunistic();
    }

    public void init(FragmentManager fragmentManager, int subId) {
        mFragmentManager = fragmentManager;
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);
        mIccIds = mContext.getResources().getStringArray(R.array.operator_required_iccid_list);
    }

    @VisibleForTesting
    boolean isDialogNeeded() {
        final boolean enableData = !isChecked();
        final boolean isMultiSim = (mTelephonyManager.getSimCount() > 1);
        final int defaultSubId = mSubscriptionManager.getDefaultDataSubscriptionId();
        final boolean needToDisableOthers = mSubscriptionManager
                .isActiveSubscriptionId(defaultSubId) && defaultSubId != mSubId;
        if (enableData && isMultiSim && needToDisableOthers) {
            mDialogType = MobileDataDialogFragment.TYPE_MULTI_SIM_DIALOG;
            /* UNISOC:Add for Reliance custom set default data sub need pop dialog @{ */
            if (!MobileNetworkUtils.isSupportLTE(mContext,mSubId) && MobileNetworkUtils.isSupportLTE(mContext,defaultSubId)
                    && isCustomOperatorName(defaultSubId) && !isCustomOperatorName(mSubId)) {
                mDialogType = MobileDataDialogFragment.TYPE_ATTENTION_CHANGE_DIALOG;
            }
            /* @} */
            return true;
        }
        return false;
    }

    private void showDialog(int type) {
        if (mDialogFragment != null) {
            mDialogFragment.dismiss();
        }
        mDialogFragment = MobileDataDialogFragment.newInstance(type,
                mSubId);
        mDialogFragment.show(mFragmentManager, DIALOG_TAG);
    }

    private boolean isSpecificOperatorSim () {
        final List<SubscriptionInfo> subInfoList = mSubscriptionManager.getActiveSubscriptionInfoList();
        int specificOperatorSimCount = 0;

        if (subInfoList != null) {
            for (SubscriptionInfo subInfo : subInfoList) {
                String iccid = subInfo.getIccId();
                for (String iccidString : mIccIds) {
                    if (iccid != null && iccid.startsWith(iccidString)) {
                        specificOperatorSimCount++;
                        break;
                    }
                }
            }
        }
        if (subInfoList != null && subInfoList.size() == mPhoneCount
                && specificOperatorSimCount == 1) {
            return true;
        }
        return false;
    }

    private boolean canSetDefaultDataSubId(int subId) {
        SubscriptionInfo subscriptionInfo = mSubscriptionManager
                .getActiveSubscriptionInfo(subId);
        if (subscriptionInfo != null && isSpecificOperatorSim()) {
            String iccid = subscriptionInfo.getIccId();
            for (String iccidString : mIccIds) {
                if (iccid.startsWith(iccidString)) {
                    return true;
                }
            }
            Log.d(LOG_TAG,"canSetDefaultDataSubId: " +
                    "could not switch default data sim for specific sim");
            return false;
        }
        return true;
    }

    /* UNISOC:Add for Reliance custom set default data sub need pop dialog @{ */
    private boolean isCustomOperatorName(int subId) {
        String simOperatorName = mTelephonyManager.getSimOperatorName(subId);
        if (!TextUtils.isEmpty(simOperatorName)) {
            return simOperatorName.matches(
                    mContext.getResources().getString(R.string.operator_required_name));
        }
        return false;
    }
    /* @} */
}

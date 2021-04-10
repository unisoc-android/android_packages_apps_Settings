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

package com.android.settings.homepage.contextualcards.conditional;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkPolicyManager;
import android.os.Bundle;

import com.android.settings.R;
import com.android.settings.Settings;
import com.android.settings.homepage.contextualcards.ContextualCard;
import static com.android.settings.network.NetworkDashboardFragment.ARG_SHOW_EXPAND_BUTTON;
import static com.android.settings.SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS;

import java.util.Objects;

public class BackgroundDataConditionController implements ConditionalCardController {
    static final int ID = Objects.hash("BackgroundDataConditionController");

    private final Context mAppContext;
    private final ConditionManager mConditionManager;
    private final NetworkPolicyManager mNetworkPolicyManager;

    public BackgroundDataConditionController(Context appContext, ConditionManager manager) {
        mAppContext = appContext;
        mConditionManager = manager;
        mNetworkPolicyManager =
                (NetworkPolicyManager) appContext.getSystemService(Context.NETWORK_POLICY_SERVICE);
    }

    @Override
    public long getId() {
        return ID;
    }

    @Override
    public boolean isDisplayable() {
        return mNetworkPolicyManager.getRestrictBackground();
    }

    @Override
    public void onPrimaryClick(Context context) {
        //Modify for bug1130300、1130366, Change the display activity
        //UNISOC: Modify for bug1136114, We do not want to display an advanced button.
        Intent intent = new Intent(context, Settings.NetworkDashboardActivity.class);
        final Bundle bundle = new Bundle();
        bundle.putBoolean(ARG_SHOW_EXPAND_BUTTON, false);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, bundle);
        context.startActivity(intent);
    }

    @Override
    public void onActionClick() {
        mNetworkPolicyManager.setRestrictBackground(false);
        mConditionManager.onConditionChanged();
    }

    @Override
    public ContextualCard buildContextualCard() {
        return new ConditionalContextualCard.Builder()
                .setConditionId(ID)
                .setMetricsConstant(SettingsEnums.SETTINGS_CONDITION_BACKGROUND_DATA)
                .setActionText(mAppContext.getText(R.string.condition_turn_off))
                .setName(mAppContext.getPackageName() + "/"
                        + mAppContext.getText(R.string.condition_bg_data_title))
                .setTitleText(mAppContext.getText(R.string.condition_bg_data_title).toString())
                .setSummaryText(mAppContext.getText(R.string.condition_bg_data_summary).toString())
                .setIconDrawable(mAppContext.getDrawable(R.drawable.ic_data_saver))
                .setViewType(ConditionContextualCardRenderer.VIEW_TYPE_HALF_WIDTH)
                .build();
    }

    @Override
    public void startMonitoringStateChange() {

    }

    @Override
    public void stopMonitoringStateChange() {

    }
}

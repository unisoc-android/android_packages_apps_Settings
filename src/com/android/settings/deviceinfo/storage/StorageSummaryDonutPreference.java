/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.settings.deviceinfo.storage;

import android.app.AppOpsManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Typeface;
import android.os.storage.StorageManager;
import android.text.TextPaint;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.widget.DonutView;

/**
 * StorageSummaryDonutPreference is a preference which summarizes the used and remaining storage left
 * on a given storage volume. It is visualized with a donut graphing the % used.
 */
public class StorageSummaryDonutPreference extends Preference implements View.OnClickListener {
    /* Bug1106941: no need to startActivity if StorageManager application is disabled @{ */
    private static final String TAG = "StorageSummaryDonutPreference";
    private static final String STORAGE_MANAGER_PACKAGE_NAME = "com.android.storagemanager";
    private AppOpsManager mAppOpsManager;
    private PackageInfo mPackageInfo;
    /* @} */

    private double mPercent = -1;

    public StorageSummaryDonutPreference(Context context) {
        this(context, null);
    }

    public StorageSummaryDonutPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setLayoutResource(R.layout.storage_summary_donut);
        setEnabled(false);
        /* Bug1106941: no need to startActivity if StorageManager application is disabled @{ */
        mAppOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        try {
            mPackageInfo = context.getPackageManager().getPackageInfo(STORAGE_MANAGER_PACKAGE_NAME,
                    PackageManager.MATCH_DISABLED_COMPONENTS |
                    PackageManager.MATCH_ANY_USER |
                    PackageManager.GET_SIGNATURES |
                    PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            Log.e(TAG, STORAGE_MANAGER_PACKAGE_NAME + " not found!");
        }
        /* @} */
    }

    public void setPercent(long usedBytes, long totalBytes) {
        if (totalBytes == 0) {
            return;
        }

        mPercent = usedBytes / (double) totalBytes;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        view.itemView.setClickable(false);

        final DonutView donut = (DonutView) view.findViewById(R.id.donut);
        if (donut != null) {
            donut.setPercentage(mPercent);
        }

        final Button deletionHelperButton = (Button) view.findViewById(R.id.deletion_helper_button);
        if (deletionHelperButton != null) {
            deletionHelperButton.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        if (v != null && R.id.deletion_helper_button == v.getId()) {
            Context context = getContext();
            FeatureFactory.getFactory(context).getMetricsFeatureProvider().action(
                    context, SettingsEnums.STORAGE_FREE_UP_SPACE_NOW);
            Intent intent = new Intent(StorageManager.ACTION_MANAGE_STORAGE);
            /* Bug1106941: no need to startActivity if StorageManager application is disabled @{ */
            if (Utils.isIntentCanBeResolved(getContext(), intent) && isAppOpsModeAllowed()) {
                getContext().startActivity(intent);
            }
            /* @} */
        }
    }

    /* Bug1106941: no need to startActivity if StorageManager application is disabled @{ */
    private boolean isAppOpsModeAllowed(){
        if (mPackageInfo == null) {
            return false;
        }
        int mode = mAppOpsManager.checkOpNoThrow(AppOpsManager.OP_GET_USAGE_STATS,
                mPackageInfo.applicationInfo.uid, STORAGE_MANAGER_PACKAGE_NAME);
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT;
    }
    /* @} */

    private static class BoldLinkSpan extends StyleSpan {
        public BoldLinkSpan() {
            super(Typeface.BOLD);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(ds.linkColor);
        }
    }
}

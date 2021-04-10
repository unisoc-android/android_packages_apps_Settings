/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.app.UiModeManager;
import android.content.Context;
import android.graphics.Color;
import android.os.PowerManager;
import android.util.MathUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

public class StorageSummaryPreference extends Preference {
    private int mPercent = -1;
    private TextView mSummary;
    private UiModeManager mUiModeManager;
    private PowerManager mPowerManager;

    public StorageSummaryPreference(Context context) {
        super(context);

        mUiModeManager = context.getSystemService(UiModeManager.class);
        //bug 1145820 : update summary text color when power save mode is on
        mPowerManager = context.getSystemService(PowerManager.class);
        setLayoutResource(R.layout.storage_summary);
        setEnabled(false);
    }

    public void setPercent(long usedBytes, long totalBytes) {
        /*UNISOC:1156388 If the SD card is damaged, cannot get the correct totalBytes @{*/
        if (totalBytes <= 0) {
            mPercent = 0;
            return;
        }
        /* @} */
        mPercent = MathUtils.constrain((int) ((usedBytes * 100) / totalBytes),
                (usedBytes > 0) ? 1 : 0, 100);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        final ProgressBar progress = (ProgressBar) view.findViewById(android.R.id.progress);
        if (mPercent != -1) {
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(mPercent);
            progress.setScaleY(7f);
        } else {
            progress.setVisibility(View.GONE);
        }

        mSummary = (TextView) view.findViewById(android.R.id.summary);
        updateSummaryTextColor();

        super.onBindViewHolder(view);
    }

    /*bug 1138566 : update summary text color with ui night mode @{ */
    private void updateSummaryTextColor() {
        int mode = mUiModeManager.getNightMode();
        if (mSummary != null) {
            if (mode == UiModeManager.MODE_NIGHT_YES || isPowerSaveMode()) {
                mSummary.setTextColor(Color.parseColor("#8aFFFFFF"));
            } else {
                mSummary.setTextColor(Color.parseColor("#8a000000"));
            }
        }
    }
    /* @} */

    /* bug 1145820 : update summary text color when power save mode is on@{ */
    boolean isPowerSaveMode() {
        return mPowerManager.isPowerSaveMode();
    }
    /* @} */
}

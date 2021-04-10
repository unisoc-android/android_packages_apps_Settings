/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.wifi;

import android.content.Context;
import android.net.wifi.WifiFeaturesUtils;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;

/**
 * {@link AbstractPreferenceController} that controls whether we use WLAN+ function.
 */
public class WlanPlusPreferenceController extends TogglePreferenceController {


    public WlanPlusPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return isSupportWlanPlus() ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isChecked() {
        return openWlanPlus();
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Global.putInt(mContext.getContentResolver(),
                WifiFeaturesUtils.WLAN_PLUS_ON, isChecked ? 1 : 0);
    }

    private boolean isSupportWlanPlus() {
        return WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_WLAN_PLUS;
    }

    private boolean openWlanPlus() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                WifiFeaturesUtils.WLAN_PLUS_ON, 0) == 1;
    }
}
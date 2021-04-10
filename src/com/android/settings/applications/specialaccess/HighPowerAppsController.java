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

package com.android.settings.applications.specialaccess;

import android.content.Context;
import android.os.SystemProperties;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class HighPowerAppsController extends BasePreferenceController {
    //UNISOC: 1073195 modifed for Power saving management, if support sprd power manager, hide aosp high power app
    private final boolean isSupportSprdPowerManager = (1 == SystemProperties.getInt("persist.sys.pwctl.enable", 1));

    public HighPowerAppsController(Context context, String key) {
        super(context, key);
    }

    @AvailabilityStatus
    public int getAvailabilityStatus() {
        //UNISOC: 1073195 modifed for Power saving management, if support sprd power manager, hide aosp high power app
        return mContext.getResources().getBoolean(R.bool.config_show_high_power_apps)
                && !isSupportSprdPowerManager
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }
}
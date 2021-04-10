/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.os.SystemProperties;

import androidx.annotation.VisibleForTesting;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.deviceinfo.SupportCPVersion;

public class BasebandVersionPreferenceController extends BasePreferenceController {

    @VisibleForTesting
    static final String BASEBAND_PROPERTY = "gsm.version.baseband";

    public BasebandVersionPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return !Utils.isWifiOnly(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        /* UNISOC:1072208 Support "Show CP2 Version" @{ */
        String basebandInfo = "";
        if (mContext.getResources().getBoolean(R.bool.config_support_showCp2Info)){
            // return baseband version + cp2 version
            basebandInfo = SupportCPVersion.getInstance().getBasedSummary(mContext,
                    BASEBAND_PROPERTY);
        } else {
            // return baseband version only
            basebandInfo = SystemProperties.get(BASEBAND_PROPERTY,
                    mContext.getString(R.string.device_info_default));
        }
        return basebandInfo;
        /* @} */
    }
}

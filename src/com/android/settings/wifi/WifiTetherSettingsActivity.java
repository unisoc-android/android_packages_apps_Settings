/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Intent;
import android.net.wifi.WifiFeaturesUtils;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settings.R;
import com.android.settings.SettingsActivity;

public class WifiTetherSettingsActivity extends SettingsActivity {

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_FEATURES) {
            modIntent.putExtra(EXTRA_SHOW_FRAGMENT, "com.android.settings.wifi.tether.SprdWifiTetherSettings");
        }
        return modIntent;
    }

}


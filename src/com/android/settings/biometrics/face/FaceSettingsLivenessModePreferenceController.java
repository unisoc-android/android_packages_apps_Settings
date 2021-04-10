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
 * limitations under the License
 */

package com.android.settings.biometrics.face;

import static android.provider.Settings.Secure.FACE_UNLOCK_REQUIRE_LIVENESSMODE;

import android.content.Context;
import android.hardware.face.FaceManager;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.settings.Utils;

/**
 * Preference controller for the liveness for face settings.
 */
public class FaceSettingsLivenessModePreferenceController extends FaceSettingsPreferenceController {

    static final String KEY = "security_settings_face_require_livenessmode";

    private static final int ON = 1;
    private static final int OFF = 0;
    private static final int DEFAULT = OFF;

    private FaceManager mFaceManager;

    public FaceSettingsLivenessModePreferenceController(Context context) {
        this(context, KEY);
    }

    public FaceSettingsLivenessModePreferenceController(Context context,
            String preferenceKey) {
        super(context, preferenceKey);
        mFaceManager = Utils.getFaceManagerOrNull(context);
    }

    @Override
    public boolean isChecked() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                FACE_UNLOCK_REQUIRE_LIVENESSMODE, DEFAULT, getUserId()) == ON;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Secure.putIntForUser(mContext.getContentResolver(),
                FACE_UNLOCK_REQUIRE_LIVENESSMODE, isChecked ? ON : OFF, getUserId());
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (!FaceSettings.isAvailable(mContext)) {
            preference.setEnabled(false);
        } else if (!mFaceManager.hasEnrolledTemplates(getUserId())) {
            preference.setEnabled(false);
        } else {
            preference.setEnabled(true);
        }
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
}

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
 * limitations under the License
 */

package com.android.settings.biometrics.face;

import android.app.Activity;
import android.content.Intent;
import android.app.settings.SettingsEnums;
import android.hardware.face.FaceManager;
import android.os.UserHandle;
import android.view.Surface;
import android.util.Log;

import com.android.settings.Utils;
import com.android.settings.biometrics.BiometricEnrollBase;
import com.android.settings.biometrics.BiometricEnrollSidecar;

import java.util.Arrays;

/**
 * Sidecar fragment to handle the state around face enrollment
 */
public class FaceEnrollSidecar extends BiometricEnrollSidecar {
    private static final String TAG = "FaceEnrollSidecar";

    private final int[] mDisabledFeatures;

    private FaceManager mFaceManager;
    private Surface mSurfaceTexture;

    public FaceEnrollSidecar(int[] disabledFeatures) {
        mDisabledFeatures = Arrays.copyOf(disabledFeatures, disabledFeatures.length);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mFaceManager = Utils.getFaceManagerOrNull(activity);
    }

    @Override
    public void startEnrollment() {
        Log.d(TAG, "startEnrollment");
        super.startEnrollment();
        if (mUserId != UserHandle.USER_NULL) {
            mFaceManager.setActiveUser(mUserId);
        }

        // Fix for bug 1227675, this may not be happen in normal mode,
        // just in case.
        if (mToken == null) {
            Activity activity = getActivity();
            Intent intent = new Intent(activity, FaceEnrollIntroduction.class);
            intent.putExtra(BiometricEnrollBase.REENROLL_TOKEN, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(intent);
            activity.finish();
            return;
        }

        if (mSurfaceTexture != null) {
            Log.d(TAG, "enroll with surface texture");
            mFaceManager.enroll(mToken, mEnrollmentCancel,
                    mEnrollmentCallback, mDisabledFeatures, mSurfaceTexture);
        } else {
            mFaceManager.enroll(mToken, mEnrollmentCancel,
                    mEnrollmentCallback, mDisabledFeatures);
        }
    }

    public void setSurfaceTexture(Surface surface) {
        mSurfaceTexture = surface;
    }

    private FaceManager.EnrollmentCallback mEnrollmentCallback
            = new FaceManager.EnrollmentCallback() {

        @Override
        public void onEnrollmentProgress(int remaining) {
            FaceEnrollSidecar.super.onEnrollmentProgress(remaining);
        }

        @Override
        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
            FaceEnrollSidecar.super.onEnrollmentHelp(helpMsgId, helpString);
        }

        @Override
        public void onEnrollmentError(int errMsgId, CharSequence errString) {
            FaceEnrollSidecar.super.onEnrollmentError(errMsgId, errString);
        }
    };

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.FACE_ENROLL_SIDECAR;
    }
}

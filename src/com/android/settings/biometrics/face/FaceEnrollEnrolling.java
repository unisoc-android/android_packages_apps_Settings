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

package com.android.settings.biometrics.face;

import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.hardware.face.Face;
import android.hardware.face.FaceManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.biometrics.BiometricEnrollBase;
import com.android.settings.biometrics.BiometricEnrollSidecar;
import com.android.settings.biometrics.BiometricErrorDialog;
import com.android.settings.biometrics.BiometricsEnrollEnrolling;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.Utils;

import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;

import java.util.ArrayList;
import java.util.List;

public class FaceEnrollEnrolling extends BiometricsEnrollEnrolling {

    private static final String TAG = "FaceEnrollEnrolling";
    private static final boolean DEBUG = true;
    private static final String TAG_FACE_PREVIEW = "tag_preview";
    private static final int FACE_ERROR_VERIFY_TOKEN_FAIL = 1004;

    private TextView mErrorText;
    private TextView mHelpText;
    private Interpolator mLinearOutSlowInInterpolator;
    private FaceEnrollPreviewFragment mPreviewFragment;
    private FaceManager mFaceManager;

    private ArrayList<Integer> mDisabledFeatures = new ArrayList<>();
    private ParticleCollection.Listener mListener = new ParticleCollection.Listener() {
        @Override
        public void onEnrolled() {
            FaceEnrollEnrolling.this.launchFinish(mToken);
        }
    };

    public interface StartedListener {
        void onEnrollStarted();
    }

    private StartedListener mStartedListener = new StartedListener() {
        @Override
        public void onEnrollStarted() {
            if (DEBUG) {
                Log.d(TAG, "onEnrollStarted");
            }
            startEnrollment();
        }
    };

    public static class FaceErrorDialog extends BiometricErrorDialog {
        static FaceErrorDialog newInstance(CharSequence msg, int msgId) {
            FaceErrorDialog dialog = new FaceErrorDialog();
            Bundle args = new Bundle();
            args.putCharSequence(KEY_ERROR_MSG, msg);
            args.putInt(KEY_ERROR_ID, msgId);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.DIALOG_FACE_ERROR;
        }

        @Override
        public int getTitleResId() {
            return R.string.security_settings_face_enroll_error_dialog_title;
        }

        @Override
        public int getOkButtonTextResId() {
            return R.string.security_settings_face_enroll_dialog_ok;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* UNISOC: Fix bug 1192961 not support screen-split in biometric enroll mode @{ */
        if (isInMultiWindowMode()){
            Toast.makeText(getApplicationContext(), R.string.not_support_in_split_mode,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        /* @} */
        setContentView(R.layout.face_enroll_enrolling);
        setHeaderText(R.string.security_settings_face_enroll_repeat_title);
        mErrorText = findViewById(R.id.error_text);
        mHelpText = findViewById(R.id.help_text);
        mLinearOutSlowInInterpolator = AnimationUtils.loadInterpolator(
                this, android.R.interpolator.linear_out_slow_in);

        mFooterBarMixin = getLayout().getMixin(FooterBarMixin.class);
        mFooterBarMixin.setSecondaryButton(
                new FooterButton.Builder(this)
                        .setText(R.string.security_settings_face_enroll_introduction_cancel)
                        .setListener(this::onSkipButtonClick)
                        .setButtonType(FooterButton.ButtonType.SKIP)
                        .setTheme(R.style.SudGlifButton_Secondary)
                        .build()
        );

        if (!getIntent().getBooleanExtra(BiometricEnrollBase.EXTRA_KEY_REQUIRE_DIVERSITY, true)) {
            mDisabledFeatures.add(FaceManager.FEATURE_REQUIRE_REQUIRE_DIVERSITY);
        }
        if (!getIntent().getBooleanExtra(BiometricEnrollBase.EXTRA_KEY_REQUIRE_VISION, true)) {
            mDisabledFeatures.add(FaceManager.FEATURE_REQUIRE_ATTENTION);
        }

        // Unisoc: fix for bug 1147394
        mFaceManager = getSystemService(FaceManager.class);
        final List<Face> faces = mFaceManager.getEnrolledFaces(mUserId);
        if (!faces.isEmpty()) {
            Log.d(TAG, "face already enrolled start deleting..");
            // Remove the first/only face
            mFaceManager.remove(faces.get(0), mUserId, null);
        }

        startPreviewFrame();
    }

    private void startPreviewFrame() {
        if (DEBUG) {
            Log.d(TAG, "startPreviewFrame");
        }
        mPreviewFragment = (FaceEnrollPreviewFragment) getSupportFragmentManager()
                .findFragmentByTag(TAG_FACE_PREVIEW);
        if (mPreviewFragment == null) {
            mPreviewFragment = new FaceEnrollPreviewFragment();
            getSupportFragmentManager().beginTransaction().add(mPreviewFragment, TAG_FACE_PREVIEW)
                    .commitAllowingStateLoss();
        }
        mPreviewFragment.setListener(mListener);
        mPreviewFragment.setStartedListener(mStartedListener);
    }

    @Override
    public void startEnrollment() {
        super.startEnrollment();
        if (DEBUG) {
            Log.d(TAG, "startEnrollment");
        }
        if (mSidecar != null && mPreviewFragment != null) {
            ((FaceEnrollSidecar) mSidecar).setSurfaceTexture(mPreviewFragment.getPreviewSurface());
        }
    }

    @Override
    protected Intent getFinishIntent() {
        return new Intent(this, FaceEnrollFinish.class);
    }

    @Override
    protected BiometricEnrollSidecar getSidecar() {
        final int[] disabledFeatures = new int[mDisabledFeatures.size()];
        for (int i = 0; i < mDisabledFeatures.size(); i++) {
            disabledFeatures[i] = mDisabledFeatures.get(i);
        }

        return new FaceEnrollSidecar(disabledFeatures);
    }

    @Override
    protected boolean shouldStartAutomatically() {
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.FACE_ENROLL_ENROLLING;
    }

    @Override
    public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
        Log.v(TAG, "onEnrollmentHelp helpMsgId: " + helpMsgId + " helpString: " + helpString);
        if (!TextUtils.isEmpty(helpString)) {
            showHelp(helpString);
        }
        mPreviewFragment.onEnrollmentHelp(helpMsgId, helpString);
    }

    @Override
    public void onEnrollmentError(int errMsgId, CharSequence errString) {
        int msgId;
        Log.v(TAG, "onEnrollmentError errMsgId: " + errMsgId + " errString: " + errString);
        switch (errMsgId) {
            case FaceManager.FACE_ERROR_TIMEOUT:
                msgId = R.string.security_settings_face_enroll_error_timeout_dialog_message;
                break;
            // Unisoc: fix for bug 1146252
            case FACE_ERROR_VERIFY_TOKEN_FAIL:
                // Unisoc: fix for bug 1162281
                Intent intent = new Intent(FaceEnrollEnrolling.this, FaceEnrollIntroduction.class);
                intent.putExtra(REENROLL_TOKEN, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
                return;
            default:
                msgId = R.string.security_settings_face_enroll_error_generic_dialog_message;
                break;
        }
        mPreviewFragment.onEnrollmentError(errMsgId, errString);
        showErrorDialog(getText(msgId), errMsgId);
    }

    @Override
    public void onEnrollmentProgressChange(int steps, int remaining) {
        if (DEBUG) {
            Log.v(TAG, "Steps: " + steps + " Remaining: " + remaining);
        }
        mPreviewFragment.onEnrollmentProgressChange(steps, remaining);

        // TODO: Update the actual animation
        showError(String.format(getText(R.string.face_data_remaining).toString(), remaining));

        // TODO: Have this match any animations that UX comes up with
        if (remaining == 0) {
            launchFinish(mToken);
        }
    }

    private void showErrorDialog(CharSequence msg, int msgId) {
        BiometricErrorDialog dialog = FaceErrorDialog.newInstance(msg, msgId);
        dialog.show(getSupportFragmentManager(), FaceErrorDialog.class.getName());
    }

    private void showHelp(CharSequence help) {
        mHelpText.setText(help);
        if (mHelpText.getVisibility() == View.INVISIBLE) {
            mHelpText.setVisibility(View.VISIBLE);
            mHelpText.setTranslationY(getResources().getDimensionPixelSize(
                    R.dimen.fingerprint_error_text_appear_distance));
            mHelpText.setAlpha(0f);
            mHelpText.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .setInterpolator(mLinearOutSlowInInterpolator)
                    .start();
        } else {
            mHelpText.animate().cancel();
            mHelpText.setAlpha(1f);
            mHelpText.setTranslationY(0f);
        }
    }

    private void showError(CharSequence error) {
        mErrorText.setText(error);
        if (mErrorText.getVisibility() == View.INVISIBLE) {
            mErrorText.setVisibility(View.VISIBLE);
            mErrorText.setTranslationY(getResources().getDimensionPixelSize(
                    R.dimen.fingerprint_error_text_appear_distance));
            mErrorText.setAlpha(0f);
            mErrorText.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .setInterpolator(mLinearOutSlowInInterpolator)
                    .start();
        } else {
            mErrorText.animate().cancel();
            mErrorText.setAlpha(1f);
            mErrorText.setTranslationY(0f);
        }
    }

    private void launchChooseLock() {
        FaceManager faceManager = Utils.getFaceManagerOrNull(this);
        if (faceManager != null) {
            final long challenge = faceManager.generateChallenge();
            ChooseLockSettingsHelper helper = new ChooseLockSettingsHelper(this);
            if (!helper.launchConfirmationActivity(CHOOSE_LOCK_GENERIC_REQUEST,
                    getString(R.string.security_settings_face_preference_title),
                    null, null, challenge, ActivityManager.getCurrentUser())) {
                Log.e(TAG, "Password not set");
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHOOSE_LOCK_GENERIC_REQUEST) {
            if (resultCode == RESULT_FINISHED || resultCode == RESULT_OK) {
                mToken = data.getByteArrayExtra(
                        ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                if (mSidecar != null) {
                    mSidecar.updateToken(mToken);
                }
                return;
            } else {
                setResult(resultCode, data);
                finish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}

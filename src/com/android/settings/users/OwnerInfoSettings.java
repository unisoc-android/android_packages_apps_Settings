/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.users;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.security.OwnerInfoPreferenceController.OwnerInfoCallback;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;
import android.util.Log;

public class OwnerInfoSettings extends InstrumentedDialogFragment implements OnClickListener {

    private static final String TAG_OWNER_INFO = "ownerInfo";

    private View mView;
    private int mUserId;
    private LockPatternUtils mLockPatternUtils;
    private EditText mOwnerInfo;
    // UNISOC: 1072210  modify for limit OwnerInfo length
    private Context mContext;
    private static final int TEXT_MAX_LENGTH = 50;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUserId = UserHandle.myUserId();
        mLockPatternUtils = new LockPatternUtils(getActivity());
        // UNISOC: 1072210  modify for limit OwnerInfo length
        mContext = getActivity();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mView = LayoutInflater.from(getActivity()).inflate(R.layout.ownerinfo, null);
        initView();
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.owner_info_settings_title)
                .setView(mView)
                .setPositiveButton(R.string.save, this)
                .setNegativeButton(R.string.cancel, this)
                .show();
    }

    private void initView() {
        String info = mLockPatternUtils.getOwnerInfo(mUserId);

        mOwnerInfo = (EditText) mView.findViewById(R.id.owner_info_edit_text);
        if (!TextUtils.isEmpty(info)) {
            mOwnerInfo.setText(info);
            mOwnerInfo.setSelection(info.length());
        }

        // UNISOC: 1072210  modify for limit OwnerInfo length
        if (mContext.getResources().getBoolean(R.bool.config_support_limitOwnerInfoLength)) {
            checkText(mOwnerInfo, mContext);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            String info = mOwnerInfo.getText().toString();
            mLockPatternUtils.setOwnerInfoEnabled(!TextUtils.isEmpty(info), mUserId);
            mLockPatternUtils.setOwnerInfo(info, mUserId);

            if (getTargetFragment() instanceof OwnerInfoCallback) {
                ((OwnerInfoCallback) getTargetFragment()).onOwnerInfoUpdated();
            }
        }
    }

    public static void show(Fragment parent) {
        if (!parent.isAdded()) return;

        final OwnerInfoSettings dialog = new OwnerInfoSettings();
        dialog.setTargetFragment(parent, 0);
        dialog.show(parent.getFragmentManager(), TAG_OWNER_INFO);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_OWNER_INFO_SETTINGS;
    }

    private void checkText(EditText mOwnerInfo, final Context context) {
        mOwnerInfo.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TEXT_MAX_LENGTH)});
        mOwnerInfo.setSelection(mOwnerInfo.getText().length());
        mOwnerInfo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() >= TEXT_MAX_LENGTH) {
                    Toast.makeText(context, R.string.name_too_long, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}

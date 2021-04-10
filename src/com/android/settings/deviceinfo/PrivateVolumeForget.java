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

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.android.settings.R;
import com.android.settings.core.InstrumentedFragment;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.search.actionbar.SearchMenuController;

import java.util.Collections;
import java.util.List;

public class PrivateVolumeForget extends InstrumentedFragment {
    @VisibleForTesting
    static final String TAG_FORGET_CONFIRM = "forget_confirm";

    private VolumeRecord mRecord;
    private StorageManager mStorage;
    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DEVICEINFO_STORAGE;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
        SearchMenuController.init(this /* host */);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mStorage = getActivity().getSystemService(StorageManager.class);
        /* Modify for bug1111754,Cannot access the storage settings from status bar @{ */
        String fsUuid = getArguments().getString(VolumeRecord.EXTRA_FS_UUID);
        if (fsUuid == null) {
            fsUuid = getIntent().getStringExtra(VolumeRecord.EXTRA_FS_UUID);
        }
        /* @} */
        // Passing null will crash the StorageManager, so let's early exit.
        if (fsUuid == null) {
            getActivity().finish();
            return null;
        }
        mRecord = mStorage.findRecordByUuid(fsUuid);

        if (mRecord == null) {
            getActivity().finish();
            return null;
        }

        final View view = inflater.inflate(R.layout.storage_internal_forget, container, false);
        final TextView body = (TextView) view.findViewById(R.id.body);
        final Button confirm = (Button) view.findViewById(R.id.confirm);

        body.setText(TextUtils.expandTemplate(getText(R.string.storage_internal_forget_details),
                mRecord.getNickname()));
        confirm.setOnClickListener(mConfirmListener);

        return view;
    }

    /* Add for bug1111754,Cannot access the storage settings from status bar @{ */
    private Intent getIntent() {
        if (getActivity() == null) {
            return null;
        }
        return getActivity().getIntent();
    }
    /* @} */

    private final OnClickListener mConfirmListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // check uuid before show dialog
            if (mStorage.findRecordByUuid(mRecord.getFsUuid()) == null) {
                getActivity().finish();
                return;
            }
            ForgetConfirmFragment.show(PrivateVolumeForget.this, mRecord.getFsUuid());
        }
    };

    public static class ForgetConfirmFragment extends InstrumentedDialogFragment {

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.DIALOG_VOLUME_FORGET;
        }

        public static void show(Fragment parent, String fsUuid) {
            final Bundle args = new Bundle();
            args.putString(VolumeRecord.EXTRA_FS_UUID, fsUuid);

            final ForgetConfirmFragment dialog = new ForgetConfirmFragment();
            dialog.setArguments(args);
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_FORGET_CONFIRM);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            final StorageManager storage = context.getSystemService(StorageManager.class);

            final String fsUuid = getArguments().getString(VolumeRecord.EXTRA_FS_UUID);
            final VolumeRecord record = storage.findRecordByUuid(fsUuid);

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(TextUtils.expandTemplate(
                    getText(R.string.storage_internal_forget_confirm_title), record.getNickname()));
            builder.setMessage(TextUtils.expandTemplate(
                    getText(R.string.storage_internal_forget_confirm), record.getNickname()));

            builder.setPositiveButton(R.string.storage_menu_forget,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            /* Bug1118250: Rename sdcard after forget volume operation cause settings crash {@ */
                            boolean isAvailable = isInsertInternalSdCardExist(storage,fsUuid);
                            Log.d(TAG_FORGET_CONFIRM, " isAvailable = " + isAvailable);
                            if (!isAvailable) {
                                storage.forgetVolume(fsUuid);
                            }
                            /* @} */
                            getActivity().finish();
                        }
                    });
            builder.setNegativeButton(R.string.cancel, null);

            return builder.create();
        }
    }

    /* Bug1118250: Rename sdcard after forget volume operation cause settings crash {@ */
    public static boolean isInsertInternalSdCardExist(StorageManager mStorageManager, String fsUuid){
        boolean isExist = false;
        final List<VolumeInfo> volumes = mStorageManager.getVolumes();
        Collections.sort(volumes, VolumeInfo.getDescriptionComparator());
        for (VolumeInfo vol : volumes) {
            if (vol.getType() == VolumeInfo.TYPE_PRIVATE) {
                Log.d(TAG_FORGET_CONFIRM, " isMounted =" + vol.isMountedReadable() +
                        " fsUuid =" + fsUuid + " vol.getFsUuid()=" + vol.getFsUuid());
                if (vol.isMountedReadable() && fsUuid != null && fsUuid.equals(vol.getFsUuid())) {
                    isExist = true;
                }
            }
        }
        return isExist;
   }
   /* @} */
}

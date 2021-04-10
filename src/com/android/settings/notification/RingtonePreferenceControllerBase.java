/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.settings.notification;

import android.content.Context;
import android.database.Cursor;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.provider.MediaStore;
import android.net.Uri;
import android.util.Log;

import androidx.preference.Preference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.utils.ThreadUtils;

public abstract class RingtonePreferenceControllerBase extends AbstractPreferenceController
        implements PreferenceControllerMixin {

    private String TAG = "RingtonePreferenceControllerBase";

    public RingtonePreferenceControllerBase(Context context) {
        super(context);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        ThreadUtils.postOnBackgroundThread(() -> updateSummary(preference));
    }

    private void updateSummary(Preference preference) {
        Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(
                mContext, getRingtoneType());
        if (!isRingtoneAvailable(ringtoneUri)) {
            //set null when returned uri is not available
            RingtoneManager.setActualDefaultRingtoneUri(mContext, getRingtoneType(), null);
        }
        ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(mContext, getRingtoneType());
        CharSequence summary = Ringtone.getTitle(
                    mContext, ringtoneUri, false /* followSettingsUri */, true /* allowRemote */);

        if (summary != null) {
            ThreadUtils.postOnMainThread(() -> preference.setSummary(summary));
        }
    }

    public abstract int getRingtoneType();

    //Bug 1177353 ringtone show some odd numbers after delete the ringtone file.
    private boolean isRingtoneAvailable(Uri ringtoneUri) {
        ContentResolver res = mContext.getContentResolver();
        if (ringtoneUri == null) return true;
        String authority = ContentProvider.getAuthorityWithoutUserId(ringtoneUri.getAuthority());
        if (MediaStore.AUTHORITY.equals(authority)) {
            try (Cursor cursor = res.query(ringtoneUri, new String[]{"_id"}, null, null, null)) {
                return (cursor != null && cursor.getCount() == 1) ? true : false;
            } catch (SecurityException se) {
               Log.e(TAG, "reading content://media/external/audio/media/id ,permission requires :", se);
            }
        }
        return false;
    }

}

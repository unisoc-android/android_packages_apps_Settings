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

package com.android.settings.wallpaper;

import android.content.Context;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.android.settings.display.WallpaperPreferenceController;
import com.android.settings.R;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_SUMMARY;

public class StyleSuggestionContentProvider extends ContentProvider {
    private static final String AUTHORITY =
        "com.android.settings.wallpaper.StyleSuggestionContentProvider";
    private static final String SUMMARY = "summary";
    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        URI_MATCHER.addURI(AUTHORITY, SUMMARY, 1);
    }

    @Override
    public Bundle call(String method, String uri, Bundle extras) {
        if (!SUMMARY.equals(method)) {
            return null;
        }
        Bundle bundle = new Bundle();
        bundle.putString(META_DATA_PREFERENCE_SUMMARY, getSummary());
        return bundle;
    }

    private String getSummary() {
        Context context = getContext();
        if(new WallpaperPreferenceController(context, "dummy key")
                .areStylesAvailable()) {
            return context.getString(R.string.style_suggestion_summary);
        }
        return context.getString(R.string.wallpaper_suggestion_summary);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}

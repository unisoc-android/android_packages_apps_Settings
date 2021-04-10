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
package com.android.settings.system;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserManager;
import android.os.Bundle;
import android.provider.SearchIndexableResource;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.search.SearchIndexable;

import java.util.Arrays;
import java.util.List;

@SearchIndexable
public class SystemDashboardFragment extends DashboardFragment {

    private static final String TAG = "SystemDashboardFrag";

    private static final String KEY_RESET = "reset_dashboard";

    public static final String EXTRA_SHOW_AWARE_DISABLED = "show_aware_dialog_disabled";
    private static final String KEY_SYSTEM_UPDATE_SETTINGS = "system_update_settings";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final PreferenceScreen screen = getPreferenceScreen();
        // We do not want to display an advanced button if only one setting is hidden
        if (getVisiblePreferenceCount(screen) == screen.getInitialExpandedChildrenCount() + 1) {
            screen.setInitialExpandedChildrenCount(Integer.MAX_VALUE);
        }

        showRestrictionDialog();
    }

    @VisibleForTesting
    public void showRestrictionDialog() {
        final Bundle args = getArguments();
        if (args != null && args.getBoolean(EXTRA_SHOW_AWARE_DISABLED, false)) {
            FeatureFactory.getFactory(getContext()).getAwareFeatureProvider()
                    .showRestrictionDialog(this);
        }
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.system_dashboard_fragment;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_system_dashboard;
    }

    private int getVisiblePreferenceCount(PreferenceGroup group) {
        int visibleCount = 0;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            final Preference preference = group.getPreference(i);
            if (preference instanceof PreferenceGroup) {
                visibleCount += getVisiblePreferenceCount((PreferenceGroup) preference);
            } else if (preference.isVisible()) {
                visibleCount++;
            }
        }
        return visibleCount;
    }

    /**
     * For Search.
     */
    private final static String SYSTEM_UPDATE = "android.settings.SYSTEM_UPDATE_SETTINGS";

    private static boolean haveSpecificActivityForSystemUpdate(Context context) {
        Intent intent = new Intent(SYSTEM_UPDATE);
        if (intent != null) {
            // Find the activity that is in the system image
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags &
                        (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                    return true;
                }
            }
        }
        // Did not find a matching activity
        return false;
    }

    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.system_dashboard_fragment;
                    return Arrays.asList(sir);
                }
                /* Add for bug1182287, remove system update search if no system update activity @{ */
                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    UserManager um = UserManager.get(context);
                    if (!um.isAdminUser() || !haveSpecificActivityForSystemUpdate(context)) {
                        keys.add(KEY_SYSTEM_UPDATE_SETTINGS);
                    }
                    return keys;
                }
                /* @} */
            };
}

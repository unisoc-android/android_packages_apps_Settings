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

package com.android.settings.location;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.location.SettingInjectorService;
import android.location.LocationFeaturesUtils;
import android.os.Bundle;
import android.provider.SearchIndexableResource;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.widget.SwitchBar;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.location.RecentLocationApps;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * System location settings (Settings &gt; Location). The screen has three parts:
 * <ul>
 *     <li>Platform location controls</li>
 *     <ul>
 *         <li>In switch bar: location master switch. Used to toggle location on and off.
 *         </li>
 *     </ul>
 *     <li>Recent location requests: automatically populated by {@link RecentLocationApps}</li>
 *     <li>Location services: multi-app settings provided from outside the Android framework. Each
 *     is injected by a system-partition app via the {@link SettingInjectorService} API.</li>
 * </ul>
 * <p>
 * Note that as of KitKat, the {@link SettingInjectorService} is the preferred method for OEMs to
 * add their own settings to this page, rather than directly modifying the framework code. Among
 * other things, this simplifies integration with future changes to the default (AOSP)
 * implementation.
 */
@SearchIndexable
public class LocationSettings extends DashboardFragment {

    private static final String TAG = "LocationSettings";

    private LocationSwitchBarController mSwitchBarController;
    private static boolean SUPPORT_AGPS_SETTING = false;
    private static boolean GNSS_DISABLED = false;
    private static boolean LOCATION_DISABLED = false;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.LOCATION;
    }

    @Override
    public void onAttach(Context context) {
        SUPPORT_AGPS_SETTING = LocationFeaturesUtils.getInstance(context).isSupportAgpsSettings();
        GNSS_DISABLED = LocationFeaturesUtils.getInstance(context).isGnssDisabled();
        LOCATION_DISABLED = LocationFeaturesUtils.getInstance(context).isLocationDisabled();
        super.onAttach(context);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final SettingsActivity activity = (SettingsActivity) getActivity();
        final SwitchBar switchBar = activity.getSwitchBar();
        switchBar.setSwitchBarText(R.string.location_settings_master_switch_title,
                R.string.location_settings_master_switch_title);
        mSwitchBarController = new LocationSwitchBarController(activity, switchBar,
                getSettingsLifecycle());
        switchBar.show();
        if (LOCATION_DISABLED) {
            Toast.makeText(activity, R.string.location_disabled_toast_info, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        int resId = R.xml.location_settings;
        if (!GNSS_DISABLED && SUPPORT_AGPS_SETTING) {
            resId = R.xml.location_settings_agps;
        }
        return resId;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, this, getSettingsLifecycle());
    }

    static void addPreferencesSorted(List<Preference> prefs, PreferenceGroup container) {
        // If there's some items to display, sort the items and add them to the container.
        Collections.sort(prefs,
                Comparator.comparing(lhs -> lhs.getTitle().toString()));
        for (Preference entry : prefs) {
            container.addPreference(entry);
        }
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_location_access;
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, LocationSettings fragment, Lifecycle lifecycle) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new AppLocationPermissionPreferenceController(context, lifecycle));
        controllers.add(new LocationForWorkPreferenceController(context, lifecycle));
        controllers.add(new RecentLocationRequestPreferenceController(context, fragment, lifecycle));
        controllers.add(new LocationScanningPreferenceController(context));
        if (!GNSS_DISABLED && SUPPORT_AGPS_SETTING) {
            controllers.add(new LocationAssistGnssPreferenceController(context, lifecycle));
        }
        controllers.add(new LocationServicePreferenceController(context, fragment, lifecycle));
        controllers.add(new LocationFooterPreferenceController(context, lifecycle));
        return controllers;
    }

    /**
     * For Search.
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = (!GNSS_DISABLED && SUPPORT_AGPS_SETTING) ? R.xml.location_settings_agps : R.xml.location_settings;
                    return Arrays.asList(sir);
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(Context
                        context) {
                    return buildPreferenceControllers(context, null /* fragment */,
                            null /* lifecycle */);
                }

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                  TopLevelLocationPreferenceController controller =
                    new TopLevelLocationPreferenceController(context,"top_level_location");
                      return controller.isSearchAble();
                }
            };
}

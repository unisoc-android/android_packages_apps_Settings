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

package com.android.settings.gestures;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_2BUTTON;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

import java.util.ArrayList;

import com.sprd.settings.navigation.NavigationBarSettings;

public class SystemNavigationPreferenceController extends BasePreferenceController {

    static final String PREF_KEY_SYSTEM_NAVIGATION = "gesture_system_navigation";
    private static final String ACTION_QUICKSTEP = "android.intent.action.QUICKSTEP_SERVICE";

    public SystemNavigationPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return isGestureAvailable(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        if (isEdgeToEdgeEnabled(mContext)) {
            return mContext.getText(R.string.edge_to_edge_navigation_title);
        } else if (isSwipeUpEnabled(mContext)) {
            return mContext.getText(R.string.swipe_up_to_switch_apps_title);
        } else {
            return mContext.getText(R.string.legacy_navigation_title);
        }
    }

    static boolean isGestureAvailable(Context context) {
        // Skip if the swipe up settings are not available
        /* UNISOC: Modify for bug 1111853 @{ */
        if (!NavigationBarSettings.hasNavigationBar(context) || !context.getResources().getBoolean(
                com.android.internal.R.bool.config_swipe_up_gesture_setting_available)) {
            return false;
        }
        /* }@ */
        // Skip if the recents component is not defined
        final ComponentName recentsComponentName = ComponentName.unflattenFromString(
                context.getString(com.android.internal.R.string.config_recentsComponentName));
        if (recentsComponentName == null) {
            return false;
        }

        // Skip if the overview proxy service exists
        final Intent quickStepIntent = new Intent(ACTION_QUICKSTEP)
                .setPackage(recentsComponentName.getPackageName());
        if (context.getPackageManager().resolveService(quickStepIntent,
                PackageManager.MATCH_SYSTEM_ONLY) == null) {
            return false;
        }

        return true;
    }

    static boolean isOverlayPackageAvailable(Context context, String overlayPackage) {
        try {
            return context.getPackageManager().getPackageInfo(overlayPackage, 0) != null;
        } catch (PackageManager.NameNotFoundException e) {
            // Not found, just return unavailable
            return false;
        }
    }

    static boolean isSwipeUpEnabled(Context context) {
        /*UNISOC: Modify for bug 1111853 @{*/
        if (!NavigationBarSettings.hasNavigationBar(context) || isEdgeToEdgeEnabled(context)) {
            return false;
        }
        /*}@*/
        return NAV_BAR_MODE_2BUTTON == context.getResources().getInteger(
                com.android.internal.R.integer.config_navBarInteractionMode);
    }

    static boolean isEdgeToEdgeEnabled(Context context) {
        /*UNISOC: Modify for bug 1111853*/
        return  NavigationBarSettings.hasNavigationBar(context) && NAV_BAR_MODE_GESTURAL == context.getResources().getInteger(
                com.android.internal.R.integer.config_navBarInteractionMode);
    }

    static boolean isGestureNavSupportedByDefaultLauncher(Context context) {
        final ComponentName cn = context.getPackageManager().getHomeActivities(new ArrayList<>());
        if (cn == null) {
            // There is no default home app set for the current user, don't make any changes yet.
            return true;
        }
        ComponentName recentsComponentName = ComponentName.unflattenFromString(context.getString(
                com.android.internal.R.string.config_recentsComponentName));
        return recentsComponentName.getPackageName().equals(cn.getPackageName());
    }

    static String getDefaultHomeAppName(Context context) {
        final PackageManager pm = context.getPackageManager();
        final ComponentName cn = pm.getHomeActivities(new ArrayList<>());
        if (cn != null) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(cn.getPackageName(), 0);
                if (ai != null) {
                    return pm.getApplicationLabel(ai).toString();
                }
            } catch (final PackageManager.NameNotFoundException e) {
                // Do nothing
            }
        }
        return "";
    }
}

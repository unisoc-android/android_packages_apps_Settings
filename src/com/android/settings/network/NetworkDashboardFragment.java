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
package com.android.settings.network;

import static com.android.settings.network.MobilePlanPreferenceController.MANAGE_MOBILE_PLAN_DIALOG_ID;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.provider.SearchIndexableResource;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.FeatureFlags;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.development.featureflags.FeatureFlagPersistent;
import com.android.settings.network.MobilePlanPreferenceController.MobilePlanPreferenceHost;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.wifi.WifiMasterSwitchPreferenceController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SearchIndexable
public class NetworkDashboardFragment extends DashboardFragment implements
        MobilePlanPreferenceHost {

    private static final String TAG = "NetworkDashboardFrag";

    public static final String ARG_SHOW_EXPAND_BUTTON = "showExpandButton";

    private static final int COUNT_INVALID = -1;

    /*UNISOC: bug for 966475 @{*/
    private AlertDialog mMobilePlanDialog;
    /*UNISOC: @}*/

    private int mChildrenCount = COUNT_INVALID;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_NETWORK_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        if (FeatureFlagPersistent.isEnabled(getContext(), FeatureFlags.NETWORK_INTERNET_V2)) {
            return R.xml.network_and_internet_v2;
        } else {
            return R.xml.network_and_internet;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        /*UNISOC: Modify for bug1145683, We do not want to display an advanced button if
         * linking to this screen from condition @{*/
        Bundle args = getArguments();
        if (args != null) {
            if (!args.getBoolean(ARG_SHOW_EXPAND_BUTTON, true)) {
                mChildrenCount = Integer.MAX_VALUE;
            }
        }
        if (FeatureFlagPersistent.isEnabled(context, FeatureFlags.NETWORK_INTERNET_V2)) {
            // UNISOC:Improve the function of turning on and off the Sub
            use(MultiNetworkHeaderController.class).init(getSettingsLifecycle(),
                   getFragmentManager(), mChildrenCount);
        }
        /* @} */
        use(AirplaneModePreferenceController.class).setFragment(this);
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_network_dashboard;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, getSettingsLifecycle(), mMetricsFeatureProvider,
                this /* fragment */, this /* mobilePlanHost */);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context,
            Lifecycle lifecycle, MetricsFeatureProvider metricsFeatureProvider, Fragment fragment,
            MobilePlanPreferenceHost mobilePlanHost) {
        final MobilePlanPreferenceController mobilePlanPreferenceController =
                new MobilePlanPreferenceController(context, mobilePlanHost);
        final WifiMasterSwitchPreferenceController wifiPreferenceController =
                new WifiMasterSwitchPreferenceController(context, metricsFeatureProvider);
        MobileNetworkPreferenceController mobileNetworkPreferenceController = null;
        if (!FeatureFlagPersistent.isEnabled(context, FeatureFlags.NETWORK_INTERNET_V2)) {
            mobileNetworkPreferenceController = new MobileNetworkPreferenceController(context);
        }

        final VpnPreferenceController vpnPreferenceController =
                new VpnPreferenceController(context);
        final PrivateDnsPreferenceController privateDnsPreferenceController =
                new PrivateDnsPreferenceController(context);

        if (lifecycle != null) {
            lifecycle.addObserver(mobilePlanPreferenceController);
            lifecycle.addObserver(wifiPreferenceController);
            if (mobileNetworkPreferenceController != null) {
                lifecycle.addObserver(mobileNetworkPreferenceController);
            }
            lifecycle.addObserver(vpnPreferenceController);
            lifecycle.addObserver(privateDnsPreferenceController);
        }

        final List<AbstractPreferenceController> controllers = new ArrayList<>();

        if (FeatureFlagPersistent.isEnabled(context, FeatureFlags.NETWORK_INTERNET_V2)) {
            controllers.add(new MobileNetworkSummaryController(context, lifecycle));
        }
        if (mobileNetworkPreferenceController != null) {
            controllers.add(mobileNetworkPreferenceController);
        }
        controllers.add(new TetherPreferenceController(context, lifecycle));
        controllers.add(vpnPreferenceController);
        controllers.add(new ProxyPreferenceController(context));
        controllers.add(mobilePlanPreferenceController);
        controllers.add(wifiPreferenceController);
        controllers.add(privateDnsPreferenceController);
        return controllers;
    }

    @Override
    public void showMobilePlanMessageDialog() {
        /* UNISOC: BUG1113271 Quick click @{ */
        final MobilePlanPreferenceController controller =
                use(MobilePlanPreferenceController.class);
        setController(controller);
        final Preference pref = getPreferenceScreen().findPreference(controller.getPreferenceKey());
        pref.setEnabled(false);
        /* UNISOC:  @} */
        showDialog(MANAGE_MOBILE_PLAN_DIALOG_ID);
    }

    public Dialog onCreateDialog(int dialogId) {
        Log.d(TAG, "onCreateDialog: dialogId = " + dialogId);
        switch (dialogId) {
            case MANAGE_MOBILE_PLAN_DIALOG_ID:
                final MobilePlanPreferenceController controller =
                        use(MobilePlanPreferenceController.class);
                AlertDialog.Builder alertDialogBuilder =  new AlertDialog.Builder(getActivity());
                mMobilePlanDialog = alertDialogBuilder
                        .setMessage(controller.getMobilePlanDialogMessage())
                        .setCancelable(false)
                        .setPositiveButton(com.android.internal.R.string.ok,
                                (dialog, id) -> controller.setMobilePlanDialogMessage(null))
                        .create();
                return mMobilePlanDialog;
        }
        return super.onCreateDialog(dialogId);
    }

    @Override
    public void dismissMobilePlanMessageDialog(){
        if (mMobilePlanDialog != null){
            removeDialog(MANAGE_MOBILE_PLAN_DIALOG_ID);
        }
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        if (MANAGE_MOBILE_PLAN_DIALOG_ID == dialogId) {
            return SettingsEnums.DIALOG_MANAGE_MOBILE_PLAN;
        }
        return 0;
    }

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    if (FeatureFlagPersistent.isEnabled(context,
                            FeatureFlags.NETWORK_INTERNET_V2)) {
                        sir.xmlResId = R.xml.network_and_internet_v2;
                    } else {
                        sir.xmlResId = R.xml.network_and_internet;
                    }
                    return Arrays.asList(sir);
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(Context
                        context) {
                    return buildPreferenceControllers(context, null /* lifecycle */,
                            null /* metricsFeatureProvider */, null /* fragment */,
                            null /* mobilePlanHost */);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    // Remove master switch as a result
                    keys.add(WifiMasterSwitchPreferenceController.KEY_TOGGLE_WIFI);
                    return keys;
                }
            };
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.wifi.tether;

import static android.net.wifi.WifiManager.WIFI_AP_CONNECTION_CHANGED_ACTION;
import static android.net.wifi.WifiManager.WIFI_AP_CLIENT_DETAILINFO_AVAILABLE_ACTION;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiFeaturesUtils;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.UserManager;
import androidx.annotation.VisibleForTesting;
import android.util.Log;
import android.provider.SearchIndexableResource;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.dashboard.RestrictedDashboardFragment;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.SwitchBarController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settings.search.BaseSearchIndexProvider;
import android.app.Activity;
import android.provider.Settings;
import android.database.ContentObserver;

import androidx.preference.SwitchPreference;
import androidx.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import android.widget.Button;
import android.view.LayoutInflater;
import android.view.View;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settingslib.TetherUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SprdWifiTetherSettings extends RestrictedDashboardFragment
        implements WifiTetherBasePreferenceController.OnTetherConfigUpdateListener {

    private static final String TAG = "SprdWifiTetherSettings";
    private static final IntentFilter TETHER_STATE_CHANGE_FILTER;
    private static final String KEY_WIFI_TETHER_SCREEN = "wifi_tether_settings_screen";
    @VisibleForTesting
    static final String KEY_WIFI_TETHER_NETWORK_NAME = "wifi_tether_network_name";
    @VisibleForTesting
    static final String KEY_WIFI_TETHER_NETWORK_PASSWORD = "wifi_tether_network_password";
    @VisibleForTesting
    static final String KEY_WIFI_TETHER_AUTO_OFF = "wifi_tether_auto_turn_off";
    @VisibleForTesting
    static final String KEY_WIFI_TETHER_NETWORK_AP_BAND = "wifi_tether_network_ap_band";

    private static final String HOTSPOT_CONNECTED_STATIONS = "hotspot_connected_stations";
    private static final String HOTSPOT_NO_CONNECTED_STATION = "hotspot_no_connected_station";
    private static final String HOTSPOT_BLOCKED_STATIONS = "hotspot_blocked_stations";
    private static final String HOTSPOT_NO_BLOCKED_STATION = "hotspot_no_blocked_station";
    private static final String HOTSPOT_WHITELIST_STATIONS = "hotspot_whitelist_stations";
    private static final String HOTSPOT_NO_WHITELIST_STATION = "hotspot_no_whitelist_station";
    private static final String HOTSPOT_ADD_WHITELIST = "hotspot_add_whiltelist";

    public static final String STATIONS_STATE_CHANGED_ACTION = "com.sprd.settings.STATIONS_STATE_CHANGED";
    private static final String AP_CHANNEL = "ap_channel";
    private static final String AP_5G_CHANNEL = "ap_5g_channel";
    private static final String HOTSPOT_MAX_CONNECTIONS = "limit_user";

    private String mUserConnectTitle;
    private String mUserNoConnectTitle;
    private String mUserBlockTitle;
    private String mUserNoBlockTitle;
    private PreferenceCategory mConnectedStationsCategory;
    private Preference mHotspotNoConnectedStation;
    private PreferenceCategory mBlockedStationsCategory;
    private Preference mHotspotNoBlockedStations;
    private PreferenceCategory mWhitelistStationsCategory;
    private Preference mHotspotNoWhitelistStations;
    private Preference mHotspotAddWhitelist;
    private ListPreference mHotspotMaxConnections;
    private WifiManager mWifiManager;

    public static final int DEFAULT_LIMIT = WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_MAX_NUMBER;
    private ListPreference mSoftApChanPref;
    private ListPreference mSoftAp5GChanPref;
    private WifiTetherSwitchBarController mSwitchBarController;
    private WifiTetherSSIDPreferenceController mSSIDPreferenceController;
    private WifiTetherPasswordPreferenceController mPasswordPreferenceController;
    private WifiTetherApBandPreferenceController mApBandPreferenceController;
    private WifiTetherSecurityPreferenceController mSecurityPreferenceController;
    private WifiTetherSoftApMaxNumPreferenceController mSoftApMaxNumPreferenceController;
    private WifiTetherHiddenSSIDPreferenceController mHiddenSSIDPreferenceController;
    private WifiTetherWpsConnectPreferenceController mWpsConnectPreferenceController;
    private WifiTetherSoftApManagerModeController mSoftApManagerModeController;
    private WifiTetherSoftApChannelPreferenceController mWifiTetherSoftApChannelPreferenceController;
    private WifiTetherSoftAp5GChannelPreferenceController mWifiTetherSoftAp5GChannelPreferenceController;
    private boolean mUnavailable;

    private Context mContext;

    @VisibleForTesting
    TetherChangeReceiver mTetherChangeReceiver;
    @VisibleForTesting
    WifiTetherSoftApManager mWifiTetherSoftApManager;

    static {
        TETHER_STATE_CHANGE_FILTER = new IntentFilter();
        TETHER_STATE_CHANGE_FILTER.addAction(WIFI_AP_CONNECTION_CHANGED_ACTION);
        TETHER_STATE_CHANGE_FILTER.addAction(WIFI_AP_CLIENT_DETAILINFO_AVAILABLE_ACTION);
    }

    public SprdWifiTetherSettings() {
        super(UserManager.DISALLOW_CONFIG_TETHERING);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.WIFI_TETHER_SETTINGS;
    }

    @Override
    protected String getLogTag() {
        return "SprdWifiTetherSettings";
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setIfOnlyAvailableForAdmins(true);
        if (isUiRestricted()) {
            mUnavailable = true;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mTetherChangeReceiver = new TetherChangeReceiver();

        mSSIDPreferenceController = use(WifiTetherSSIDPreferenceController.class);
        mSecurityPreferenceController = use(WifiTetherSecurityPreferenceController.class);
        mPasswordPreferenceController = use(WifiTetherPasswordPreferenceController.class);
        mApBandPreferenceController = use(WifiTetherApBandPreferenceController.class);
        mSoftApMaxNumPreferenceController= use(WifiTetherSoftApMaxNumPreferenceController.class);
        mHiddenSSIDPreferenceController = use(WifiTetherHiddenSSIDPreferenceController.class);
        mWpsConnectPreferenceController = use(WifiTetherWpsConnectPreferenceController.class);
        mSoftApManagerModeController = use(WifiTetherSoftApManagerModeController.class);
        mWifiTetherSoftApChannelPreferenceController = use(WifiTetherSoftApChannelPreferenceController.class);
        mWifiTetherSoftAp5GChannelPreferenceController = use(WifiTetherSoftAp5GChannelPreferenceController.class);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        mContext = null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mUnavailable) {
            return;
        }
        // Assume we are in a SettingsActivity. This is only safe because we currently use
        // SettingsActivity as base for all preference fragments.
        final SettingsActivity activity = (SettingsActivity) getActivity();
        final SwitchBar switchBar = activity.getSwitchBar();
        mSwitchBarController = new WifiTetherSwitchBarController(activity,
                new SwitchBarController(switchBar));
        getSettingsLifecycle().addObserver(mSwitchBarController);
        switchBar.show();

        mConnectedStationsCategory = (PreferenceCategory) findPreference(HOTSPOT_CONNECTED_STATIONS);
        mBlockedStationsCategory = (PreferenceCategory) findPreference(HOTSPOT_BLOCKED_STATIONS);
        mWhitelistStationsCategory = (PreferenceCategory) findPreference(HOTSPOT_WHITELIST_STATIONS);
        mHotspotNoConnectedStation = (Preference) findPreference(HOTSPOT_NO_CONNECTED_STATION);
        mHotspotNoBlockedStations = (Preference) findPreference(HOTSPOT_NO_BLOCKED_STATION);
        mHotspotNoWhitelistStations = (Preference) findPreference(HOTSPOT_NO_WHITELIST_STATION);
        mHotspotAddWhitelist = (Preference) findPreference(HOTSPOT_ADD_WHITELIST);
        //NOTE: Bug #505201 Add for softap support wps connect mode and hidden ssid Feature BEG-->
        mSoftApChanPref = (ListPreference) findPreference(AP_CHANNEL);
        mSoftAp5GChanPref = (ListPreference) findPreference(AP_5G_CHANNEL);
        mHotspotMaxConnections = (ListPreference) findPreference(HOTSPOT_MAX_CONNECTIONS);
        if (DEFAULT_LIMIT == 10) {
            mHotspotMaxConnections.setEntries(R.array.wifi_ap_max_connect_default);
            mHotspotMaxConnections.setEntryValues(R.array.wifi_ap_max_connect_default);
        } else if (DEFAULT_LIMIT == 8) {
            mHotspotMaxConnections.setEntries(R.array.wifi_ap_max_connect_8);
            mHotspotMaxConnections.setEntryValues(R.array.wifi_ap_max_connect_8);
        } else if (DEFAULT_LIMIT == 5) {
            mHotspotMaxConnections.setEntries(R.array.wifi_ap_max_connect_5);
            mHotspotMaxConnections.setEntryValues(R.array.wifi_ap_max_connect_5);
        }

        if (!WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_WHITE_LIST) {
            getPreferenceScreen().removePreference(mWhitelistStationsCategory);
            getPreferenceScreen().removePreference(mHotspotNoWhitelistStations);
            getPreferenceScreen().removePreference(mHotspotAddWhitelist);
        }
        initSprdWifiTethering();

        //if support sprd softap & LTE coexist, softap channel should not be set by user
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_COEXIST_LTE && mSoftApChanPref != null) {
            getPreferenceScreen().removePreference(mSoftApChanPref);
        }
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_HIDE_5G_CHANNEL && mSoftAp5GChanPref != null) {
            getPreferenceScreen().removePreference(mSoftAp5GChanPref);
        }

        if (mApBandPreferenceController.getBandIndex() == 1) {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(true);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(false);
            }
        } else if (mApBandPreferenceController.getBandIndex() == 0) {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(false);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(true);
            }
        } else {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(false);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(false);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mUnavailable) {
            if (!isUiRestrictedByOnlyAdmin()) {
                getEmptyTextView().setText(R.string.tethering_settings_not_available);
            }
            getPreferenceScreen().removeAll();
            return;
        }
        addWhitelistStations();
        if (mContext != null) {
            mContext.registerReceiver(mTetherChangeReceiver, TETHER_STATE_CHANGE_FILTER);
        }
        if (mWifiTetherSoftApManager != null) {
            mWifiTetherSoftApManager.registerSoftApCallback();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mUnavailable) {
            return;
        }
        if (mContext != null) {
            mContext.unregisterReceiver(mTetherChangeReceiver);
        }
        if (mWifiTetherSoftApManager != null) {
            mWifiTetherSoftApManager.unRegisterSoftApCallback();
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.hotspot_tether_settings;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, this::onTetherConfigUpdated);
    }
    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context,
            WifiTetherBasePreferenceController.OnTetherConfigUpdateListener listener) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
                controllers.add(new WifiTetherSSIDPreferenceController(context, listener));
        controllers.add(new WifiTetherSecurityPreferenceController(context, listener));
        controllers.add(new WifiTetherPasswordPreferenceController(context, listener));
        controllers.add(new WifiTetherApBandPreferenceController(context, listener));
        controllers.add(
                new WifiTetherAutoOffPreferenceController(context, KEY_WIFI_TETHER_AUTO_OFF));

        controllers.add(new WifiTetherSoftApMaxNumPreferenceController(context, listener));
        controllers.add(new WifiTetherWpsConnectPreferenceController(context, listener));
        controllers.add(new WifiTetherHiddenSSIDPreferenceController(context, listener));
        controllers.add(new WifiTetherSoftApManagerModeController(context, listener));
        controllers.add(new WifiTetherSoftAp5GChannelPreferenceController(context, listener));
        controllers.add(new WifiTetherSoftApChannelPreferenceController(context, listener));
        return controllers;
    }

    @Override
    public void onTetherConfigUpdated() {
        if (mSoftApManagerModeController.isSoftApModeChanged()) {
            int mode = mSoftApManagerModeController.getHotspotModeType();
            updateModePref(mode == 1);
            addWhitelistStations();
            return;
        }
        final WifiConfiguration config = buildNewConfig();
        mPasswordPreferenceController.updateVisibility(config.getAuthType());
        if (mApBandPreferenceController.getBandIndex() == 1) {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(true);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(false);
            }
        } else if (mApBandPreferenceController.getBandIndex() == 0) {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(false);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(true);
            }
        } else {
            if (mWifiTetherSoftAp5GChannelPreferenceController != null && mSoftAp5GChanPref != null){
                mWifiTetherSoftAp5GChannelPreferenceController.updateVisibility(false);
            }
            if (mWifiTetherSoftApChannelPreferenceController != null && mSoftApChanPref != null){
                mWifiTetherSoftApChannelPreferenceController.updateVisibility(false);
            }
        }

        /**
         * if soft AP is stopped, bring up
         * else restart with new config
         */
        mSSIDPreferenceController.updateWifiApConfig(config);
        mHiddenSSIDPreferenceController.updateWifiApConfig(config);
        mWpsConnectPreferenceController.updateWifiApConfig(config);
        mWifiManager.setWifiApConfiguration(config);
    }

    private WifiConfiguration buildNewConfig() {
        final WifiConfiguration config = new WifiConfiguration();
        final int securityType = mSecurityPreferenceController.getSecurityType();

        config.SSID = mSSIDPreferenceController.getSSID();
        config.allowedKeyManagement.set(securityType);
        config.preSharedKey = mPasswordPreferenceController.getPasswordValidated(securityType);
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        config.apBand = mApBandPreferenceController.getBandIndex();
        config.softApMaxNumSta = mSoftApMaxNumPreferenceController.getApMaxConnectType();
        if (config.apBand == 1) {
            config.apChannel = mWifiTetherSoftAp5GChannelPreferenceController.getApChannelType();
        } else {
            config.apChannel = mWifiTetherSoftApChannelPreferenceController.getApChannelType();
        }
        config.hiddenSSID = mHiddenSSIDPreferenceController.getIsHiddenSSID();
        Log.d(TAG, "config.apBand: " + config.apBand + ", config.apChannel:" + config.apChannel);

        return config;
    }

    private void initSprdWifiTethering() {
        final Activity activity = getActivity();
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mUserConnectTitle = activity.getString(R.string.wifi_tether_connect_title);
        mUserBlockTitle = activity.getString(R.string.wifi_tether_block_title);
        mUserNoConnectTitle = activity.getString(R.string.hotspot_connected_stations);
        mUserNoBlockTitle = activity.getString(R.string.hotspot_blocked_stations);
        updateModePref(mWifiManager.softApIsWhiteListEnabled());
        mWifiTetherSoftApManager = new WifiTetherSoftApManager(mWifiManager,
                new WifiTetherSoftApManager.WifiTetherSoftApCallback() {

                    @Override
                    public void onStateChanged(int state, int failureReason) {
                        handleWifiApStateChanged(state);
                    }

                    @Override
                    public void onNumClientsChanged(int numClients) {
                    }
                });
    }

    private void handleWifiApStateChanged(int state) {
        Log.d(TAG, "SoftAPCallback state = " + state);
        mSSIDPreferenceController.updateWifiApState(state);
        mHiddenSSIDPreferenceController.updateWifiApState(state);
        mWpsConnectPreferenceController.updateWifiApState(state);
        updateDisplayWithNewConfig();
        if (state == WifiManager.WIFI_AP_STATE_DISABLING ||
            state == WifiManager.WIFI_AP_STATE_DISABLED) {
            clearConnectedAndBlockedStation();
        } else if (state == WifiManager.WIFI_AP_STATE_ENABLED) {
            updateStations();
        }
    }

    private void clearConnectedAndBlockedStation() {
        mConnectedStationsCategory.removeAll();
        mConnectedStationsCategory.addPreference(mHotspotNoConnectedStation);
        mConnectedStationsCategory.setTitle(mUserNoConnectTitle);
        mBlockedStationsCategory.removeAll();
        mBlockedStationsCategory.addPreference(mHotspotNoBlockedStations);
        mBlockedStationsCategory.setTitle(mUserNoBlockTitle);
    }

    private void updateStations() {
        addConnectedStations();
        addBlockedStations();
        addWhitelistStations();
    }

    private void addConnectedStations() {
        List<String> mConnectedStationsDetail = mWifiManager.softApGetConnectedStationsDetail();
        mConnectedStationsCategory.removeAll();
        if (mConnectedStationsDetail.size() == 0 || mConnectedStationsDetail.isEmpty()) {
            mConnectedStationsCategory.addPreference(mHotspotNoConnectedStation);
            mConnectedStationsCategory.setTitle(mUserNoConnectTitle);
            return;
        }
        if (mContext == null) {
            return;
        }
        mConnectedStationsCategory.setTitle(mConnectedStationsDetail.size() + mUserConnectTitle);
        for (String mConnectedStationsStr : mConnectedStationsDetail) {
            String[] mConnectedStations = mConnectedStationsStr.split(" ");
            if (mConnectedStations.length == 3) {
                mConnectedStationsCategory.addPreference(new Station(mContext, mConnectedStations[2], mConnectedStations[0], mConnectedStations[1], true, false));
            } else {
                mConnectedStationsCategory.addPreference(new Station(mContext, null, mConnectedStations[0], null, true, false));
            }
        }
    }

    private void addBlockedStations() {
        List<String> mBlockedStationsDetail = mWifiManager.softApGetBlockedStationsDetail();
        mBlockedStationsCategory.removeAll();
        if (mBlockedStationsDetail.size() == 0 || mBlockedStationsDetail.isEmpty()) {
            mBlockedStationsCategory.addPreference(mHotspotNoBlockedStations);
            mBlockedStationsCategory.setTitle(mUserNoBlockTitle);
            return;
        }
        if (mContext == null) {
            return;
        }
        mBlockedStationsCategory.setTitle(mBlockedStationsDetail.size() + mUserBlockTitle);
        for (String mBlockedStationsStr : mBlockedStationsDetail) {
            String[] mBlockedStations = mBlockedStationsStr.split(" ");
            if (mBlockedStations.length == 2) {
                mBlockedStationsCategory.addPreference(new Station(mContext, mBlockedStations[1], mBlockedStations[0], null, false, false));
            } else {
                mBlockedStationsCategory.addPreference(new Station(mContext, null, mBlockedStations[0], null, false, false));
            }
        }
    }

    private void addWhitelistStations() {
         List<String> mWhitelistStationsDetail = mWifiManager.softApGetClientWhiteList();
         mWhitelistStationsCategory.removeAll();
         if (mWhitelistStationsDetail.size() == 0 || mWhitelistStationsDetail.isEmpty() || mContext == null) {
             return;
         }
         for (String mWhitelistStationsStr : mWhitelistStationsDetail) {
             String[] mWhitelistStations = mWhitelistStationsStr.split(" ");
             int len = mWhitelistStations[0].length();
             if (mWhitelistStations.length >= 2) {
                 mWhitelistStationsCategory.addPreference(new Station(mContext, mWhitelistStationsStr.substring(len+1), mWhitelistStations[0], null, false, true));
             } else {
                 mWhitelistStationsCategory.addPreference(new Station(mContext, null, mWhitelistStations[0], null, false, true));
             }
         }
     }

    private void startTether() {
        mSwitchBarController.startTether();
    }

    private void updateDisplayWithNewConfig() {
        use(WifiTetherSSIDPreferenceController.class)
                .updateDisplay();
        use(WifiTetherWpsConnectPreferenceController.class)
                .updateDisplay();
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_HIDE_SSID) {
            use(WifiTetherHiddenSSIDPreferenceController.class)
                    .refreshHiddenSsidState();
        }
        /*
        use(WifiTetherSecurityPreferenceController.class)
                .updateDisplay();
        use(WifiTetherPasswordPreferenceController.class)
                .updateDisplay();
        use(WifiTetherApBandPreferenceController.class)
                .updateDisplay();
        */
    }

    private void updateModePref(boolean mode) {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (mode) {
            if (mBlockedStationsCategory != null) preferenceScreen.removePreference(mBlockedStationsCategory);
        } else {
            preferenceScreen.addPreference(mBlockedStationsCategory);
        }
    }

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    UserManager mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
                    if (mUserManager.isGuestUser()) {
                        return null;
                    }
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.wifi_tether_settings;
                    return Arrays.asList(sir);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    final List<String> keys = super.getNonIndexableKeys(context);

                    if (!TetherUtil.isTetherAvailable(context)) {
                        keys.add(KEY_WIFI_TETHER_NETWORK_NAME);
                        keys.add(KEY_WIFI_TETHER_NETWORK_PASSWORD);
                        keys.add(KEY_WIFI_TETHER_AUTO_OFF);
                        keys.add(KEY_WIFI_TETHER_NETWORK_AP_BAND);
                    }

                    // Remove duplicate
                    keys.add(KEY_WIFI_TETHER_SCREEN);
                    return keys;
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context, null /* listener */);
                }
            };

    @VisibleForTesting
    class TetherChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "receiving broadcast action " + action);
            if (action.equals(WIFI_AP_CONNECTION_CHANGED_ACTION)) {
                addConnectedStations();
            } else if (action.equals(WIFI_AP_CLIENT_DETAILINFO_AVAILABLE_ACTION)) {
                updateStations();
            }
        }
    }
}


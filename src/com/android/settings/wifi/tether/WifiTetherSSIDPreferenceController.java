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

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiFeaturesUtils;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration;
import android.util.Log;
import android.view.View;

import androidx.annotation.VisibleForTesting;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.widget.ValidatedEditTextPreference;
import com.android.settings.wifi.dpp.WifiDppUtils;
import com.android.settings.wifi.WifiUtils;

import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;

public class WifiTetherSSIDPreferenceController extends WifiTetherBasePreferenceController
        implements ValidatedEditTextPreference.Validator {

    private static final String TAG = "WifiTetherSsidPref";
    private static final String PREF_KEY = "wifi_tether_network_name";
    @VisibleForTesting
    static final String DEFAULT_SSID = "AndroidAP";

    private String mSSID;
    private WifiDeviceNameTextValidator mWifiDeviceNameTextValidator;

    private final MetricsFeatureProvider mMetricsFeatureProvider;

    public WifiTetherSSIDPreferenceController(Context context,
            OnTetherConfigUpdateListener listener) {
        super(context, listener);

        mWifiDeviceNameTextValidator = new WifiDeviceNameTextValidator();
        mMetricsFeatureProvider = FeatureFactory.getFactory(context).getMetricsFeatureProvider();
    }

    @Override
    public String getPreferenceKey() {
        return PREF_KEY;
    }

    @Override
    public void updateDisplay() {
        WifiConfiguration config = null;
        boolean wifiApEnabled = false;
        if (WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_FEATURES) {
            config = mWifiConfig;
            wifiApEnabled = mHotspotState == WifiManager.WIFI_AP_STATE_ENABLED;
        } else {
            config = mWifiManager.getWifiApConfiguration();
            wifiApEnabled = mWifiManager.isWifiApEnabled();
        }
        if (config != null) {
            mSSID = config.SSID;
            Log.d(TAG, "Updating SSID in Preference, " + mSSID);
        } else {
            mSSID = DEFAULT_SSID;
            Log.d(TAG, "Updating to default SSID in Preference, " + mSSID);
        }
        ((ValidatedEditTextPreference) mPreference).setValidator(this);
        ((ValidatedEditTextPreference) mPreference).setIsSSID(true);

        if (wifiApEnabled && config != null) {
            final Intent intent = WifiDppUtils.getHotspotConfiguratorIntentOrNull(mContext,
                    mWifiManager, config);

            if (intent == null) {
                Log.e(TAG, "Invalid security to share hotspot");
                ((WifiTetherSsidPreference) mPreference).setButtonVisible(false);
            } else {
                ((WifiTetherSsidPreference) mPreference).setButtonOnClickListener(
                        view -> shareHotspotNetwork(intent));
                ((WifiTetherSsidPreference) mPreference).setButtonVisible(true);
            }
        } else {
            ((WifiTetherSsidPreference) mPreference).setButtonVisible(false);
        }
        updateSsidDisplay((EditTextPreference) mPreference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        mSSID = (String) newValue;
        updateSsidDisplay((EditTextPreference) preference);
        WifiConfiguration config = mWifiManager.getWifiApConfiguration();
        if (!mSSID.equals(config.SSID)) {
            mListener.onTetherConfigUpdated();
        }
        return true;
    }

    @Override
    public boolean isTextValid(String value) {
        return mWifiDeviceNameTextValidator.isTextValid(value);
    }

    public String getSSID() {
        return mSSID;
    }

    private void updateSsidDisplay(EditTextPreference preference) {
        preference.setText(mSSID);
        preference.setSummary(mSSID);
    }

    private void shareHotspotNetwork(Intent intent) {
        WifiDppUtils.showLockScreen(mContext, () -> {
            mMetricsFeatureProvider.action(SettingsEnums.PAGE_UNKNOWN,
                    SettingsEnums.ACTION_SETTINGS_SHARE_WIFI_HOTSPOT_QR_CODE,
                    SettingsEnums.SETTINGS_WIFI_DPP_CONFIGURATOR,
                    /* key */ null,
                    /* value */ Integer.MIN_VALUE);

            mContext.startActivity(intent);
        });
    }

    @VisibleForTesting
    boolean isQrCodeButtonAvailable() {
        return ((WifiTetherSsidPreference) mPreference).isQrCodeButtonAvailable();
    }
}

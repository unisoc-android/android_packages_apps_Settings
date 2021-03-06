/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.wifi.dpp;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.settings.tests.unit.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WifiDppConfiguratorActivityTest {
    private static final String TAG = "Settings_ut";
    // Valid Wi-Fi DPP QR code & it's parameters
    private static final String VALID_WIFI_DPP_QR_CODE = "DPP:I:SN=4774LH2b4044;M:010203040506;K:"
            + "MDkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDIgADURzxmttZoIRIPWGoQMV00XHWCAQIhXruVWOz0NjlkIA=;;";

    @Rule
    public final ActivityTestRule<WifiDppConfiguratorActivity> mActivityRule =
            new ActivityTestRule<>(WifiDppConfiguratorActivity.class);

    private UiDevice mDevice;
    private boolean mTestFlag;

    @Before
    public void setUp() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mTestFlag = context.getResources().getBoolean(R.bool.config_test_wifi);
    }

    @Test
    public void launchActivity_qrCodeScanner_shouldNotAutoFinish() {
        if (mTestFlag) {
            Intent intent = new Intent(WifiDppConfiguratorActivity.ACTION_CONFIGURATOR_QR_CODE_SCANNER);
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_SECURITY, "WEP");
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_SSID, "GoogleGuest");
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_PRE_SHARED_KEY, "password");

            mActivityRule.launchActivity(intent);

            assertThat(mActivityRule.getActivity().isFinishing()).isEqualTo(false);
        } else {
            Log.d(TAG, "launchActivity_qrCodeScanner_shouldNotAutoFinish not test");
        }
    }

    @Test
    public void launchActivity_qrCodeGenerator_shouldNotAutoFinish() {
        if (mTestFlag) {
            Intent intent = new Intent(
                    WifiDppConfiguratorActivity.ACTION_CONFIGURATOR_QR_CODE_GENERATOR);
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_SECURITY, "WEP");
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_SSID, "GoogleGuest");
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_PRE_SHARED_KEY, "password");

            mActivityRule.launchActivity(intent);

            assertThat(mActivityRule.getActivity().isFinishing()).isEqualTo(false);
        } else {
            Log.d(TAG, "launchActivity_qrCodeGenerator_shouldNotAutoFinish not test");
        }
    }

    @Test
    public void launchActivity_chooseSavedWifiNetwork_shouldNotAutoFinish() {
        if (mTestFlag) {
            final Intent intent = new Intent(Settings.ACTION_PROCESS_WIFI_EASY_CONNECT_URI);
            intent.setData(Uri.parse(VALID_WIFI_DPP_QR_CODE));

            mActivityRule.launchActivity(intent);

            assertThat(mActivityRule.getActivity().isFinishing()).isEqualTo(false);
        } else {
            Log.d(TAG, "launchActivity_chooseSavedWifiNetwork_shouldNotAutoFinish not test");
        }
    }

    @Test
    public void testActivity_shouldImplementsWifiNetworkConfigRetriever() {
        if (mTestFlag) {
            WifiDppConfiguratorActivity activity = mActivityRule.getActivity();

            assertThat(activity instanceof WifiNetworkConfig.Retriever).isEqualTo(true);
        } else {
            Log.d(TAG, "testActivity_shouldImplementsWifiNetworkConfigRetriever not test");
        }
    }

    @Test
    public void testActivity_shouldImplementsQrCodeGeneratorFragmentCallback() {
        WifiDppConfiguratorActivity activity = mActivityRule.getActivity();

        assertThat(activity instanceof WifiDppQrCodeGeneratorFragment
                .OnQrCodeGeneratorFragmentAddButtonClickedListener).isEqualTo(true);
    }

    @Test
    public void testActivity_shouldImplementsOnScanWifiDppSuccessCallback() {
        WifiDppConfiguratorActivity activity = mActivityRule.getActivity();

        assertThat(activity instanceof WifiDppQrCodeScannerFragment
                .OnScanWifiDppSuccessListener).isEqualTo(true);
    }

    @Test
    public void testActivity_shouldImplementsOnClickChooseDifferentNetworkCallback() {
        WifiDppConfiguratorActivity activity = mActivityRule.getActivity();

        assertThat(activity instanceof WifiDppAddDeviceFragment
                .OnClickChooseDifferentNetworkListener).isEqualTo(true);
    }

    @Test
    public void rotateScreen_shouldGetCorrectWifiDppQrCode() {
        if (mTestFlag) {
            WifiQrCode wifiQrCode = new WifiQrCode(VALID_WIFI_DPP_QR_CODE);
            Intent intent = new Intent(WifiDppConfiguratorActivity.ACTION_CONFIGURATOR_QR_CODE_SCANNER);
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_SECURITY, "WEP");
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_SSID, "GoogleGuest");
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_PRE_SHARED_KEY, "password");

            // setWifiDppQrCode and check if getWifiDppQrCode correctly after rotation
            mActivityRule.launchActivity(intent);
            mActivityRule.getActivity().setWifiDppQrCode(wifiQrCode);

            try {
                mDevice.setOrientationLeft();
                mDevice.setOrientationNatural();
                mDevice.setOrientationRight();
                mDevice.setOrientationNatural();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }

            WifiQrCode restoredWifiDppQrCode = mActivityRule.getActivity().getWifiDppQrCode();
            assertThat(restoredWifiDppQrCode).isNotNull();
            assertThat(restoredWifiDppQrCode.getQrCode()).isEqualTo(VALID_WIFI_DPP_QR_CODE);
        } else {
            Log.d(TAG, "rotateScreen_shouldGetCorrectWifiDppQrCode not test");
        }

    }

    @Test
    public void rotateScreen_shouldGetCorrectWifiNetworkConfig() {
        final WifiNetworkConfig wifiNetworkConfig = new WifiNetworkConfig("WPA", "WifiSsid",
                "password", /* hiddenSsid */ false, /* networkId */ 0, /* isHotspot */ true);
        final Intent intent = new Intent(Settings.ACTION_PROCESS_WIFI_EASY_CONNECT_URI);
        intent.setData(Uri.parse(VALID_WIFI_DPP_QR_CODE));

        // setWifiNetworkConfig and check if getWifiNetworkConfig correctly after rotation
        mActivityRule.launchActivity(intent);
        mActivityRule.getActivity().setWifiNetworkConfig(wifiNetworkConfig);

        try {
            mDevice.setOrientationLeft();
            mDevice.setOrientationNatural();
            mDevice.setOrientationRight();
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        WifiNetworkConfig restoredWifiNetworkConfig =
                mActivityRule.getActivity().getWifiNetworkConfig();

        assertThat(restoredWifiNetworkConfig).isNotNull();
        assertThat(restoredWifiNetworkConfig.getSecurity()).isEqualTo("WPA");
        assertThat(restoredWifiNetworkConfig.getSsid()).isEqualTo("WifiSsid");
        assertThat(restoredWifiNetworkConfig.getPreSharedKey()).isEqualTo("password");
        assertThat(restoredWifiNetworkConfig.getHiddenSsid()).isFalse();
        assertThat(restoredWifiNetworkConfig.getNetworkId()).isEqualTo(0);
        assertThat(restoredWifiNetworkConfig.isHotspot()).isTrue();
    }

    @Test
    public void launchScanner_onNavigateUp_shouldFinish() {
        if (mTestFlag) {
            Intent intent = new Intent(WifiDppConfiguratorActivity.ACTION_CONFIGURATOR_QR_CODE_SCANNER);
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_SECURITY, "WEP");
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_SSID, "GoogleGuest");
            intent.putExtra(WifiDppUtils.EXTRA_WIFI_PRE_SHARED_KEY, "password");
            final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

            mActivityRule.launchActivity(intent);

            instrumentation.runOnMainSync(() -> {
                mActivityRule.getActivity().onNavigateUp();

                assertThat(mActivityRule.getActivity().isFinishing()).isEqualTo(true);
            });
        } else {
            Log.d(TAG, "launchScanner_onNavigateUp_shouldFinish not test");
        }
    }

    @Test
    public void launchGenerator_onNavigateUp_shouldFinish() {
        Intent intent = new Intent(
                WifiDppConfiguratorActivity.ACTION_CONFIGURATOR_QR_CODE_GENERATOR);
        intent.putExtra(WifiDppUtils.EXTRA_WIFI_SECURITY, "WEP");
        intent.putExtra(WifiDppUtils.EXTRA_WIFI_SSID, "GoogleGuest");
        intent.putExtra(WifiDppUtils.EXTRA_WIFI_PRE_SHARED_KEY, "password");
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        mActivityRule.launchActivity(intent);

        instrumentation.runOnMainSync(() -> {
            mActivityRule.getActivity().onNavigateUp();

            assertThat(mActivityRule.getActivity().isFinishing()).isEqualTo(true);
        });
    }

}

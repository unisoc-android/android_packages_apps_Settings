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
package com.android.settings.connecteddevice.usb;

import static android.hardware.usb.UsbPortStatus.DATA_ROLE_DEVICE;
import static android.hardware.usb.UsbPortStatus.DATA_ROLE_HOST;
import static android.hardware.usb.UsbPortStatus.POWER_ROLE_NONE;
import static android.hardware.usb.UsbPortStatus.POWER_ROLE_SINK;
import static android.hardware.usb.UsbPortStatus.POWER_ROLE_SOURCE;

import android.content.Context;
import android.hardware.usb.UsbManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.connecteddevice.DevicePreferenceCallback;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.sprd.settings.SprdUsbSettingsFragment;

import android.net.ConnectivityManager;
import android.os.SystemProperties;

/**
 * Controller to maintain connected usb device
 */
public class ConnectedUsbDeviceUpdater {
    private DashboardFragment mFragment;
    private UsbBackend mUsbBackend;
    private DevicePreferenceCallback mDevicePreferenceCallback;
    private static Context mContext;
    @VisibleForTesting
    Preference mUsbPreference;
    @VisibleForTesting
    UsbConnectionBroadcastReceiver mUsbReceiver;
    private boolean orignal_support = SystemProperties.getBoolean("ro.usb.orignal.design", false);

    @VisibleForTesting
    UsbConnectionBroadcastReceiver.UsbConnectionListener mUsbConnectionListener =
            (connected, functions, powerRole, dataRole) -> {
                if (connected) {
                    if (mContext == null) mContext = mUsbPreference.getContext();
                    mUsbPreference.setSummary(getSummary(dataRole != DATA_ROLE_HOST
                                    ? functions : UsbManager.FUNCTION_NONE, powerRole));
                    mDevicePreferenceCallback.onDeviceAdded(mUsbPreference);
                } else {
                    mDevicePreferenceCallback.onDeviceRemoved(mUsbPreference);
                }
            };

    public ConnectedUsbDeviceUpdater(Context context, DashboardFragment fragment,
            DevicePreferenceCallback devicePreferenceCallback) {
        this(context, fragment, devicePreferenceCallback, new UsbBackend(context));
    }

    @VisibleForTesting
    ConnectedUsbDeviceUpdater(Context context, DashboardFragment fragment,
            DevicePreferenceCallback devicePreferenceCallback, UsbBackend usbBackend) {
        mFragment = fragment;
        mDevicePreferenceCallback = devicePreferenceCallback;
        mUsbBackend = usbBackend;
        mUsbReceiver = new UsbConnectionBroadcastReceiver(context,
                mUsbConnectionListener, mUsbBackend);
    }

    public void registerCallback() {
        // This method could handle multiple register
        mUsbReceiver.register();
    }

    public void unregisterCallback() {
        mUsbReceiver.unregister();
    }

    public void initUsbPreference(Context context) {
        mUsbPreference = new Preference(context, null /* AttributeSet */);
        mUsbPreference.setTitle(R.string.usb_pref);
        mUsbPreference.setIcon(R.drawable.ic_usb);
        mUsbPreference.setOnPreferenceClickListener((Preference p) -> {
            // New version - uses a separate screen.
            new SubSettingLauncher(mFragment.getContext())
                    .setDestination(orignal_support? UsbDetailsFragment.class.getName() : SprdUsbSettingsFragment.class.getName())
                    .setTitleRes(R.string.device_details_title)
                    .setSourceMetricsCategory(mFragment.getMetricsCategory())
                    .launch();
            return true;
        });

        forceUpdate();
    }

    private void forceUpdate() {
        // Register so we can get the connection state from sticky intent.
        //TODO(b/70336520): Use an API to get data instead of sticky intent
        mUsbReceiver.register();

    }

    private static int getTetherOrPcNetTether() {
        if (mContext == null) return R.string.usb_summary_tether;
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return (connectivityManager.getPCNetTether()) ? R.string.usb_pc_internet_share
                : R.string.usb_summary_tether;
    }

    public static int getSummary(long functions, int power) {
        switch (power) {
            case POWER_ROLE_NONE:
            case POWER_ROLE_SINK:
                if (functions == UsbManager.FUNCTION_MTP) {
                    return R.string.usb_summary_file_transfers;
                } else if (functions == UsbManager.FUNCTION_RNDIS) {
                    return getTetherOrPcNetTether();
                } else if (functions == UsbManager.FUNCTION_PTP) {
                    return R.string.usb_summary_photo_transfers;
                } else if (functions == UsbManager.FUNCTION_MIDI) {
                    return R.string.usb_summary_MIDI;
                } else if (functions == UsbManager.FUNCTION_CDROM) {
                    return R.string.usb_virtual_drive_summary;
                } else if (functions == UsbManager.FUNCTION_MASS_STORAGE) {
                    return R.string.usb_storage_summary;
                } else {
                    return R.string.usb_summary_charging_only;
                }
            case POWER_ROLE_SOURCE:
                if (functions == UsbManager.FUNCTION_MTP) {
                    return R.string.usb_summary_file_transfers_power;
                } else if (functions == UsbManager.FUNCTION_RNDIS) {
                    return R.string.usb_summary_tether_power;
                } else if (functions == UsbManager.FUNCTION_PTP) {
                    return R.string.usb_summary_photo_transfers_power;
                } else if (functions == UsbManager.FUNCTION_MIDI) {
                    return R.string.usb_summary_MIDI_power;
                } else if (functions == UsbManager.FUNCTION_CDROM) {
                    return R.string.usb_virtual_drive_summary;
                } else if (functions == UsbManager.FUNCTION_MASS_STORAGE) {
                    return R.string.usb_storage_summary;
                } else {
                    return R.string.usb_summary_power_only;
                }
            default:
                return R.string.usb_summary_charging_only;
        }
    }
}

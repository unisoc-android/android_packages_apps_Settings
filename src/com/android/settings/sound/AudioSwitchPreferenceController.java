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

package com.android.settings.sound;

import static android.media.AudioManager.STREAM_DEVICES_CHANGED_ACTION;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.os.Handler;
import android.os.Looper;
import android.util.FeatureFlagUtils;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.bluetooth.Utils;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.FeatureFlags;
import com.android.settingslib.bluetooth.A2dpProfile;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.HeadsetProfile;
import com.android.settingslib.bluetooth.HearingAidProfile;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Abstract class for audio switcher controller to notify subclass
 * updating the current status of switcher entry. Subclasses must overwrite
 */
public abstract class AudioSwitchPreferenceController extends BasePreferenceController
        implements BluetoothCallback, LifecycleObserver, OnStart, OnStop {

    private static final String TAG = "AudioSwitchPrefCtrl";

    protected final List<BluetoothDevice> mConnectedDevices;
    protected final AudioManager mAudioManager;
    protected final MediaRouter mMediaRouter;
    protected int mSelectedIndex;
    protected Preference mPreference;
    protected LocalBluetoothProfileManager mProfileManager;
    protected AudioSwitchCallback mAudioSwitchPreferenceCallback;

    private final AudioManagerAudioDeviceCallback mAudioManagerAudioDeviceCallback;
    private final WiredHeadsetBroadcastReceiver mReceiver;
    private final Handler mHandler;
    private LocalBluetoothManager mLocalBluetoothManager;

    public interface AudioSwitchCallback {
        void onPreferenceDataChanged(ListPreference preference);
    }

    public AudioSwitchPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mMediaRouter = (MediaRouter) context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mAudioManagerAudioDeviceCallback = new AudioManagerAudioDeviceCallback();
        mReceiver = new WiredHeadsetBroadcastReceiver();
        mConnectedDevices = new ArrayList<>();
        final FutureTask<LocalBluetoothManager> localBtManagerFutureTask = new FutureTask<>(
                // Avoid StrictMode ThreadPolicy violation
                () -> Utils.getLocalBtManager(mContext));
        try {
            localBtManagerFutureTask.run();
            mLocalBluetoothManager = localBtManagerFutureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            Log.w(TAG, "Error getting LocalBluetoothManager.", e);
            return;
        }
        if (mLocalBluetoothManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }
        mProfileManager = mLocalBluetoothManager.getProfileManager();
    }

    /**
     * Make this method as final, ensure that subclass will checking
     * the feature flag and they could mistakenly break it via overriding.
     */
    @Override
    public final int getAvailabilityStatus() {
        if (!Utils.isBluetoothSupported(mContext)) {
            return CONDITIONALLY_UNAVAILABLE;
        }
        return FeatureFlagUtils.isEnabled(mContext, FeatureFlags.AUDIO_SWITCHER_SETTINGS) &&
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
                ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(mPreferenceKey);
        mPreference.setVisible(false);
    }

    @Override
    public void onStart() {
        if (mLocalBluetoothManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }
        mLocalBluetoothManager.setForegroundActivity(mContext);
        register();
    }

    @Override
    public void onStop() {
        if (mLocalBluetoothManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }
        mLocalBluetoothManager.setForegroundActivity(null);
        unregister();
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        // To handle the case that Bluetooth on and no connected devices
        updateState(mPreference);
    }

    @Override
    public void onActiveDeviceChanged(CachedBluetoothDevice activeDevice, int bluetoothProfile) {
        updateState(mPreference);
    }

    @Override
    public void onAudioModeChanged() {
        updateState(mPreference);
    }

    @Override
    public void onProfileConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state,
            int bluetoothProfile) {
        updateState(mPreference);
    }

    /**
     * Indicates a change in the bond state of a remote
     * device. For example, if a device is bonded (paired).
     */
    @Override
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        updateState(mPreference);
    }

    public void setCallback(AudioSwitchCallback callback) {
        mAudioSwitchPreferenceCallback = callback;
    }

    protected boolean isStreamFromOutputDevice(int streamType, int device) {
        return (device & mAudioManager.getDevicesForStream(streamType)) != 0;
    }

    /**
     * get hands free profile(HFP) connected device
     */
    protected List<BluetoothDevice> getConnectedHfpDevices() {
        final List<BluetoothDevice> connectedDevices = new ArrayList<>();
        final HeadsetProfile hfpProfile = mProfileManager.getHeadsetProfile();
        if (hfpProfile == null) {
            return connectedDevices;
        }
        final List<BluetoothDevice> devices = hfpProfile.getConnectedDevices();
        for (BluetoothDevice device : devices) {
            if (device.isConnected()) {
                connectedDevices.add(device);
            }
        }
        return connectedDevices;
    }

    /**
     * get A2dp devices on all states
     * (STATE_DISCONNECTED, STATE_CONNECTING, STATE_CONNECTED,  STATE_DISCONNECTING)
     */
    protected List<BluetoothDevice> getConnectedA2dpDevices() {
        final A2dpProfile a2dpProfile = mProfileManager.getA2dpProfile();
        if (a2dpProfile == null) {
            return new ArrayList<>();
        }
        return a2dpProfile.getConnectedDevices();
    }

    /**
     * get hearing aid profile connected device, exclude other devices with same hiSyncId.
     */
    protected List<BluetoothDevice> getConnectedHearingAidDevices() {
        final List<BluetoothDevice> connectedDevices = new ArrayList<>();
        final HearingAidProfile hapProfile = mProfileManager.getHearingAidProfile();
        if (hapProfile == null) {
            return connectedDevices;
        }
        final List<Long> devicesHiSyncIds = new ArrayList<>();
        final List<BluetoothDevice> devices = hapProfile.getConnectedDevices();
        for (BluetoothDevice device : devices) {
            final long hiSyncId = hapProfile.getHiSyncId(device);
            // device with same hiSyncId should not be shown in the UI.
            // So do not add it into connectedDevices.
            if (!devicesHiSyncIds.contains(hiSyncId) && device.isConnected()) {
                devicesHiSyncIds.add(hiSyncId);
                connectedDevices.add(device);
            }
        }
        return connectedDevices;
    }

    /**
     * Find active hearing aid device
     */
    protected BluetoothDevice findActiveHearingAidDevice() {
        final HearingAidProfile hearingAidProfile = mProfileManager.getHearingAidProfile();

        if (hearingAidProfile != null) {
            // The first element is the left active device; the second element is
            // the right active device. And they will have same hiSyncId. If either
            // or both side is not active, it will be null on that position.
            List<BluetoothDevice> activeDevices = hearingAidProfile.getActiveDevices();
            for (BluetoothDevice btDevice : activeDevices) {
                if (btDevice != null && mConnectedDevices.contains(btDevice)) {
                    // also need to check mConnectedDevices, because one of
                    // the device(same hiSyncId) might not be shown in the UI.
                    return btDevice;
                }
            }
        }
        return null;
    }

    /**
     * Find the active device from the corresponding profile.
     *
     * @return the active device. Return null if the
     * corresponding profile don't have active device.
     */
    public abstract BluetoothDevice findActiveDevice();

    private void register() {
        mLocalBluetoothManager.getEventManager().registerCallback(this);
        mAudioManager.registerAudioDeviceCallback(mAudioManagerAudioDeviceCallback, mHandler);

        // Register for misc other intent broadcasts.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        intentFilter.addAction(STREAM_DEVICES_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    private void unregister() {
        mLocalBluetoothManager.getEventManager().unregisterCallback(this);
        mAudioManager.unregisterAudioDeviceCallback(mAudioManagerAudioDeviceCallback);
        mContext.unregisterReceiver(mReceiver);
    }

    /** Notifications of audio device connection and disconnection events. */
    private class AudioManagerAudioDeviceCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            updateState(mPreference);
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) {
            updateState(mPreference);
        }
    }

    /** Receiver for wired headset plugged and unplugged events. */
    private class WiredHeadsetBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (AudioManager.ACTION_HEADSET_PLUG.equals(action) ||
                    AudioManager.STREAM_DEVICES_CHANGED_ACTION.equals(action)) {
                updateState(mPreference);
            }
        }
    }
}

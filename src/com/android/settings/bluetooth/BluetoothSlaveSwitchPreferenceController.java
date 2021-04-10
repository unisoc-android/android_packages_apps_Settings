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
package com.android.settings.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.widget.SwitchWidgetController;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

public class BluetoothSlaveSwitchPreferenceController
        implements LifecycleObserver, OnStart, OnStop,
        SwitchWidgetController.OnSwitchChangeListener {

    private static final String TAG = "BluetoothSlavePreCtl";
    private LocalBluetoothAdapter mBluetoothAdapter;
    private LocalBluetoothManager mBluetoothManager;
    private SwitchWidgetController mSwitch;
    private IntentFilter mIntentFilter;
    private Context mContext;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)  {
                Log.d(TAG, "onReceive action is null. return");
                return;
            }

            if (BluetoothAdapter.ACTION_BLUETOOH_MODE_CHANGED.equals(action)) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_BLUETOOTH_MODE,
                            BluetoothAdapter.BT_MODE_MASTER);
                Log.d(TAG, "onReceive mode = " + mode);
                mSwitch.setChecked((mode == BluetoothAdapter.BT_MODE_MASTER) ? false : true);
                mSwitch.setEnabled(true);
            }
        }
    };

    public BluetoothSlaveSwitchPreferenceController(Context context,
            SwitchWidgetController switchController) {
        mContext = context;
        mBluetoothManager = Utils.getLocalBtManager(mContext);
        if (mBluetoothManager != null) {
            mBluetoothAdapter = mBluetoothManager.getBluetoothAdapter();
        }
        mSwitch = switchController;
        mSwitch.setListener(this);
        mSwitch.setupView();
        mIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_BLUETOOH_MODE_CHANGED);
    }

    @Override
    public void onStart() {
        boolean isChecked = (mBluetoothAdapter.getBluetoothMode() == BluetoothAdapter.BT_MODE_MASTER) ? false : true;
        mSwitch.setChecked(isChecked);
        mSwitch.startListening();
        mContext.registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    public void onStop() {
        mSwitch.stopListening();
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onSwitchToggled(boolean isChecked) {
        boolean isOnOff = (mBluetoothAdapter.getBluetoothMode() == BluetoothAdapter.BT_MODE_MASTER) ? false : true;
        Log.d(TAG, "onSwitchToggled.isOnOff = " + isOnOff + ", isChecked = " + isChecked);
        if (isChecked == isOnOff) {
            return true;
        }
        boolean isSuccess = mBluetoothAdapter.setBluetoothMode(isChecked
                    ? BluetoothAdapter.BT_MODE_SLAVE
                    : BluetoothAdapter.BT_MODE_MASTER);
        Log.d(TAG, "onSwitchToggled.isSuccess = " + isSuccess);
        if (isSuccess) {
            mSwitch.setEnabled(false);
        }
        return isSuccess;
    }
}

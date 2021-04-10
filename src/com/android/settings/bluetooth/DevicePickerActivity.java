/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import androidx.fragment.app.FragmentActivity;
import com.android.settings.R;
import com.android.settings.search.SearchFeatureProvider;
import com.android.settings.overlay.FeatureFactory;

/**
 * Activity for Bluetooth device picker dialog. The device picker logic
 * is implemented in the {@link BluetoothPairingDetail} fragment.
 */
public final class DevicePickerActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_device_picker);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                final SearchFeatureProvider sfp = FeatureFactory.getFactory(getApplicationContext())
                        .getSearchFeatureProvider();
                final Intent intent = sfp.buildSearchIntent(this, SettingsEnums.BLUETOOTH_DEVICE_PICKER);
                startActivityForResult(intent, 0 /* requestCode */);
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }
}

package com.sprd.settings.wifi;

import android.content.Context;
import android.net.wifi.WifiFeaturesUtils;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;

/**
 * AutoConnectPreferenceController controls whether wifi should auto connect.
 */
public class AutoConnectPreferenceController extends TogglePreferenceController {

    private WifiFeaturesUtils mWifiFeaturesUtils;

    public AutoConnectPreferenceController(Context context, String key) {
        super(context, key);
        mWifiFeaturesUtils = WifiFeaturesUtils.getInstance(context);
    }

    @Override
    public int getAvailabilityStatus() {
        return mWifiFeaturesUtils.isSupportAppConnectPolicy() ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isChecked() {
        return mWifiFeaturesUtils.isAutoConnect();
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        mWifiFeaturesUtils.setAutoConnect(isChecked);
        return true;
    }

}

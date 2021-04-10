package com.sprd.settings.wifi;

import android.content.Context;
import android.net.wifi.WifiFeaturesUtils;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;

/**
 * {@link PreferenceController} that controls whether we should notify user when special AP (such as:CMCC) is connected.
 */
public class WifiConnectedWarningPreferenceController extends TogglePreferenceController {

    private WifiFeaturesUtils mWifiFeaturesUtils;

    public WifiConnectedWarningPreferenceController(Context context, String key) {
        super(context, key);
        mWifiFeaturesUtils = WifiFeaturesUtils.getInstance(context);
    }

    @Override
    public int getAvailabilityStatus() {
        return mWifiFeaturesUtils.isSupportAppConnectPolicy() ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isChecked() {
        return isShowWifiConnectedWarning();
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return setWifiConnectedWarningFlag(isChecked);
    }

    private boolean isShowWifiConnectedWarning() {
        return Settings.Global.getInt(mContext.getContentResolver(),
            WifiFeaturesUtils.WIFI_CONNECTED_WARNING_FLAG, 1) == 1;
    }

    private boolean setWifiConnectedWarningFlag(boolean isChecked) {
        return Settings.Global.putInt(mContext.getContentResolver(),
            WifiFeaturesUtils.WIFI_CONNECTED_WARNING_FLAG, isChecked ? 1 : 0);
    }

}

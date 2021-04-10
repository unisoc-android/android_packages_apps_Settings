
package com.android.settings.network;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.provider.Settings;
import android.provider.SettingsEx;
/**
 * {@link ContentObserver} to listen to update of dds change
 */
public class DefaultDataSubIdContentObserver extends ContentObserver {
    private OnDefaultDataSubIdChangedListener mListener;

    public DefaultDataSubIdContentObserver(Handler handler) {
        super(handler);
    }

    public static Uri getDefaultDataSubIdUri() {
        Uri uri = Settings.Global.getUriFor(Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION);
        return uri;
    }

    public void setOnDefaultDataSubIdChangedListener(OnDefaultDataSubIdChangedListener lsn) {
        mListener = lsn;
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        if (mListener != null) {
            mListener.onDefaultDataSubIdChanged();
        }
    }

    public void register(Context context) {
        final Uri defaultDataSubIdUri = getDefaultDataSubIdUri();
        context.getContentResolver().registerContentObserver(defaultDataSubIdUri, false, this);
    }

    public void unRegister(Context context) {
        context.getContentResolver().unregisterContentObserver(this);
    }

    public interface OnDefaultDataSubIdChangedListener {
        void onDefaultDataSubIdChanged();
    }
}

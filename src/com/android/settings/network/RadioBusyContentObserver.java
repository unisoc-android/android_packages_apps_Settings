package com.android.settings.network;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.provider.SettingsEx;

public class RadioBusyContentObserver extends ContentObserver {
    private OnRadioBusyChangedListener mListener;

    public RadioBusyContentObserver(Handler handler) {
        super(handler);
    }

    public static Uri getObservableUri() {
        Uri uri = Settings.Global.getUriFor(SettingsEx.GlobalEx.RADIO_BUSY);
        return uri;
    }

    public void setOnRadioBusyChangedListener(OnRadioBusyChangedListener lsn) {
        mListener = lsn;
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        if (mListener != null) {
            mListener.onRadioBusyChanged();
        }
    }

    public void register(Context context) {
        final Uri uri = getObservableUri();
        context.getContentResolver().registerContentObserver(uri, false, this);

    }

    public void unRegister(Context context) {
        context.getContentResolver().unregisterContentObserver(this);
    }

    /**
     * Listener for update of mobile data(ON vs OFF)
     */
    public interface OnRadioBusyChangedListener {
        void onRadioBusyChanged();
    }

}

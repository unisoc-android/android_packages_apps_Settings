
package com.android.settings.network.telephony;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.R;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

/**
 * Preference controller for "Moblie Data Always Online"
 */
public class MobileDataAlwaysOnlinePreferenceController extends TelephonyTogglePreferenceController implements
        LifecycleObserver, OnStart, OnStop, MobileDataAlwaysOnlineDialogFragment.MobileDataAlwaysOnlineDialogListener {

    private static final String DIALOG_TAG = "MobileDataAlwaysOnlineDialog";
    private static final String LOG_TAG = "MobileDataAlwaysOnlinePreferenceController";

    private SwitchPreference mSwitchPreference;
    private MobileDataAlwaysOnlineContentObserver mDataAlwaysOnlineContentObserver;
    boolean mNeedDialog;
    @VisibleForTesting
    FragmentManager mFragmentManager;

    public MobileDataAlwaysOnlinePreferenceController(Context context, String key) {
        super(context, key);
        mDataAlwaysOnlineContentObserver = new MobileDataAlwaysOnlineContentObserver(
                new Handler(Looper.getMainLooper()));
    }

    @Override
    public void onStart() {
        mDataAlwaysOnlineContentObserver.register(mContext, mSubId);
    }

    @Override
    public void onStop() {
        mDataAlwaysOnlineContentObserver.unRegister(mContext);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mSwitchPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        boolean isMobileDataAlwaysOnlineShown =
                 mContext.getResources().getBoolean(R.bool.enable_mobile_data_always_online);

        return isMobileDataAlwaysOnlineShown? AVAILABLE : DISABLED_FOR_USER;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            if (mNeedDialog) {
                showDialog();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        mNeedDialog = !isChecked;

        if (mNeedDialog) {
            return false;
        }
        setMobileDataAlwaysOnline(mSubId, true);
        return true;
    }

    @Override
    public boolean isChecked() {
        return isMobileDataAlwaysOnline(mSubId);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setSummary(R.string.mobile_data_always_online_summary);
    }

    public void init(FragmentManager fragmentManager, int subId) {
        mFragmentManager = fragmentManager;
        mSubId = subId;
    }

    private void showDialog() {
        mSwitchPreference.setEnabled(false);
        final MobileDataAlwaysOnlineDialogFragment dialogFragment = MobileDataAlwaysOnlineDialogFragment.newInstance(mSubId);
        dialogFragment.setController(this);
        dialogFragment.show(mFragmentManager, DIALOG_TAG);
    }

    public void onDialogDismiss(InstrumentedDialogFragment dialog) {
        Log.d(LOG_TAG, "onDialogDismiss");
        mSwitchPreference.setEnabled(true);
    }

    private boolean isMobileDataAlwaysOnline(int subId) {
        int isMobileDataAlwaysOnline = Settings.Global.getInt(mContext.getContentResolver(),
                SettingsEx.GlobalEx.MOBILE_DATA_ALWAYS_ONLINE + subId,1);
        return 1 == isMobileDataAlwaysOnline;
    }

    private void setMobileDataAlwaysOnline(int subId, boolean onOff) {
        Settings.Global.putInt(mContext.getContentResolver(),
                SettingsEx.GlobalEx.MOBILE_DATA_ALWAYS_ONLINE + subId,onOff ? 1 : 0);
    }

    /**
     * Listener that listens mobile data always online change
     */
    public class MobileDataAlwaysOnlineContentObserver extends ContentObserver {

        public MobileDataAlwaysOnlineContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateState(mSwitchPreference);
        }

        public void register(Context context, int subId) {
            Uri uri = Settings.Global.getUriFor(SettingsEx.GlobalEx.MOBILE_DATA_ALWAYS_ONLINE + subId);
            context.getContentResolver().registerContentObserver(uri, false, this);
        }

        public void unRegister(Context context) {
            context.getContentResolver().unregisterContentObserver(this);
        }
    }
}

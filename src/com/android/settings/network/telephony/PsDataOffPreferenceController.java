
package com.android.settings.network.telephony;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.telephony.PhoneConstants;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.R;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.sprd.telephony.RadioInteractor;

/**
 * Preference controller for "PS Data Off"
 */
public class PsDataOffPreferenceController extends TelephonyTogglePreferenceController implements
        LifecycleObserver, OnStart, OnStop, PsDataOffDialogFragment.PsDataOffDialogListener {

    private static final String DEBUG_TEST = "persist.radio.psdataoff.debug";
    public static final String DIALOG_TAG = "PsDataOffDialog";
    public static final String LOG_TAG = "PsDataOffPreferenceController";
    public static final String PS_DATA_OFF_ENABLED = "persist.radio.ps.data.off";

    private SwitchPreference mSwitchPreference;
    boolean mNeedDialog;
    @VisibleForTesting
    FragmentManager mFragmentManager;

    public PsDataOffPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onStop() {
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mSwitchPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        boolean isPsDataOffShown = SystemProperties.getBoolean(DEBUG_TEST, false) &&
                 mContext.getResources().getBoolean(R.bool.enable_ps_data_off) ;

        return isPsDataOffShown? AVAILABLE : DISABLED_FOR_USER;
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
        mNeedDialog = isChecked;
        int phoneId = SubscriptionManager.getPhoneId(mSubId);

        if (mNeedDialog) {
            return false;
        }
        setPsDataOff(phoneId, false, -1);
        return true;
    }

    @Override
    public boolean isChecked() {
        return isPsDataOff();
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setSummary(R.string.ps_data_off_summary);
    }

    public void init(FragmentManager fragmentManager, int subId) {
        mFragmentManager = fragmentManager;
        mSubId = subId;
    }

    private void showDialog() {
        mSwitchPreference.setEnabled(false);
        final PsDataOffDialogFragment dialogFragment = PsDataOffDialogFragment.newInstance(mSubId);
        dialogFragment.setController(this);
        dialogFragment.show(mFragmentManager, DIALOG_TAG);
    }

    public void onDialogDismiss(InstrumentedDialogFragment dialog) {
        Log.d(LOG_TAG, "onDialogDismiss");
        mSwitchPreference.setEnabled(true);
        updateState(mSwitchPreference);
    }

    private boolean isPsDataOff() {
        int phoneId = SubscriptionManager.getPhoneId(mSubId);
        return !TelephonyManager.getTelephonyProperty(phoneId, PS_DATA_OFF_ENABLED, "-1").equals("-1");
    }

    private void setPsDataOff(int phoneId, boolean onOff, int exceptService) {
        RadioInteractor radioInteractor = new RadioInteractor(mContext);

        if (radioInteractor != null) {
            radioInteractor.setPsDataOff(phoneId, onOff, exceptService);
        }
        if (onOff) {
            TelephonyManager.setTelephonyProperty(phoneId, PS_DATA_OFF_ENABLED, Integer.toString(exceptService));
        } else {
            TelephonyManager.setTelephonyProperty(phoneId, PS_DATA_OFF_ENABLED, "-1");
        }
    }
}

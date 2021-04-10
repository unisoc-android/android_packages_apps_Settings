/** Created by Spreadst */
package com.sprd.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.preference.PreferenceFragment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.util.Log;
/*import android.support.v7.preference.PreferenceScreen;*/

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.storage.VolumeInfo;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.os.storage.IStorageManager;

import android.os.UserHandle;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedLockUtils;
import androidx.preference.PreferenceViewHolder;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import com.android.settings.search.Indexable;
import com.android.settings.search.BaseSearchIndexProvider;
import android.provider.SearchIndexableResource;
import com.android.settingslib.core.AbstractPreferenceController;
import com.google.android.collect.Lists;

public class SprdUsbSettingsFragment extends SettingsPreferenceFragment
        implements RadioButtonPreference.OnClickListener, RadioButtonPreference.OnFileTransferRestrictedListener, Indexable {

    private final String LOG_TAG = "SprdUsbSettings";
    private final static boolean DBG = true;

    private static final String KEY_CHARGE_ONLY = "usb_charge_only";
    private static final String KEY_MTP = "usb_mtp";
    private static final String KEY_PTP = "usb_ptp";
    private static final String KEY_CDROM = "usb_virtual_drive";
    private static final String KEY_MIDI = "usb_midi";
    private static final String KEY_UMS = "usb_storage";

    private RadioButtonPreference mUsbChargeOnly;
    private RadioButtonPreference mMtp;
    private RadioButtonPreference mPtp;
    private RadioButtonPreference mMidi;
    private RadioButtonPreference mCdrom;
    private RadioButtonPreference mUms;
    // bug 1053105,use static value  ensuring consistency
    private static String mCurrentfunction;
    private static boolean mIsUnlocked = false;

    private UsbManager mUsbManager = null;
    private BroadcastReceiver mPowerDisconnectReceiver = null;
    private BroadcastReceiver mUmsReceiver = null;

    private final static boolean SUPPORT_UMS = false;

    private boolean mIsFileTransferRestricted;

    @SuppressWarnings("deprecation")
    public void onActivityCreated(Bundle savedInstanceState) {
        addPreferencesFromResource(R.xml.sprd_usb_settings);
        Intent i = getContext().registerReceiver(null, new IntentFilter(UsbManager.ACTION_USB_STATE));

        // SPRD:Add for bug708610 nullpointerexception the intent is null
        if (i != null) mIsUnlocked = i.getBooleanExtra(UsbManager.USB_DATA_UNLOCKED, false);

        if (DBG) Log.d(LOG_TAG, "on Create");

        mUsbChargeOnly = (RadioButtonPreference) findPreference(KEY_CHARGE_ONLY);
        mMtp = (RadioButtonPreference) findPreference(KEY_MTP);
        mPtp = (RadioButtonPreference) findPreference(KEY_PTP);
        mCdrom = (RadioButtonPreference) findPreference(KEY_CDROM);
        mMidi = (RadioButtonPreference) findPreference(KEY_MIDI);
        mUms = (RadioButtonPreference) findPreference(KEY_UMS);

        mUsbChargeOnly.setOnClickListener(this);
        mMtp.setOnClickListener(this);
        mPtp.setOnClickListener(this);
        mCdrom.setOnClickListener(this);
        mMidi.setOnClickListener(this);
        mUms.setOnClickListener(this);
        mMtp.setOnFileTransferRestrictedListener(this);
        mPtp.setOnFileTransferRestrictedListener(this);
        if (!SUPPORT_UMS) {
            getPreferenceScreen().removePreference(mUms);
        }
        if (SUPPORT_UMS){
            if (!isUmsAvailable()) mUms.setEnabled(false);
        }
        mUsbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);

        mIsFileTransferRestricted = ((UserManager) getActivity().getSystemService(Context.USER_SERVICE))
                .hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER);
        /* SPRD:Add for bug#733341 Disallow USB file transfer cts tests failed @{ */
        mCurrentfunction = getCurrentFunction();
        if (mIsUnlocked && mIsFileTransferRestricted) {
            boolean isMtpOrPtp = UsbManager.USB_FUNCTION_MTP.equals(mCurrentfunction)
                              || UsbManager.USB_FUNCTION_PTP.equals(mCurrentfunction);
            if (isMtpOrPtp) {
                mUsbManager.setCurrentFunctions(UsbManager.FUNCTION_NONE);
                mIsUnlocked = false;
            }
        }
        /* Bug733341 @} */

        mPowerDisconnectReceiver = new PowerDisconnectReceiver();
        getActivity().registerReceiver(mPowerDisconnectReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (DBG) Log.d(LOG_TAG, "on onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DBG) Log.d(LOG_TAG, "on Resume");
        if (SUPPORT_UMS) {
            mUmsReceiver = new UmsReceiver();
            IntentFilter ums_filter = new IntentFilter();
            ums_filter.addAction(Intent.ACTION_MEDIA_SHARED);
            ums_filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            ums_filter.addAction(Intent.ACTION_MEDIA_REMOVED);
            ums_filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
            ums_filter.addAction(Intent.ACTION_MEDIA_NOFS);
            ums_filter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
            ums_filter.addDataScheme("file");
            getActivity().registerReceiver(mUmsReceiver, ums_filter);
        }
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DBG) Log.d(LOG_TAG, "on Pause");
        if (SUPPORT_UMS) getActivity().unregisterReceiver(mUmsReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mPowerDisconnectReceiver);
    }

    @Override
    public void onRadioButtonClicked(RadioButtonPreference preference) {

        Log.d(LOG_TAG ,"onRadioButtonClicked");
        if (Utils.isMonkeyRunning()) {
            return;
        }
        String key = preference.getKey();
        if (mIsFileTransferRestricted && (KEY_MTP.equals(key) || KEY_PTP.equals(key))) {
            EnforcedAdmin mEnforcedAdmin = RestrictedLockUtils.getProfileOrDeviceOwner(getActivity(),
                    UserManager.DISALLOW_USB_FILE_TRANSFER,  UserHandle.of(UserHandle.myUserId()));
            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(
                    getActivity(), mEnforcedAdmin);
            return;
        }
        if (preference == mUsbChargeOnly) {
            mCurrentfunction = UsbManager.USB_FUNCTION_NONE;
            if (mUsbChargeOnly.isChecked()) return;
            mUsbManager.setCurrentFunctions(UsbManager.FUNCTION_NONE);
        } else if (preference == mMtp) {
            mIsUnlocked = true;
            mCurrentfunction = UsbManager.USB_FUNCTION_MTP;
            if (mMtp.isChecked()) return;
            mUsbManager.setCurrentFunctions(UsbManager.FUNCTION_MTP);
        } else if (preference == mPtp) {
            mIsUnlocked = true;
            mCurrentfunction = UsbManager.USB_FUNCTION_PTP;
            if (mPtp.isChecked()) return;
            mUsbManager.setCurrentFunctions(UsbManager.FUNCTION_PTP);
        } else if (preference == mMidi) {
            mCurrentfunction = UsbManager.USB_FUNCTION_MIDI;
            if (mMidi.isChecked()) return;
            mUsbManager.setCurrentFunctions(UsbManager.FUNCTION_MIDI);
        } else if (preference == mCdrom) {
            mCurrentfunction = UsbManager.USB_FUNCTION_CDROM;
            if (mCdrom.isChecked()) return;
            mUsbManager.setCurrentFunctions(UsbManager.FUNCTION_CDROM);
        } else if (preference == mUms) {
            mCurrentfunction = UsbManager.USB_FUNCTION_MASS_STORAGE;
            if (mUms.isChecked()) return;
            mUsbManager.setCurrentFunctions(UsbManager.FUNCTION_MASS_STORAGE);
        }
        //mUsbManager.setUsbDataUnlocked(true);
        getActivity().finish();
        return;
    }

    private void updateUI() {
        //String currentfunction = getCurrentFunction();
        Log.i(LOG_TAG, "mCurrentfunction = " + mCurrentfunction);

        if (DBG) Log.d(LOG_TAG, "mIsUnlocked = " + mIsUnlocked);
        uncheckAllUI();
        if (SUPPORT_UMS){
            if(isUmsAvailable())
                mUms.setEnabled(true);
            else
                mUms.setEnabled(false);
        }
        if (UsbManager.USB_FUNCTION_NONE.equals(mCurrentfunction)) {
            mUsbChargeOnly.setChecked(true);
        } else if (UsbManager.USB_FUNCTION_MTP.equals(mCurrentfunction)) {
            if(mIsUnlocked)
                mMtp.setChecked(true);
            else
                mUsbChargeOnly.setChecked(true);
        } else if (UsbManager.USB_FUNCTION_PTP.equals(mCurrentfunction)) {
            if(mIsUnlocked)
                mPtp.setChecked(true);
            else
                mUsbChargeOnly.setChecked(true);
        } else if (UsbManager.USB_FUNCTION_MIDI.equals(mCurrentfunction)) {
            mMidi.setChecked(true);
        } else if (UsbManager.USB_FUNCTION_CDROM.equals(mCurrentfunction)) {
            mCdrom.setChecked(true);
        } else if (UsbManager.USB_FUNCTION_MASS_STORAGE.equals(mCurrentfunction)) {
            mUms.setChecked(true);
        }
    }

    private void uncheckAllUI() {
        mUsbChargeOnly.setChecked(false);
        mMtp.setChecked(false);
        mPtp.setChecked(false);
        mMidi.setChecked(false);
        mCdrom.setChecked(false);
        mUms.setChecked(false);
    }

    public String getCurrentFunction() {
        String functions = SystemProperties.get("sys.usb.config", "");
        if (DBG) Log.d(LOG_TAG, "getCurrentFunction (sys.usb.config) = " + functions);
        int commaIndex = functions.indexOf(',');
        if (commaIndex > 0) {
            return functions.substring(0, commaIndex);
        } else {
            if (functions.equals(UsbManager.USB_FUNCTION_ADB)) {
                return UsbManager.USB_FUNCTION_NONE;
            } else {
                //return UsbManager.usbFunctionsFromString(functions);
                return functions;
            }
        }

    }

    private class UmsReceiver extends BroadcastReceiver {
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (DBG) Log.d(LOG_TAG, "ums action = " + action);
            updateUI();
        }
    }

    private class PowerDisconnectReceiver extends BroadcastReceiver {
        public void onReceive(Context content, Intent intent) {
            int plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            if (DBG) Log.d(LOG_TAG, "plugType = " + plugType);
            if (plugType == 0) {
                getActivity().finish();
            }
        }
    }

    @Override
    public int getMetricsCategory() {
        // TODO Auto-generated method stub
        return 1;
    }

    private boolean isUmsAvailable() {
        int sdVolSize = getSdcardVolumes().size();
        if( sdVolSize > 1) {
            Log.d(LOG_TAG, String.format("multiple[%d] partition in sdcard, skip", sdVolSize));
            return false;
        }
        String sdcardState = EnvironmentEx.getExternalStoragePathState();
        Log.d(LOG_TAG, "sdcardState = " + sdcardState);
        if (Environment.MEDIA_SHARED.equals(sdcardState) || Environment.MEDIA_UNMOUNTED.equals(sdcardState)
                || Environment.MEDIA_MOUNTED.equals(sdcardState)) return true;
        return false;
    }

    private List<VolumeInfo> getSdcardVolumes() {
        List<VolumeInfo> vols = new ArrayList<>();
        final ArrayList<VolumeInfo> res = new ArrayList<>();
        final IStorageManager storageManager = IStorageManager.Stub.asInterface(ServiceManager.getService("mount"));
        try {
            vols = Arrays.asList(storageManager.getVolumes(0));
        } catch (RemoteException e) {
            Log.d(LOG_TAG, "RemoteException connected with StorageManager");
        }
        for (VolumeInfo vol : vols) {
            if (vol.disk != null && vol.disk.isSd()) {
                res.add(vol);
            }
        }
        return res;
    }

    /* SPRD:Add for bug#733341 Disallow USB file transfer cts tests failed @{ */
    @Override
    public void OnFileTransferRestricted(PreferenceViewHolder view) {
        if (mIsFileTransferRestricted) {
            TextView title = (TextView) view.findViewById(android.R.id.title);
            ImageView infoButton = (ImageView) view.findViewById(R.id.restricted_icon);
            RadioButton radioButton = (RadioButton) view.findViewById(android.R.id.checkbox);
            TextView summary = (TextView) view.findViewById(android.R.id.summary);

            radioButton.setVisibility(View.GONE);
            infoButton.setVisibility(View.VISIBLE);
            summary.setEnabled(false);
            title.setEnabled(false);
        }
    }
    /* Bug733341 @} */

    /**
     * For Search.
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    SearchIndexableResource res = new SearchIndexableResource(context);
                    res.xmlResId = R.xml.sprd_usb_settings;
                    return Lists.newArrayList(res);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    keys.add(KEY_UMS);
                    IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_STATE);
                    Intent intent = context.registerReceiver(null, filter);
                    boolean connected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                    if(!connected) {
                        keys.add(KEY_CHARGE_ONLY);
                        keys.add(KEY_MTP);
                        keys.add(KEY_PTP);
                        keys.add(KEY_CDROM);
                        keys.add(KEY_MIDI);
                    }
                    return keys;
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return super.createPreferenceControllers(context);
                }
            };
}

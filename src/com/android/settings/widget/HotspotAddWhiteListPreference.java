package com.android.settings.widget;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.TextUtils;

import android.net.wifi.WifiManager;
import androidx.appcompat.app.AlertDialog;

import java.util.List;
import java.util.regex.Pattern;
import android.widget.Toast;
import com.android.settings.R;
import com.android.settingslib.CustomDialogPreferenceCompat;

public class HotspotAddWhiteListPreference extends CustomDialogPreferenceCompat implements
        DialogInterface.OnShowListener {

    EditText mNameText;
    MacAddressEditText mMacText;
    private Context mContext;
    private WifiManager mWifiManager;
    private boolean isPositiveButtonEnabled = false;
    private static final String patternStr = "^[A-Fa-f0-9]{2}(:[A-Fa-f0-9]{2}){5}$";
    public HotspotAddWhiteListPreference(Context context) {
        super(context);
        mContext = context;
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public HotspotAddWhiteListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public HotspotAddWhiteListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public HotspotAddWhiteListPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        final Context context = getContext();

        // Register so we can adjust the buttons if needed once the dialog is available.
        setOnShowListener(this);
        addWhiteListViews((ScrollView) view);

    }

    @Override
    protected void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
                List<String> mWhitelistStations = mWifiManager.softApGetClientWhiteList();
                if (mWhitelistStations != null && mWhitelistStations.size() >= 10) {
                    String error = "null";
                    if (mContext != null) {
                        error = String.format(mContext.getString(R.string.wifi_add_whitelist_limit_error), 10);
                    }
                    Toast.makeText(mContext, error, Toast.LENGTH_SHORT).show();
                    return;
                }
                mWifiManager.softApAddClientToWhiteList(mMacText.getText().toString().trim(), mNameText.getText().toString().trim());
                callChangeListener(true);
        } else {
            callChangeListener(false);
        }
    }

    @Override
    public void onShow(DialogInterface dialog) {
        isPositiveButtonEnabled = isAddWhitelistButtonEnabled();
        updatePositiveButton();
    }

    private void addWhiteListViews(ScrollView view) {
        mNameText = view.findViewById(R.id.nameText);
        mMacText = view.findViewById(R.id.macText);
        mNameText.addTextChangedListener(addTextChangedListener);
        mMacText.addTextChangedListener(macTextChangedListener);
    }

    TextWatcher addTextChangedListener = new TextWatcher(){
        @Override
        public void beforeTextChanged(CharSequence s, int start,
            int count, int after) {
                // TODO Auto-generated method stub
        }
        @Override
        public void onTextChanged(CharSequence s, int start,
                int before, int count) {
            // TODO Auto-generated method stub
        }
        @Override
        public void afterTextChanged(Editable s) {
                isPositiveButtonEnabled = isAddWhitelistButtonEnabled();
                updatePositiveButton();
        }
    };

    MacAddressEditText.MacWatcher macTextChangedListener = new MacAddressEditText.MacWatcher() {
        @Override
        public void onTextChanged() {
            isPositiveButtonEnabled = isAddWhitelistButtonEnabled();
            updatePositiveButton();
        }
    };

    private void updatePositiveButton(){
        AlertDialog dialog = (AlertDialog) getDialog();
        Button button = dialog == null ? null : dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null && mNameText != null && mMacText != null) {
            button.setEnabled(isPositiveButtonEnabled);
        }
    }

    private boolean isAddWhitelistButtonEnabled() {
        if (TextUtils.isEmpty(mMacText.getText()) ||
                !checkMac(mMacText.getText().toString().trim())) {
            return false;
        }
        if (TextUtils.isEmpty(mNameText.getText()) ||
                mNameText.getText().toString().trim().equals("")) {
            return false;
        }
        return true;
    }

    private boolean checkMac(String str) {
        return Pattern.matches(patternStr, str);
    }
}

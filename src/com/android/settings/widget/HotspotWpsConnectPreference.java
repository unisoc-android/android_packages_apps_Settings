package com.android.settings.widget;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;

import android.net.wifi.WpsInfo;
import android.net.wifi.WifiManager;
import androidx.appcompat.app.AlertDialog;

import android.util.Log;
import com.android.settings.R;
import com.android.settingslib.CustomDialogPreferenceCompat;

public class HotspotWpsConnectPreference extends CustomDialogPreferenceCompat implements
        DialogInterface.OnShowListener {

    EditText mPinEdit;
    TextView mPinText;
    Spinner mModeSpinner;
    private boolean needPin = false;
    private WifiManager mWifiManager;
    private Context mContext;
    private boolean isPositiveButtonEnabled = true;
    private final String TAG = "HotspotWpsConnect";
    public HotspotWpsConnectPreference(Context context) {
        super(context);
        mContext = context;
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public HotspotWpsConnectPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public HotspotWpsConnectPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public HotspotWpsConnectPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        // Register so we can adjust the buttons if needed once the dialog is available.
        setOnShowListener(this);
        addWpsModeViews((LinearLayout) view);
    }

    @Override
    protected void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            if (needPin) {
                if (mWifiManager.softApWpsCheckPin(mPinEdit.getText().toString().trim())) {
                    WpsInfo config = new WpsInfo();
                    config.pin = mPinEdit.getText().toString().trim();
                    config.setup = WpsInfo.KEYPAD;
                    Log.d(TAG,"hotspot wps config: "+config.toString());
                    mWifiManager.softApStartWps(config,null);
                } else {
                    Toast.makeText(mContext, R.string.hotspot_pin_error,
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                WpsInfo config = new WpsInfo();
                config.setup = WpsInfo.PBC;
                Log.d(TAG,"hotspot wps config: "+config.toString());
                mWifiManager.softApStartWps(config,null);
            }
        }
    }

    @Override
    public void onShow(DialogInterface dialog) {
        updatePositiveButton();
    }

    private void addWpsModeViews(LinearLayout view) {
        mPinEdit = view.findViewById(R.id.pin_number);
        mPinText = view.findViewById(R.id.hotspot_wps_pin);
        mModeSpinner = view.findViewById(R.id.hotspot_wps_mode);
        mPinEdit.addTextChangedListener(addTextChangedListener);
        mModeSpinner.setOnItemSelectedListener(wpsSelectedListener);
    }

    OnItemSelectedListener wpsSelectedListener = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view,
                int position, long id) {
            if (position == 0) {
                mPinEdit.setVisibility(View.GONE);
                mPinText.setVisibility(View.GONE);
                needPin = false;
                isPositiveButtonEnabled = true;
                updatePositiveButton();
            } else {
                mPinEdit.setVisibility(View.VISIBLE);
                mPinText.setVisibility(View.VISIBLE);
                needPin = true;
                isPositiveButtonEnabled = isWpsPinAvailable();
                updatePositiveButton();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    TextWatcher addTextChangedListener =new TextWatcher(){
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
                isPositiveButtonEnabled = isWpsPinAvailable();
                updatePositiveButton();
        }
    };

    private void updatePositiveButton(){
        AlertDialog dialog = (AlertDialog) getDialog();
        Button button = dialog == null ? null : dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setEnabled(isPositiveButtonEnabled);
        }
    }


    private boolean isWpsPinAvailable() {
        if (mPinEdit != null && !TextUtils.isEmpty(mPinEdit.getText())) {
            mPinEdit.setSelection(mPinEdit.getText().length());
        }
        if (mPinEdit != null) {
            String pin = mPinEdit.getText().toString().trim();
            if (pin.matches("[0-9]{8}") && !pin.equals("")) {
                return true;
            }
        }
        return false;
    }
}


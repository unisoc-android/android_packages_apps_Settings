/** Created by Spreadst */

package com.sprd.settings.wifi;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiFeaturesUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.Global;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.android.settings.R;

public class WifiConnectedWarningActivity extends Activity implements OnCheckedChangeListener{

    private static final String TAG = "CmccConnectedWarningActivity";
    private static final boolean DBG = true;
    private static final int INVALID_VALUE = -1;
    private static final int MESSAGE_DIALOG_TIME_OUT = 0;
    private static final int MESSAGE_DIALOG_TIME_OUT_VALUE = 5 * 1000;

    private TextView mMessage;
    private CheckBox mCheckBox;

    private Handler mHandler;

    private int mDialogType = INVALID_VALUE;
    private String mSsidName = null;
    private int mSsidNetworkId = INVALID_VALUE;
    private String[] mSsidNameArrary = null;
    private int[] mSsidNetworkIdArrary = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.wifi_connected_waring_dialog);

        getWindow().setCloseOnTouchOutside(false);

        setTitle(R.string.network_disconnect_title);
        mMessage = (TextView) findViewById(R.id.message);
        mMessage.setText(R.string.connect_to_cmcc_ap_message);

        mCheckBox = (CheckBox) findViewById(R.id.do_not_prompt);
        mCheckBox.setOnCheckedChangeListener(this);

        mHandler = new UpdateHandler();
        mHandler.sendEmptyMessageDelayed(MESSAGE_DIALOG_TIME_OUT,
                MESSAGE_DIALOG_TIME_OUT_VALUE);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Global.putInt(getContentResolver(),
                WifiFeaturesUtils.WIFI_CONNECTED_WARNING_FLAG,
                buttonView.isChecked() ? 0 : 1);
    }

    private class UpdateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_DIALOG_TIME_OUT:
                    finish();
                    break;
                default:
                    break;
            }
        }
    }

    private void logd(String logString) {
        if (DBG) {
            Log.d(TAG, logString);
        }
    }
}

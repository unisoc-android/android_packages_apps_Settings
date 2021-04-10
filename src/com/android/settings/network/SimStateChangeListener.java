package com.android.settings.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneConstants;

public class SimStateChangeListener {
    private final static String TAG = "SimStateChangeListener";

    public interface SimStateChangeListenerClient {
        void onSimAbsent(int phoneId);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                    SubscriptionManager.INVALID_PHONE_INDEX);
            String simState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simState)){
                Log.d(TAG,"sim " + phoneId + " removed");
                onSimAbsentCallback(phoneId);
            }
        }
    };

    private Context mContext;
    private SimStateChangeListenerClient mClient;
    public SimStateChangeListener(Context context, SimStateChangeListenerClient client) {
        mContext = context;
        mClient = client;
    }

    public void start() {
        final IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    public void stop() {
        mContext.unregisterReceiver(mReceiver);
    }

    public void onSimAbsentCallback(int phoneId) {
        mClient.onSimAbsent(phoneId);
    }
}

package com.android.settings.network.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;

public class BroadcastReceiverChanged {
    public interface BroadcastReceiverChangedClient {
        void onPhoneStateChanged();
        void onCarrierConfigChanged(int phoneId);
    }

    private Context mContext;
    private BroadcastReceiverChangedClient mClient;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                onPhoneStateChangedCallback();
            } else if(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(action)) {
                int CONFIG_CHANGED_SUB = CarrierConfigManagerEx.CONFIG_SUBINFO;
                if (intent.getIntExtra(CarrierConfigManagerEx.CARRIER_CONFIG_CHANGED_TYPE, -1) == CONFIG_CHANGED_SUB) {
                    int phoneId = intent.getIntExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, -1);
                    onCarrierConfigChangedCallback(phoneId);
                }
            }
        }
    };

    public BroadcastReceiverChanged (Context context,BroadcastReceiverChangedClient client) {
        mContext = context;
        mClient = client;
    }

    public void start() {
        final IntentFilter intentFilter = new IntentFilter(
                TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        //UNISOC: modify for bug1146093
        intentFilter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
    }

    public void stop() {
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    private void onPhoneStateChangedCallback(){
        mClient.onPhoneStateChanged();
    }
    private void onCarrierConfigChangedCallback(int phoneId) {
        mClient.onCarrierConfigChanged(phoneId);
    }
}
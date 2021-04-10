/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.network.telephony;

import android.annotation.IntDef;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.CellInfo;
import android.telephony.NetworkScan;
import android.telephony.NetworkScanRequest;
import android.telephony.PhoneStateListener;
import android.telephony.PreciseDataConnectionState;
import android.telephony.RadioAccessSpecifier;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.telephony.TelephonyScanManager;
import android.widget.Toast;
import android.util.Log;

import com.android.internal.telephony.CellNetworkScanResult;
import com.android.sprd.telephony.RadioInteractor;

import com.android.settings.R;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * A helper class that builds the common interface and performs the network scan for two different
 * network scan APIs.
 */
public class NetworkScanHelper {
    public static final String TAG = "NetworkScanHelper";

    private DataHandler mDataHandler = new DataHandler();
    private int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
    private PhoneStateListener[] mPhoneStateListeners = new PhoneStateListener[mPhoneCount];
    private int[] mDataState = new int[mPhoneCount];
    private int mConnectedPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;
    private int mDisconnectPendingCount = mPhoneCount;
    private boolean mScanFlag = true;
    private int mType;

    private Looper mLooper;
    private Messenger mMessenger;

    private static final int EVENT_DATA_DISCONNECTED_DONE = 100;
    private static final int EVENT_DC_FORCE_DETACH = 200;
    private static final int EVENT_DC_FORCE_DETACH_DONE = 300;

    /**
     * Callbacks interface to inform the network scan results.
     */
    public interface NetworkScanCallback {
        /**
         * Called when the results is returned from {@link TelephonyManager}. This method will be
         * called at least one time if there is no error occurred during the network scan.
         *
         * <p> This method can be called multiple times in one network scan, until
         * {@link #onComplete()} or {@link #onError(int)} is called.
         *
         * @param results
         */
        void onResults(List<CellInfo> results);

        /**
         * Called when the current network scan process is finished. No more
         * {@link #onResults(List)} will be called for the current network scan after this method is
         * called.
         */
        void onComplete();

        /**
         * Called when an error occurred during the network scan process.
         *
         * <p> There is no more result returned from {@link TelephonyManager} if an error occurred.
         *
         * <p> {@link #onComplete()} will not be called if an error occurred.
         *
         * @see {@link NetworkScan.ScanErrorCode}
         */
        void onError(int errorCode);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NETWORK_SCAN_TYPE_WAIT_FOR_ALL_RESULTS, NETWORK_SCAN_TYPE_INCREMENTAL_RESULTS})
    public @interface NetworkQueryType {}

    /**
     * Performs the network scan using {@link TelephonyManager#getAvailableNetworks()}. The network
     * scan results won't be returned to the caller until the network scan is completed.
     *
     * <p> This is typically used when the modem doesn't support the new network scan api
     * {@link TelephonyManager#requestNetworkScan(
     * NetworkScanRequest, Executor, TelephonyScanManager.NetworkScanCallback)}.
     */
    public static final int NETWORK_SCAN_TYPE_WAIT_FOR_ALL_RESULTS = 1;

    /**
     * Performs the network scan using {@link TelephonyManager#requestNetworkScan(
     * NetworkScanRequest, Executor, TelephonyScanManager.NetworkScanCallback)} The network scan
     * results will be returned to the caller periodically in a small time window until the network
     * scan is completed. The complete results should be returned in the last called of
     * {@link NetworkScanCallback#onResults(List)}.
     *
     * <p> This is recommended to be used if modem supports the new network scan api
     * {@link TelephonyManager#requestNetworkScan(
     * NetworkScanRequest, Executor, TelephonyScanManager.NetworkScanCallback)}
     */
    public static final int NETWORK_SCAN_TYPE_INCREMENTAL_RESULTS = 2;

    /** The constants below are used in the async network scan. */
    private static final boolean INCREMENTAL_RESULTS = true;
    private static final int SEARCH_PERIODICITY_SEC = 5;
    private static final int MAX_SEARCH_TIME_SEC = 300;
    private static final int INCREMENTAL_RESULTS_PERIODICITY_SEC = 3;

    private static final NetworkScanRequest NETWORK_SCAN_REQUEST =
            new NetworkScanRequest(
                    NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                    new RadioAccessSpecifier[]{
                            // GSM
                            new RadioAccessSpecifier(
                                    AccessNetworkType.GERAN,
                                    null /* bands */,
                                    null /* channels */),
                            // LTE
                            new RadioAccessSpecifier(
                                    AccessNetworkType.EUTRAN,
                                    null /* bands */,
                                    null /* channels */),
                            // WCDMA
                            new RadioAccessSpecifier(
                                    AccessNetworkType.UTRAN,
                                    null /* bands */,
                                    null /* channels */)
                    },
                    SEARCH_PERIODICITY_SEC,
                    MAX_SEARCH_TIME_SEC,
                    INCREMENTAL_RESULTS,
                    INCREMENTAL_RESULTS_PERIODICITY_SEC,
                    null /* List of PLMN ids (MCC-MNC) */);

    // UNISOC: Bug1175341(FL1000063592) Ignore network scan under 2G
    private static final NetworkScanRequest NETWORK_SCAN_REQUEST_CUSTOMIZED =
            new NetworkScanRequest(
                    NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                    new RadioAccessSpecifier[]{
                            // LTE
                            new RadioAccessSpecifier(
                                    AccessNetworkType.EUTRAN,
                                    null /* bands */,
                                    null /* channels */),
                            // WCDMA
                            new RadioAccessSpecifier(
                                    AccessNetworkType.UTRAN,
                                    null /* bands */,
                                    null /* channels */)
                    },
                    SEARCH_PERIODICITY_SEC,
                    MAX_SEARCH_TIME_SEC,
                    INCREMENTAL_RESULTS,
                    INCREMENTAL_RESULTS_PERIODICITY_SEC,
                    null /* List of PLMN ids (MCC-MNC) */);

    private final Context mContext;
    private final NetworkScanCallback mNetworkScanCallback;
    private final TelephonyManager mTelephonyManager;
    private final TelephonyManagerEx mTelephonyManagerEx;
    private final TelephonyScanManager.NetworkScanCallback mInternalNetworkScanCallback;
    private final Executor mExecutor;
    // Add for Bug1175341(FL1000063592)
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private NetworkScan mNetworkScanRequester;

    /** Callbacks for sync network scan */
    private ListenableFuture<List<CellInfo>> mNetworkScanFuture;

    public NetworkScanHelper(Context context, TelephonyManager tm, NetworkScanCallback callback, Executor executor, int subId) {
        mContext = context;
        mTelephonyManager = tm;
        mTelephonyManagerEx = TelephonyManagerEx.from(mContext);
        mNetworkScanCallback = callback;
        mInternalNetworkScanCallback = new NetworkScanCallbackImpl();
        mExecutor = executor;
        // Add for Bug1175341(FL1000063592)
        mSubId = subId;
    }

    /**
     * Performs a network scan for the given type {@code type}.
     * {@link #NETWORK_SCAN_TYPE_INCREMENTAL_RESULTS} is recommended if modem supports
     * {@link TelephonyManager#requestNetworkScan(
     * NetworkScanRequest, Executor, TelephonyScanManager.NetworkScanCallback)}.
     *
     * @param type used to tell which network scan API should be used.
     */
    public void startNetworkScan(@NetworkQueryType int type) {
        if (type == NETWORK_SCAN_TYPE_WAIT_FOR_ALL_RESULTS) {
            mNetworkScanFuture = SettableFuture.create();
            Futures.addCallback(mNetworkScanFuture, new FutureCallback<List<CellInfo>>() {
                @Override
                public void onSuccess(List<CellInfo> result) {
                    onResults(result);
                    onComplete();
                }

                @Override
                public void onFailure(Throwable t) {
                    int errCode = Integer.parseInt(t.getMessage());
                    onError(errCode);
                }
            });
            mExecutor.execute(new NetworkScanSyncTask(
                    mTelephonyManager, (SettableFuture) mNetworkScanFuture));
        } else if (type == NETWORK_SCAN_TYPE_INCREMENTAL_RESULTS) {
            mNetworkScanRequester = mTelephonyManager.requestNetworkScan(
                  /*NETWORK_SCAN_REQUEST, */
                    // Bug1175341(FL1000063592)
                    isCustomizedNetworkScan()? NETWORK_SCAN_REQUEST_CUSTOMIZED: NETWORK_SCAN_REQUEST,
                    mExecutor,
                    mInternalNetworkScanCallback);
        }
    }

    /**
     * UNISOC: FL0102060007 Disconnect all data connection before a network scan start
     */
    public void startNetworkScanEx(int type) {
          mType = type;
          deactivateDataConnections();
    }

    /**
     * The network scan of type {@link #NETWORK_SCAN_TYPE_WAIT_FOR_ALL_RESULTS} can't be stopped,
     * however, the result of the current network scan won't be returned to the callback after
     * calling this method.
     */
    public void stopNetworkQuery() {
        if (mNetworkScanRequester != null) {
            mNetworkScanRequester.stopScan();
            mNetworkScanFuture = null;
        }

        if (mNetworkScanFuture != null) {
            mNetworkScanFuture.cancel(true /* mayInterruptIfRunning */);
            mNetworkScanFuture = null;
        }
    }

    private void onResults(List<CellInfo> cellInfos) {
        mNetworkScanCallback.onResults(cellInfos);
    }

    private void onComplete() {
        mNetworkScanCallback.onComplete();
    }

    private void onError(int errCode) {
        mNetworkScanCallback.onError(errCode);
    }

    /**
     * Converts the status code of {@link CellNetworkScanResult} to one of the
     * {@link NetworkScan.ScanErrorCode}.
     * @param errCode status code from {@link CellNetworkScanResult}.
     *
     * @return one of the scan error code from {@link NetworkScan.ScanErrorCode}.
     */
    private static int convertToScanErrorCode(int errCode) {
        switch (errCode) {
            case CellNetworkScanResult.STATUS_RADIO_NOT_AVAILABLE:
                return NetworkScan.ERROR_RADIO_INTERFACE_ERROR;
            case CellNetworkScanResult.STATUS_RADIO_GENERIC_FAILURE:
            default:
                return NetworkScan.ERROR_MODEM_ERROR;
        }
    }

    private final class NetworkScanCallbackImpl extends TelephonyScanManager.NetworkScanCallback {
        public void onResults(List<CellInfo> results) {
            Log.d(TAG, "Async scan onResults() results = "
                    + CellInfoUtil.cellInfoListToString(results));
            NetworkScanHelper.this.onResults(results);
        }

        public void onComplete() {
            Log.d(TAG, "async scan onComplete()");
            NetworkScanHelper.this.onComplete();
        }

        public void onError(@NetworkScan.ScanErrorCode int errCode) {
            Log.d(TAG, "async scan onError() errorCode = " + errCode);
            NetworkScanHelper.this.onError(errCode);
        }
    }

    private static final class NetworkScanSyncTask implements Runnable {
        private final SettableFuture<List<CellInfo>> mCallback;
        private final TelephonyManager mTelephonyManager;

        NetworkScanSyncTask(
                TelephonyManager telephonyManager, SettableFuture<List<CellInfo>> callback) {
            mTelephonyManager = telephonyManager;
            mCallback = callback;
        }

        @Override
        public void run() {
            final CellNetworkScanResult result = mTelephonyManager.getAvailableNetworks();
            if (result.getStatus() == CellNetworkScanResult.STATUS_SUCCESS) {
                final List<CellInfo> cellInfos = result.getOperators()
                        .stream()
                        .map(operatorInfo
                                -> CellInfoUtil.convertOperatorInfoToCellInfo(operatorInfo))
                        .collect(Collectors.toList());
                Log.d(TAG, "Sync network scan completed, cellInfos = "
                        + CellInfoUtil.cellInfoListToString(cellInfos));
                mCallback.set(cellInfos);
            } else {
                final Throwable error = new Throwable(
                        Integer.toString(convertToScanErrorCode(result.getStatus())));
                mCallback.setException(error);
                Log.d(TAG, "Sync network scan error, ex = " + error);
            }
        }
    }

    /** UNISOC: FL0102060007 Disconnect all data connection before a network scan start */
    private class DataHandler extends Handler {
        DataHandler(){
        }

        @Override
        public void handleMessage(Message msg){
            Log.d(TAG,"DataHandler handleMessage: " + msg);
            switch(msg.what){
            case EVENT_DATA_DISCONNECTED_DONE:
                boolean disconnected = disconnectedOnAllPhones();
                if (mDisconnectPendingCount > 0) {
                    mDisconnectPendingCount--;
                }

                Log.d(TAG,"mScanFlag: " + mScanFlag + ", disconnected: " + disconnected +
                        ", mDisconnectPendingCount: " + mDisconnectPendingCount);

                if (mScanFlag && disconnected && (mDisconnectPendingCount == 0)){
                    if (needForceDetach(mConnectedPhoneId)) {
                        mScanFlag = false;
                        sendEmptyMessage(EVENT_DC_FORCE_DETACH);
                    } else {
                        Log.d(TAG, "Not need detach, do query network");
                        unregister();
                        mScanFlag = false;
                        startNetworkScan(mType);
                    }
                }
                break;
            case EVENT_DC_FORCE_DETACH:
                forceDetachDataConn(mConnectedPhoneId);
                break;
            case EVENT_DC_FORCE_DETACH_DONE:
                Log.d(TAG, "All data detached, do query network");
                unregister();
                mScanFlag = false;
                startNetworkScan(mType);
                break;
            default:
                break;
            }
        }
    }

    private void deactivateDataConnections(){
        Log.d(TAG, "deactivateDataConnections" );
        Toast.makeText(mContext, R.string.mobile_data_not_allowed,
                Toast.LENGTH_LONG).show();
        for (int phoneId = 0; phoneId < mPhoneCount; phoneId++) {
            if (mConnectedPhoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
                if (!mTelephonyManagerEx.isDataDisconnected(phoneId)) {
                    // current data phone id
                    mConnectedPhoneId = phoneId;
                    Log.d(TAG, "mConnectedPhoneId: " + mConnectedPhoneId);
                }
            }
            mTelephonyManagerEx.setInternalDataEnabled(phoneId, false);
        }
        register();
    }

    private PhoneStateListener getPhoneStateListener(int phoneId) {
        final int i = phoneId;
        mPhoneStateListeners[phoneId]  = new PhoneStateListener() {
            @Override
            public void onPreciseDataConnectionStateChanged(PreciseDataConnectionState dataConnectionState) {
                Log.d(TAG,"onPreciseDataConnectionStateChanged for phone: " + i
                        + ", connection state: " + dataConnectionState);

                mDataState[i] = dataConnectionState.getDataConnectionState();
                if (mDataState[i] == TelephonyManager.DATA_DISCONNECTED
                        && mTelephonyManagerEx.isDataDisconnected(i)){
                    mDataHandler.sendMessage(mDataHandler.obtainMessage(EVENT_DATA_DISCONNECTED_DONE));
                }
            }
        };
        return mPhoneStateListeners[phoneId];
    }

    private void register(){
        for (int i = 0; i < mPhoneCount; i++){
            if(!mTelephonyManagerEx.isDataDisconnected(i)){
                Log.d(TAG,"register for data state change on phone " + i + ", subId= " + SubscriptionManager.getSubId(i)[0]);
                final TelephonyManager tm =
                        (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                tm.createForSubscriptionId(SubscriptionManager.getSubId(i)[0]).listen(getPhoneStateListener(i),
                        PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE);
            } else {
                Log.d(TAG, "data already disconnected on phone " + i);
                mDataState[i] = TelephonyManager.DATA_DISCONNECTED;
                mDataHandler.sendMessage(mDataHandler.obtainMessage(EVENT_DATA_DISCONNECTED_DONE));
            }
        }
    }

    private void unregister(){
        final TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        for (int i = 0; i < mPhoneCount; i++){
            if (mPhoneStateListeners[i] != null){
                tm.listen(mPhoneStateListeners[i], PhoneStateListener.LISTEN_NONE);
                mPhoneStateListeners[i] = null;
            }
        }
    }

    private boolean disconnectedOnAllPhones(){
        for (int i = 0; i < mPhoneCount; i++){
            if (mDataState[i] != TelephonyManager.DATA_DISCONNECTED){
                Log.d(TAG, "data not disconnected on phone: " + i);
                return false;
            }
        }
        return true;
    }

    private boolean needForceDetach(int phoneId) {
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            int subId = SubscriptionManager.getSubId(phoneId)[0];
            ServiceState serviceState = TelephonyManager.from(mContext).getServiceStateForSubscriber(subId);
            int rt = serviceState.getRilVoiceRadioTechnology();
            Log.d(TAG, "needForceDetach() rt=" + rt);
            // Modem will handle data detach in 4G, so we don't send close data in 4G
            return rt != ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
        }
        return false;
    }

    private void forceDetachDataConn(Messenger messenger, int phoneId) {
      RadioInteractor radioInteractor = new RadioInteractor(mContext);
      if (radioInteractor != null) {
          radioInteractor.forceDetachDataConn(messenger, phoneId);
      }
    }

    private void forceDetachDataConn(int phoneId){
        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mLooper = thread.getLooper();
        mMessenger = new Messenger(new Handler(mLooper) {
            @Override
            public void handleMessage(Message msg) {
              Log.d(TAG, "Messenger handleMessage: " + msg);
              mDataHandler.sendEmptyMessage(EVENT_DC_FORCE_DETACH_DONE);
            }
        });
        forceDetachDataConn(mMessenger, phoneId);
    }
  /**
   * @}
   */

    /**
     * UNISOC: Bug1175341(FL1000063592) Ignore network scan under 2G
     */
    private boolean isCustomizedNetworkScan() {
        PersistableBundle bundle = ((CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE)).getConfigForSubId(mSubId);
        if (bundle != null) {
           return bundle.getBoolean(CarrierConfigManagerEx.KEY_IGNORE_NETWORK_SCAN_UNDER_2G);
        }
        return false;
    }
    /**
     * @}
     */

    public void onDestroy() {
        unregister();
    }
}
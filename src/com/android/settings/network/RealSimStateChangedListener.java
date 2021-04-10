package com.android.settings.network;


import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.sprd.telephony.RadioInteractor;
import com.android.sprd.telephony.RadioInteractorCallbackListener;

public class RealSimStateChangedListener {
    private RealSimStateChangeListenerClient mClient;
    private RadioInteractor mRadioInteractor;
    private RadioInteractorCallbackListener[] mRadioInteractorCallbackListener;
    private int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();

    public interface RealSimStateChangeListenerClient {
        void notifyRealSimStateChanged (int phoneId);
    }

    public RealSimStateChangedListener(Context context,RealSimStateChangeListenerClient client) {
        mRadioInteractorCallbackListener = new RadioInteractorCallbackListener[mPhoneCount];
        mRadioInteractor = new RadioInteractor(context);
        mClient = client;
    }

    public void start() {
        for (int i = 0; i < mPhoneCount; i++) {
            final int phoneId = i;
            mRadioInteractorCallbackListener[i] = new RadioInteractorCallbackListener(phoneId) {
                @Override
                public void onRealSimStateChangedEvent() {
                    Log.d("RealSimStateChangedListener", "onRealSimStateChangedEvent phoneId = " + phoneId);
                    notifyRealSimStateChanged(phoneId);
                }
            };
            mRadioInteractor.listen(mRadioInteractorCallbackListener[i],
                    RadioInteractorCallbackListener.LISTEN_SIMMGR_SIM_STATUS_CHANGED_EVENT, false);
        }
    }

    public void stop () {
        for (int i = 0; i < mPhoneCount; i++) {
            if (mRadioInteractorCallbackListener[i] != null) {
                mRadioInteractor.listen(mRadioInteractorCallbackListener[i],
                        RadioInteractorCallbackListener.LISTEN_NONE);
                mRadioInteractorCallbackListener[i] = null;
            }
        }
    }

    public void notifyRealSimStateChanged(int phoneId) {
        mClient.notifyRealSimStateChanged(phoneId);
    }
}

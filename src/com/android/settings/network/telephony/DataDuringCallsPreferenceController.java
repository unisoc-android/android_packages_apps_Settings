/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.settings.network.telephony;

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settings.network.DefaultDataSubIdContentObserver;
import com.android.settings.network.SubscriptionsChangeListener;

public class DataDuringCallsPreferenceController extends TelephonyTogglePreferenceController
        implements LifecycleObserver,
        SubscriptionsChangeListener.SubscriptionsChangeListenerClient {

    private SwitchPreference mPreference;
    private SubscriptionsChangeListener mChangeListener;
    private TelephonyManager mManager;
    /*UNISOC： modify for bug 1111204, when default data card is
     * changed, need to remove this preference @{ */
    private DefaultDataSubIdContentObserver mDefaultDataSubIdContentObserver;
    /*UNISOC: @} */

    public DataDuringCallsPreferenceController(Context context,
            String preferenceKey) {
        super(context, preferenceKey);
        mChangeListener = new SubscriptionsChangeListener(mContext, this);
        /*UNISOC： modify for bug 1111204, when default data card is
        * changed, need to remove this preference @{ */
        mDefaultDataSubIdContentObserver = new DefaultDataSubIdContentObserver(
                new Handler(Looper.getMainLooper()));
        mDefaultDataSubIdContentObserver.setOnDefaultDataSubIdChangedListener(()->updateState(mPreference));
        /*UNISOC: @} */
    }

    public void init(Lifecycle lifecycle, int subId) {
        this.mSubId = subId;
        mManager = mContext.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
        lifecycle.addObserver(this);
    }

    @OnLifecycleEvent(ON_RESUME)
    public void onResume() {
        mChangeListener.start();
        /*UNISOC： modify for bug 1111204, when default data card is
        * changed, need to remove this preference @{ */
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mDefaultDataSubIdContentObserver.register(mContext);
        }
        /*UNISOC: @} */
    }

    @OnLifecycleEvent(ON_PAUSE)
    public void onPause() {
        mChangeListener.stop();
        /*UNISOC： modify for bug 1111204, when default data card is
        * changed, need to remove this preference @{ */
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mDefaultDataSubIdContentObserver.unRegister(mContext);
        }
        /*UNISOC: @} */
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public boolean isChecked() {
        return mManager.isDataAllowedInVoiceCall();
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        mManager.setDataAllowedDuringVoiceCall(isChecked);
        return true;
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        if (mSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ||
                SubscriptionManager.getDefaultDataSubscriptionId() == mSubId) {
            return CONDITIONALLY_UNAVAILABLE;
        }
        return AVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setVisible(isAvailable());
        /*UNISOC： modify for bug 1111204, when default data card is
        * changed, need to remove this preference @{ */
        ((SwitchPreference) preference).setChecked(isChecked());
        /*UNISOC: @} */
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled) {}

    @Override
    public void onSubscriptionsChanged() {
        updateState(mPreference);
    }
}

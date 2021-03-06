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

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.CollectionUtils;
import com.android.settings.R;
import com.android.settings.core.FeatureFlags;
import com.android.settings.core.SettingsBaseActivity;
import com.android.settings.development.featureflags.FeatureFlagPersistent;
import com.android.settings.network.SubscriptionUtil;
import com.android.settings.Utils;
import com.android.settings.overlay.FeatureFactory;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MobileNetworkActivity extends SettingsBaseActivity {

    private static final String TAG = "MobileNetworkActivity";
    @VisibleForTesting
    static final String MOBILE_SETTINGS_TAG = "mobile_settings:";
    @VisibleForTesting
    static final int SUB_ID_NULL = Integer.MIN_VALUE;

    @VisibleForTesting
    SubscriptionManager mSubscriptionManager;
    @VisibleForTesting
    int mCurSubscriptionId;
    @VisibleForTesting
    List<SubscriptionInfo> mSubscriptionInfos = new ArrayList<>();
//    private PhoneChangeReceiver mPhoneChangeReceiver;

    private final SubscriptionManager.OnSubscriptionsChangedListener
            mOnSubscriptionsChangeListener
            = new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            if (!Objects.equals(mSubscriptionInfos,
                    mSubscriptionManager.getActiveSubscriptionInfoList(true))) {
                updateSubscriptions(null);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* UNISOC: Bug 978574 Mobile network does not support screen-split @{*/
        if (isInMultiWindowMode()){
            Toast.makeText(getApplicationContext(), R.string.screen_split_not_support,
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        /* @} */

        if (FeatureFlagPersistent.isEnabled(this, FeatureFlags.NETWORK_INTERNET_V2)) {
            setContentView(R.layout.mobile_network_settings_container_v2);
        } else {
            setContentView(R.layout.mobile_network_settings_container);
        }
        setActionBar(findViewById(R.id.mobile_action_bar));
        // UNISOC: Bug 1177350
//        mPhoneChangeReceiver = new PhoneChangeReceiver(this, (subId) -> {
//            Log.d(TAG, "onPhoneChange subid = " + subId + " mCurSubscriptionId = " + mCurSubscriptionId);
//            if (mCurSubscriptionId != SUB_ID_NULL
//                    && (mCurSubscriptionId == subId || subId == SUB_ID_NULL)) {
//                // When the radio changes (ex: CDMA->GSM), refresh the fragment.
//                // This is very rare.
//                switchFragment(new MobileNetworkSettings(), mCurSubscriptionId,
//                        true /* forceUpdate */);
//            }
//        });
        mSubscriptionManager = getSystemService(SubscriptionManager.class);
        mSubscriptionInfos = mSubscriptionManager.getActiveSubscriptionInfoList(true);
        mCurSubscriptionId = savedInstanceState != null
                ? savedInstanceState.getInt(Settings.EXTRA_SUB_ID, SUB_ID_NULL)
                : SUB_ID_NULL;

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        updateSubscriptions(savedInstanceState);
        // UNISOC: Bug 1177350
//        mPhoneChangeReceiver.register();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSubscriptionManager.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // UNISOC: Bug 1177350
//        if (mPhoneChangeReceiver != null) {
//            mPhoneChangeReceiver.unregister();
//        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        saveInstanceState(outState);
    }

    @VisibleForTesting
    void saveInstanceState(@NonNull Bundle outState) {
        outState.putInt(Settings.EXTRA_SUB_ID, mCurSubscriptionId);
    }

    @VisibleForTesting
    void updateSubscriptions(Bundle savedInstanceState) {
        // Set the title to the name of the subscription. If we don't have subscription info, the
        // title will just default to the label for this activity that's already specified in
        // AndroidManifest.xml.
        mSubscriptionInfos = mSubscriptionManager.getActiveSubscriptionInfoList(true);
        final SubscriptionInfo subscription = getSubscription();
        if (CollectionUtils.isEmpty(mSubscriptionInfos) || (mCurSubscriptionId != SUB_ID_NULL
                && getSubscriptionId() != mCurSubscriptionId)) {
            finish();
            return;
        }
        if (subscription != null) {
            setTitle(subscription.getDisplayName());
        }

        if (!FeatureFlagPersistent.isEnabled(this, FeatureFlags.NETWORK_INTERNET_V2)) {
            updateBottomNavigationView();
        }

        if (savedInstanceState == null) {
            switchFragment(new MobileNetworkSettings(), getSubscriptionId());
        }
    }

    /**
     * Get the current subscription to display. First check whether intent has {@link
     * Settings#EXTRA_SUB_ID} and if so find the subscription with that id. If not, just return the
     * first one in the mSubscriptionInfos list since it is already sorted by sim slot.
     */
    @VisibleForTesting
    SubscriptionInfo getSubscription() {
        final Intent intent = getIntent();
        if (intent != null) {
            final int subId = intent.getIntExtra(Settings.EXTRA_SUB_ID, SUB_ID_NULL);
            if (subId != SUB_ID_NULL) {
                for (SubscriptionInfo subscription :
                        SubscriptionUtil.getAvailableSubscriptions(this)) {
                    if (subscription.getSubscriptionId() == subId) {
                        return subscription;
                    }
                }
            }
        }

        if (CollectionUtils.isEmpty(mSubscriptionInfos)) {
            return null;
        }
        return mSubscriptionInfos.get(0);
    }

    /**
     * Get the current subId to display.
     */
    @VisibleForTesting
    int getSubscriptionId() {
        final SubscriptionInfo subscription = getSubscription();
        if (subscription != null) {
            return subscription.getSubscriptionId();
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    @VisibleForTesting
    void updateBottomNavigationView() {
        final BottomNavigationView navigation = findViewById(R.id.bottom_nav);

        if (CollectionUtils.size(mSubscriptionInfos) <= 1) {
            navigation.setVisibility(View.GONE);
        } else {
            final Menu menu = navigation.getMenu();
            menu.clear();
            for (int i = 0, size = mSubscriptionInfos.size(); i < size; i++) {
                final SubscriptionInfo subscriptionInfo = mSubscriptionInfos.get(i);
                menu.add(0, subscriptionInfo.getSubscriptionId(), i,
                        subscriptionInfo.getDisplayName())
                        .setIcon(R.drawable.ic_settings_sim);
            }
            navigation.setOnNavigationItemSelectedListener(item -> {
                switchFragment(new MobileNetworkSettings(), item.getItemId());
                return true;
            });
        }
    }

    @VisibleForTesting
    void switchFragment(Fragment fragment, int subscriptionId) {
        switchFragment(fragment, subscriptionId, false /* forceUpdate */);
    }

    @VisibleForTesting
    void switchFragment(Fragment fragment, int subscriptionId, boolean forceUpdate) {
        if (mCurSubscriptionId != SUB_ID_NULL && subscriptionId == mCurSubscriptionId
                && !forceUpdate) {
            return;
        }
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        final Bundle bundle = new Bundle();
        bundle.putInt(Settings.EXTRA_SUB_ID, subscriptionId);

        fragment.setArguments(bundle);
        fragmentTransaction.replace(R.id.main_content, fragment,
                buildFragmentTag(subscriptionId));
        fragmentTransaction.commit();
        mCurSubscriptionId = subscriptionId;
    }

    private String buildFragmentTag(int subscriptionId) {
        return MOBILE_SETTINGS_TAG + subscriptionId;
    }

    @VisibleForTesting
    static class PhoneChangeReceiver extends BroadcastReceiver {
        private static final IntentFilter RADIO_TECHNOLOGY_CHANGED_FILTER = new IntentFilter(
                TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);

        private Context mContext;
        private Client mClient;

        interface Client {
            void onPhoneChange(int subId);
        }

        public PhoneChangeReceiver(Context context, Client client) {
            mContext = context;
            mClient = client;
        }

        public void register() {
            mContext.registerReceiver(this, RADIO_TECHNOLOGY_CHANGED_FILTER);
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isInitialStickyBroadcast()) {
                int subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID_NULL);
                Log.d(TAG, "onReceive subid = " + subId);
                mClient.onPhoneChange(subId);
            }
        }
    }

    /** Bug1193673: Support Touch assistant search @{ */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
         switch (keyCode) {
             case KeyEvent.KEYCODE_SEARCH:
                 final Intent intent = new Intent(android.provider.Settings.ACTION_APP_SEARCH_SETTINGS);
                 intent.setPackage(FeatureFactory.getFactory(getApplicationContext())
                         .getSearchFeatureProvider().getSettingsIntelligencePkgName(this));
                 ResolveInfo result = this.getPackageManager().resolveActivity(intent, 0);
                 if (result != null) {
                     startActivityForResult(intent, 0 /* requestCode */);
                 }
                 return true;
             default:
                 break;
         }
         return super.onKeyDown(keyCode, event);
     }
     /**
      * @} */
}

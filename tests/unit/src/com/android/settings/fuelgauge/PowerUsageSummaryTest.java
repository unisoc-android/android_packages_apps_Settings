/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PowerUsageSummaryTest {
    private static final String BATTERY_INTENT = "android.intent.action.POWER_USAGE_SUMMARY";
    private UiDevice mDevice;
    private Context mContext;
    private String mTargetPackage;

    @Before
    public void SetUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(instrumentation);
        mContext = InstrumentationRegistry.getTargetContext();
        mTargetPackage = mContext.getPackageName();
        instrumentation.startActivitySync(new Intent(BATTERY_INTENT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Test
    public void testClickLastFullCharge_shouldNotCrash() throws UiObjectNotFoundException {
        // The last full charge preference is at the bottom of the screen and needs to be scrolled to display
        final UiScrollable settings = new UiScrollable(
                new UiSelector().packageName(mTargetPackage).scrollable(true));
        final String item = mContext.getResources().getString(R.string.battery_last_full_charge);
        settings.scrollTextIntoView(item);
        mDevice.findObject(new UiSelector().text(item)).click();
        // onView(withText(R.string.battery_last_full_charge)).perform(click());
    }

    @Test
    public void testClickScreenUsage_shouldNotCrash() throws UiObjectNotFoundException {
        // The screen usage preference is at the bottom of the screen and needs to be scrolled to display
        final UiScrollable settings = new UiScrollable(
                new UiSelector().packageName(mTargetPackage).scrollable(true));
        final String item = mContext.getResources().getString(R.string.device_screen_usage);
        settings.scrollTextIntoView(item);
        mDevice.findObject(new UiSelector().text(item)).click();
        // onView(withText(R.string.device_screen_usage)).perform(click());
    }

}

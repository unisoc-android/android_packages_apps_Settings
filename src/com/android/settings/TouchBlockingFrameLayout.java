/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.settings;

import android.annotation.Nullable;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewParent;;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Extension of FrameLayout that consumes all touch events.
 */
public class TouchBlockingFrameLayout extends FrameLayout {
    private int lastX = -1;
    private int lastY = -1;

    public TouchBlockingFrameLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        //bug 1156179: sliding conflict of nested scrollview when display size is biggest
        return super.dispatchTouchEvent(ev);
    }

    /* bug 1156179: sliding conflict of nested scrollview when display size is biggest. @{ */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        int x = (int)ev.getRawX();
        int y = (int)ev.getRawY();
        int dealtX = 0;
        int dealtY = 0;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                dealtX += Math.abs(x - lastX);
                dealtY += Math.abs(y - lastY);
                if (dealtX >= dealtY) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                } else {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                lastX = x;
                lastY = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return false;
    }
    /* @} */
}

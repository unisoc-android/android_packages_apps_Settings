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
package com.android.settings.widget;

import android.content.Context;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.settings.R;

public class MacAddressEditText extends LinearLayout {
    private EditText et_mac1, et_mac2, et_mac3, et_mac4, et_mac5, et_mac6;
    private EditText etMacArray[] = new EditText[6];
    private String macText;
    private MacWatcher mListener;

    public interface MacWatcher {
        void onTextChanged();
    }
    public MacAddressEditText(Context context) {
        super(context);
        initView(context);
    }

    public MacAddressEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public MacAddressEditText(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        initView(context);
    }

    private void initView(Context context) {
        LayoutInflater.from(context).inflate(R.layout.mac_address, this, true);
        et_mac1 = (EditText) findViewById(R.id.mac1);
        et_mac2 = (EditText) findViewById(R.id.mac2);
        et_mac3 = (EditText) findViewById(R.id.mac3);
        et_mac4 = (EditText) findViewById(R.id.mac4);
        et_mac5 = (EditText) findViewById(R.id.mac5);
        et_mac6 = (EditText) findViewById(R.id.mac6);
        etMacArray = new EditText[]{et_mac1, et_mac2, et_mac3, et_mac4, et_mac5, et_mac6};
        for (EditText et : etMacArray) {
            if (et != null) {
                et.addTextChangedListener(etListener);
            }
        }
    }

    private void checkFocus(CharSequence charSequence) {
        macText = null;
        if (charSequence.toString().length() < 2) {
            for (EditText et : etMacArray) {
                if (et != null) {
                    if (et == et_mac1) {
                        macText = et.getText().toString().trim();
                    } else {
                        macText += et.getText().toString().trim();
                    }
                    if (et != et_mac6) {
                        macText += ":";
                    }
                }
            }
            if (mListener != null) {
                mListener.onTextChanged();
            }
            return;
        }
        for (EditText et : etMacArray) {
            if (et != null && et.getText().toString().length() > 1) {
                if (et == et_mac1) {
                    macText = et.getText().toString().trim();
                } else {
                    macText += et.getText().toString().trim();
                }
                if (et != et_mac6) {
                    macText += ":";
                    continue;
                } else {
                    if (mListener != null) {
                        mListener.onTextChanged();
                    }
                    break;
                }
            } else if (et != null) {
                et.setFocusable(true);
                et.requestFocus();
                break;
            }
        }
    }

    public void addTextChangedListener(MacWatcher listener) {
        mListener = listener;
    }

    public Editable getText() {
        return macText == null ? null : new SpannableStringBuilder(macText);
    }

    TextWatcher etListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            checkFocus(charSequence);
        }

        @Override
        public void afterTextChanged(Editable editable) {
        }
    };

}
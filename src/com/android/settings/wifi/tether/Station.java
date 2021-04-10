/** Created by Spreadst */

package com.android.settings.wifi.tether;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiFeaturesUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;
import androidx.preference.Preference;

import com.android.settings.R;

class Station extends Preference {

    private WifiManager mWifiManager;
    private Context mContext;

    private String stationName;
    private String stationMac;
    private String stationIP;
    private boolean isConnected;
    private boolean isWhitelist;
    private boolean supportWhitelist = WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_WHITE_LIST;
    private OnClickListener mButtonClick;
    private static final int DEFAULT_LIMIT = WifiFeaturesUtils.FeatureProperty.SUPPORT_SPRD_SOFTAP_MAX_NUMBER;

    private AlertDialog dialog;

    public Station(Context context, String name, String mac, String ip, boolean connected, boolean whitelist) {
        super(context);

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        mContext = context;
        stationName = name;
        stationMac = mac;
        stationIP = ip;
        isConnected = connected;
        isWhitelist = whitelist;
        setIconSpaceReserved(true);
        if (stationName != null) {
            setTitle(stationName);
            if (isConnected) {
                setSummary("IP: "+stationIP+"\nMAC: "+stationMac);
            } else {
                setSummary("Mac: "+stationMac);
            }
        } else {
            setTitle(stationMac);
        }
    }

    @Override
    protected void onClick() {
        askToAddWhiteList();
    }
    private void askToAddWhiteList() {
        // TODO Auto-generated method stub
        String stationNameTemp = stationName;
        if (stationNameTemp == null) {
            stationNameTemp = "";
        }
        if (dialog != null && dialog.isShowing()) {
            Context context = ((ContextWrapper) dialog.getContext()).getBaseContext();
            if (context instanceof Activity) {
                if (!((Activity) context).isFinishing() && !((Activity) context).isDestroyed()) {
                    dialog.dismiss();
                }
            } else {
                dialog.dismiss();
            }
            dialog = null;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        if (isWhitelist) {
            builder.setTitle(stationNameTemp)
                  .setMessage("MAC: "+stationMac)
                  .setPositiveButton(R.string.hotspot_offwhite, removeWhiteListListener)
                  .setNegativeButton(R.string.hotspot_whitelist_cancel, null);
        } else if (isConnected) {
            if (mWifiManager.softApIsWhiteListEnabled()) {
                builder.setTitle(stationNameTemp)
                      .setMessage("IP: "+stationIP+"\nMAC: "+stationMac)
                      .setNegativeButton(R.string.hotspot_whitelist_cancel, null);
            } else if (supportWhitelist){
                List<String> mWhitelistStationsDetail = mWifiManager.softApGetClientWhiteList();
                boolean isExistConnectedDevice = false;
                builder.setTitle(stationNameTemp)
                      .setMessage("IP: "+stationIP+"\nMAC: "+stationMac)
                      .setNegativeButton(R.string.block, controlBlockListener)
                      .setNeutralButton(R.string.hotspot_whitelist_cancel, null);
                for (String mWhitelistStationsStr : mWhitelistStationsDetail) {
                    String[] mWhitelistStations = mWhitelistStationsStr.split(" ");
                    int len = mWhitelistStations[0].length();
                    if (mWhitelistStations.length >= 2 && (mWhitelistStationsStr.substring(len+1).contains(stationNameTemp)) &&
                            (mWhitelistStations[0].contains(stationMac))) {
                        isExistConnectedDevice = true;
                    }
                }
                if (!isExistConnectedDevice) {
                    builder.setPositiveButton(R.string.hotspot_whitelist_add, addWhiteListListener);
                }
            } else {
                builder.setTitle(stationNameTemp)
                      .setMessage("IP: "+stationIP+"\nMAC: "+stationMac)
                      .setNegativeButton(R.string.block, controlBlockListener)
                      .setNeutralButton(R.string.hotspot_whitelist_cancel, null);
            }
        } else {
            if (supportWhitelist) {
                List<String> mWhitelistStationsDetail = mWifiManager.softApGetClientWhiteList();
                boolean isExistBlockDevice = false;
                builder.setTitle(stationNameTemp)
                      .setMessage("MAC: "+stationMac)
                      .setNegativeButton(R.string.unblock, controlBlockListener)
                      .setNeutralButton(R.string.hotspot_whitelist_cancel, null);
                for (String mWhitelistStationsStr : mWhitelistStationsDetail) {
                    String[] mWhitelistStations = mWhitelistStationsStr.split(" ");
                    int len = mWhitelistStations[0].length();
                    if (mWhitelistStations.length >= 2 && (mWhitelistStationsStr.substring(len+1).contains(stationNameTemp)) &&
                            (mWhitelistStations[0].contains(stationMac))) {
                        isExistBlockDevice = true;
                    }
                }
                if (!isExistBlockDevice) {
                    builder.setPositiveButton(R.string.hotspot_whitelist_add, addWhiteListListener);
                }
            } else {
                builder.setTitle(stationNameTemp)
                      .setMessage("MAC: "+stationMac)
                      .setNegativeButton(R.string.unblock, controlBlockListener)
                      .setNeutralButton(R.string.hotspot_whitelist_cancel, null);
            }
        }
        if (builder != null) {
            dialog = builder.create();
            dialog.show();
        }
    }

    android.content.DialogInterface.OnClickListener addWhiteListListener  = new android.content.DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // TODO Auto-generated method stub
            if (which == DialogInterface.BUTTON_POSITIVE) {
                List<String> mWhitelistStations = mWifiManager.softApGetClientWhiteList();
                if (mWhitelistStations.size() < DEFAULT_LIMIT) {
                    mWifiManager.softApAddClientToWhiteList(stationMac, stationName);
                } else {
                    String error = "null";
                    if (mContext != null) {
                        error = String.format(mContext.getString(R.string.wifi_add_whitelist_limit_error), DEFAULT_LIMIT);
                    }
                    Toast.makeText(mContext, error, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    android.content.DialogInterface.OnClickListener removeWhiteListListener  = new android.content.DialogInterface.OnClickListener()
{
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // TODO Auto-generated method stub
            if (which == DialogInterface.BUTTON_POSITIVE) {
                offWhiteButton();
            }
        }
    };

    android.content.DialogInterface.OnClickListener controlBlockListener  = new android.content.DialogInterface.OnClickListener
()
{
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // TODO Auto-generated method stub
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                setBlockButton();
            }
        }
    };

    void offWhiteButton() {
       mWifiManager.softApDelClientFromWhiteList(stationMac,stationName);
    }
    void setBlockButton() {
        if (isConnected) {
            List<String> mBlockedStationsDetail = mWifiManager.softApGetBlockedStationsDetail();
            if (mBlockedStationsDetail.size() >= DEFAULT_LIMIT) {
                Toast.makeText(mContext, R.string.hotspot_add_blockedstations_limit, Toast.LENGTH_SHORT).show();
                return;
            }
            mWifiManager.softApBlockStation(stationMac);
        } else {
            mWifiManager.softApUnblockStation(stationMac);
        }
    }

}

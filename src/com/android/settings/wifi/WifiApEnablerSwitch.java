/*
   Copyright (c) 2014, The Linux Foundation. All Rights Reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.android.settings.wifi;

import com.android.settings.R;
import com.android.settings.WirelessSettings;
import com.android.settings.HotspotPreference;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class WifiApEnablerSwitch {

    private static final int WIFI_STATE_ENABLE_ENABLING = 1;
    private static final int WIFI_STATE_DISABLE_DISABLING = 0;
    private static final String TAG = "WifiApEnablerSwitch";

    private final Context mContext;
    private final HotspotPreference mCheckBox;
    private final CharSequence mOriginalSummary;

    private WifiManager mWifiManager;
    private final IntentFilter mIntentFilter;

    ConnectivityManager mCm;
    private String[] mWifiRegexs;
    /* Indicates if we have to wait for WIFI_STATE_CHANGED intent */
    private boolean mWaitForWifiStateChange;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiApStateChanged(intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED));
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                if (mWaitForWifiStateChange) {
                    handleWifiStateChanged(intent.getIntExtra(
                            WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
                }
            } else if (ConnectivityManager.ACTION_TETHER_STATE_CHANGED.equals(action)) {
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                updateTetherState(available.toArray(), active.toArray(), errored.toArray());
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                enableWifiCheckBox();
            }

        }
    };

    public WifiApEnablerSwitch(Context context, HotspotPreference checkBox) {
        mContext = context;
        mCheckBox = checkBox;
        mOriginalSummary = checkBox.getSummary();
        checkBox.setPersistent(false);
        mWaitForWifiStateChange = true;

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mWifiRegexs = mCm.getTetherableWifiRegexs();

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        enableWifiCheckBox();
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
    }

    private void enableWifiCheckBox() {
        boolean isAirplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        mCheckBox.setEnabled(!isAirplaneMode);
        if (isAirplaneMode) {
            mCheckBox.setSummary(mOriginalSummary);
        }
    }

    public void setSoftapEnabled(boolean enable) {
        if (enable) {
            if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING ||
                    mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                /**
                 * Disable Wifi if enabling tethering
                 */
                mWifiManager.setWifiEnabled(false);
                WifiApSwitch.setWifiSavedState(mContext, WIFI_STATE_ENABLE_ENABLING);
            }
            if (mWifiManager.setWifiApEnabled(null, enable)) {
                /* Disable here, enabled on receiving success broadcast */
                mCheckBox.setEnabled(false);
            } else {
                mCheckBox.setSummary(R.string.wifi_error);
            }
        } else {
            /**
             * Check if we have to wait for the WIFI_STATE_CHANGED intent before
             * we re-enable the Checkbox.
             */
            mWaitForWifiStateChange = false;

            if (mWifiManager.setWifiApEnabled(null, enable)) {
                /* Disable here, enabled on receiving success broadcast */
                mCheckBox.setEnabled(false);
            } else {
                mCheckBox.setSummary(R.string.wifi_error);
            }

            /**
             * If needed, restore Wifi on tether disable
             */
            if (WifiApSwitch.getWifiSavedState(mContext) == WIFI_STATE_ENABLE_ENABLING) {
                mWaitForWifiStateChange = true;
                mWifiManager.setWifiEnabled(true);
                WifiApSwitch.setWifiSavedState(mContext, WIFI_STATE_DISABLE_DISABLING);
            }
        }
    }

    private boolean isWifiTethered(Object[] tethered) {
        for (Object o : tethered) {
            String s = (String) o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isWifiApErrored(Object[] errored) {
        for (Object o : errored) {
            String s = (String) o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getSummary(Object[] available, Object[] tethered, Object[] errored) {
        if (isWifiTethered(tethered)) {
            WifiConfiguration config = mWifiManager.getWifiApConfiguration();
            if (config == null) {
                return String.format(mContext.getString(R.string.wifi_tether_enabled_subtext),
                        mContext.getString(
                                com.android.internal.R.string.wifi_tether_configure_ssid_default));
            } else {
                return String.format(
                        mContext.getString(R.string.wifi_tether_enabled_subtext), config.SSID);
            }
        } else if (isWifiApErrored(errored)) {
            return mContext.getString(R.string.wifi_error);
        }
        return null;
    }

    private void updateTetherState(Object[] available, Object[] tethered, Object[] errored) {
        mCheckBox.setSummary(getSummary(available, tethered, errored));
    }

    private void handleWifiApStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                mCheckBox.setSummary(R.string.wifi_tether_starting);
                mCheckBox.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                /**
                 * Summary on enable is handled by tether broadcast notice
                 */
                mCheckBox.setChecked(true);
                /* Doesnt need the airplane check */
                mCheckBox.setEnabled(true);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mCheckBox.setSummary(R.string.wifi_tether_stopping);
                mCheckBox.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mCheckBox.setChecked(false);
                mCheckBox.setSummary(mOriginalSummary);
                if (!mWaitForWifiStateChange) {
                    enableWifiCheckBox();
                }
                break;
            default:
                mCheckBox.setChecked(false);
                mCheckBox.setSummary(R.string.wifi_error);
                enableWifiCheckBox();
        }
    }

    private void handleWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLED:
            case WifiManager.WIFI_STATE_UNKNOWN:
                enableWifiCheckBox();
                break;
            default:
        }
    }
}

/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.settings.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiWatchdogStateMachine;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.security.Credentials;
import android.text.format.Formatter;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

public class AdvancedWifiSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "AdvancedWifiSettings";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_CURRENT_IP_ADDRESS = "current_ip_address";
    private static final String KEY_FREQUENCY_BAND = "frequency_band";
    private static final String KEY_NOTIFY_OPEN_NETWORKS = "notify_open_networks";
    private static final String KEY_SLEEP_POLICY = "sleep_policy";
    private static final String KEY_POOR_NETWORK_DETECTION = "wifi_poor_network_detection";
    private static final String KEY_SCAN_ALWAYS_AVAILABLE = "wifi_scan_always_available";
    private static final String KEY_INSTALL_CREDENTIALS = "install_credentials";
    private static final String KEY_SUSPEND_OPTIMIZATIONS = "suspend_optimizations";
    private static final String KEY_AUTO_CONNECT_TYPE = "auto_connect_type";
    private static final String KEY_SELECT_IN_SSIDS_TYPE = "select_in_ssids_type";
    private static final String KEY_PRIORITY_TYPE = "wifi_priority_type";
    private static final String KEY_PRIORITY_SETTINGS = "wifi_priority_settings";

    private static final String KEY_CURRENT_GATEWAY = "current_gateway";
    private static final String KEY_CURRENT_NETMASK = "current_netmask";
    private static final String KEY_GSM_WIFI_CONNECT_TYPE = "gsm_wifi_connect_type";
    private static final String KEY_WIFI_GSM_CONNECT_TYPE = "wifi_gsm_connect_type";
    private static final String KEY_CONNECT_CMCC_NOTIFY = "notify_cmcc_ap_connected";

    private static final String KEY_CELLULAR_WLAN_HINT = "cellular_wlan_need_hint";
    private static final String CELLULAR_WLAN_HINT = "cellular_wlan_need_hint";

    private static final int NOTIFY_USER = 0;
    private static final int DO_NOT_NOTIFY_USER = -1;

    private WifiManager mWifiManager;

    private CheckBoxPreference AutoPref;
    private ListPreference mCellularToWlanPref;
    CheckBoxPreference mCellularToWlanHintPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wifi_advanced_settings);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();
        initPreferences();
        refreshWifiInfo();
    }

    private void initPreferences() {
        CheckBoxPreference notifyOpenNetworks =
            (CheckBoxPreference) findPreference(KEY_NOTIFY_OPEN_NETWORKS);
        notifyOpenNetworks.setChecked(Settings.Global.getInt(getContentResolver(),
                Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, 0) == 1);
        notifyOpenNetworks.setEnabled(mWifiManager.isWifiEnabled());

        CheckBoxPreference poorNetworkDetection =
            (CheckBoxPreference) findPreference(KEY_POOR_NETWORK_DETECTION);
        if (poorNetworkDetection != null) {
            if (Utils.isWifiOnly(getActivity())) {
                getPreferenceScreen().removePreference(poorNetworkDetection);
            } else {
                poorNetworkDetection.setChecked(Global.getInt(getContentResolver(),
                        Global.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED,
                        WifiWatchdogStateMachine.DEFAULT_POOR_NETWORK_AVOIDANCE_ENABLED ?
                        1 : 0) == 1);
            }
        }

        CheckBoxPreference scanAlwaysAvailable =
            (CheckBoxPreference) findPreference(KEY_SCAN_ALWAYS_AVAILABLE);
        scanAlwaysAvailable.setChecked(Global.getInt(getContentResolver(),
                    Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0) == 1);

        Intent intent=new Intent(Credentials.INSTALL_AS_USER_ACTION);
        intent.setClassName("com.android.certinstaller",
                "com.android.certinstaller.CertInstallerMain");
        intent.putExtra(Credentials.EXTRA_INSTALL_AS_UID, android.os.Process.WIFI_UID);
        Preference pref = findPreference(KEY_INSTALL_CREDENTIALS);
        pref.setIntent(intent);

        CheckBoxPreference suspendOptimizations =
            (CheckBoxPreference) findPreference(KEY_SUSPEND_OPTIMIZATIONS);
        suspendOptimizations.setChecked(Global.getInt(getContentResolver(),
                Global.WIFI_SUSPEND_OPTIMIZATIONS_ENABLED, 1) == 1);

        ListPreference frequencyPref = (ListPreference) findPreference(KEY_FREQUENCY_BAND);

        if (mWifiManager.isDualBandSupported()) {
            frequencyPref.setOnPreferenceChangeListener(this);
            int value = mWifiManager.getFrequencyBand();
            if (value != -1) {
                frequencyPref.setValue(String.valueOf(value));
                updateFrequencyBandSummary(frequencyPref, value);
            } else {
                Log.e(TAG, "Failed to fetch frequency band");
            }
        } else {
            if (frequencyPref != null) {
                // null if it has already been removed before resume
                getPreferenceScreen().removePreference(frequencyPref);
            }
        }

        ListPreference sleepPolicyPref = (ListPreference) findPreference(KEY_SLEEP_POLICY);
        if (sleepPolicyPref != null) {
            if (Utils.isWifiOnly(getActivity())) {
                sleepPolicyPref.setEntries(R.array.wifi_sleep_policy_entries_wifi_only);
            }
            sleepPolicyPref.setOnPreferenceChangeListener(this);
            int value = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.WIFI_SLEEP_POLICY,
                    Settings.Global.WIFI_SLEEP_POLICY_NEVER);
            String stringValue = String.valueOf(value);
            sleepPolicyPref.setValue(stringValue);
            updateSleepPolicySummary(sleepPolicyPref, stringValue);
        }

        AutoPref = (CheckBoxPreference) findPreference(KEY_AUTO_CONNECT_TYPE);
        if (AutoPref != null) {
            if (getResources().getBoolean(R.bool.config_auto_connect_wifi_enabled)) {
                AutoPref.setChecked(Settings.System.getInt(getContentResolver(),
                        getResources().getString(R.string.wifi_autoconn_type),
                        getResources().getInteger(R.integer.wifi_autoconn_type_auto)) ==
                        getResources().getInteger(R.integer.wifi_autoconn_type_auto));
                AutoPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(AutoPref);
            }
        } else {
            Log.d(TAG, "Fail to get auto connect pref");
        }

        String data2wifiKey = getActivity().getString(R.string.data_to_wifi_connect_key);
        String data2wifiValueAuto = getActivity().getString(R.string.data_to_wifi_connect_value_auto);
        mCellularToWlanPref = (ListPreference) findPreference(KEY_GSM_WIFI_CONNECT_TYPE);

        if (mCellularToWlanPref != null) {
            if (getResources().getBoolean(R.bool.cell_to_wifi)) {
                int value = Settings.System.getInt(getContentResolver(), data2wifiKey,
                        Integer.parseInt(data2wifiValueAuto));
                mCellularToWlanPref.setValue(String.valueOf(value));
                mCellularToWlanPref.setOnPreferenceChangeListener(this);
                updateCellToWlanSummary(mCellularToWlanPref, value);
            } else {
                getPreferenceScreen().removePreference(mCellularToWlanPref);
            }
        } else {
            Log.d(TAG, "Fail to get cellular2wifi pref");
        }

        CheckBoxPreference wifi2cellPref =
                (CheckBoxPreference) findPreference(KEY_WIFI_GSM_CONNECT_TYPE);
        if (wifi2cellPref != null) {
            if (getResources().getBoolean(R.bool.wifi_to_cell)) {
                wifi2cellPref.setChecked(Settings.System.getInt(getContentResolver(),
                        getResources().getString(R.string.wifi2cell_connect_type),
                        getResources().getInteger(R.integer.wifi2cell_connect_type_ask))
                        == getResources().getInteger(R.integer.wifi2cell_connect_type_ask));
                wifi2cellPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(wifi2cellPref);
            }
        } else {
            Log.d(TAG, "Fail to get wifi2cell pref");
        }

        String selectBySSIDKey = getActivity().getString(R.string.select_in_ssids_key);
        String selectBySSIDValueAsk = getActivity().getString(R.string.select_in_ssids_value_ask);
        ListPreference ssidPref = (ListPreference) findPreference(KEY_SELECT_IN_SSIDS_TYPE);
        if (ssidPref != null) {
            if (getResources().getBoolean(R.bool.config_wifi_connect_mode_among_ssid)) {
                int value = Settings.System.getInt(getContentResolver(), selectBySSIDKey,
                        Integer.parseInt(selectBySSIDValueAsk));
                ssidPref.setValue(String.valueOf(value));
                ssidPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(ssidPref);
            }
        } else {
            Log.d(TAG, "Fail to get ssid pref");
        }

        CheckBoxPreference priorityTypePref = (CheckBoxPreference) findPreference(KEY_PRIORITY_TYPE);
        Preference prioritySettingPref = findPreference(KEY_PRIORITY_SETTINGS);
        if (priorityTypePref != null && prioritySettingPref != null) {
            getPreferenceScreen().removePreference(priorityTypePref);
            if (!getResources().getBoolean(R.bool.show_ap_priority_settings)) {
                getPreferenceScreen().removePreference(prioritySettingPref);
            }
        } else {
            Log.d(TAG, "Fail to get priority pref...");
        }

        CheckBoxPreference notifyConnectedCmccApPref =
                (CheckBoxPreference) findPreference(KEY_CONNECT_CMCC_NOTIFY);
        if (notifyConnectedCmccApPref != null) {
            if (getResources().getBoolean(R.bool.connect_to_cmcc_ap_prompt)) {
                notifyConnectedCmccApPref.setChecked(Settings.System.getInt(getContentResolver(),
                        getResources().getString(R.string.prompt_when_connect_cmcc),
                        getResources().getInteger(R.integer.notify_user_to_connect_cmcc))
                        == getResources().getInteger(R.integer.notify_user_to_connect_cmcc));
                notifyConnectedCmccApPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(notifyConnectedCmccApPref);
            }
        } else {
            Log.d(TAG, "Fail to get cmcc wlan prompt pref");
        }

        mCellularToWlanHintPref = (CheckBoxPreference) findPreference(KEY_CELLULAR_WLAN_HINT);
        if (mCellularToWlanHintPref != null) {
            if (getResources().getBoolean(R.bool.cellular_wlan_hint)) {
                mCellularToWlanHintPref.setChecked(isCellularToWlanHintEnable());
                mCellularToWlanHintPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(mCellularToWlanHintPref);
            }
        }
    }

    private boolean isCellularToWlanHintEnable() {
        return Settings.System.getInt(getActivity().getContentResolver(),
                CELLULAR_WLAN_HINT, NOTIFY_USER) == NOTIFY_USER;
    }

    private void updateSleepPolicySummary(Preference sleepPolicyPref, String value) {
        if (value != null) {
            String[] values = getResources().getStringArray(R.array.wifi_sleep_policy_values);
            final int summaryArrayResId = Utils.isWifiOnly(getActivity()) ?
                    R.array.wifi_sleep_policy_entries_wifi_only : R.array.wifi_sleep_policy_entries;
            String[] summaries = getResources().getStringArray(summaryArrayResId);
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    if (i < summaries.length) {
                        sleepPolicyPref.setSummary(summaries[i]);
                        return;
                    }
                }
            }
        }

        sleepPolicyPref.setSummary("");
        Log.e(TAG, "Invalid sleep policy value: " + value);
    }

    private void updateCellToWlanSummary(Preference preference, int index) {
        String[] summaries = getResources().getStringArray(R.array.cellular_to_wlan_type_entries);
        preference.setSummary(summaries[index]);
    }

    private void updateFrequencyBandSummary(Preference frequencyBandPref, int index) {
        String[] summaries = getResources().getStringArray(R.array.wifi_frequency_band_entries);
        frequencyBandPref.setSummary(summaries[index]);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        String key = preference.getKey();

        if (KEY_NOTIFY_OPEN_NETWORKS.equals(key)) {
            Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_POOR_NETWORK_DETECTION.equals(key)) {
            Global.putInt(getContentResolver(),
                    Global.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_SUSPEND_OPTIMIZATIONS.equals(key)) {
            Global.putInt(getContentResolver(),
                    Global.WIFI_SUSPEND_OPTIMIZATIONS_ENABLED,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_SCAN_ALWAYS_AVAILABLE.equals(key)) {
            Global.putInt(getContentResolver(),
                    Global.WIFI_SCAN_ALWAYS_AVAILABLE,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        } else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (KEY_FREQUENCY_BAND.equals(key)) {
            try {
                int value = Integer.parseInt((String) newValue);
                mWifiManager.setFrequencyBand(value, true);
                updateFrequencyBandSummary(preference, value);
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.wifi_setting_frequency_band_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if (KEY_SLEEP_POLICY.equals(key)) {
            try {
                String stringValue = (String) newValue;
                Settings.Global.putInt(getContentResolver(), Settings.Global.WIFI_SLEEP_POLICY,
                        Integer.parseInt(stringValue));
                updateSleepPolicySummary(preference, stringValue);
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.wifi_setting_sleep_policy_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        if (KEY_WIFI_GSM_CONNECT_TYPE.equals(key)) {
            Log.d(TAG, "wifi2cell connect type is " + newValue);
            boolean checked = ((Boolean) newValue).booleanValue();
            Settings.System.putInt(getContentResolver(),
                    getResources().getString(R.string.wifi2cell_connect_type),
                    checked ? getResources().getInteger(R.integer.wifi2cell_connect_type_ask)
                            : getResources().getInteger(R.integer.wifi2cell_connect_type_auto));
        }

        if (KEY_AUTO_CONNECT_TYPE.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            Settings.System.putInt(getContentResolver(),
                    getResources().getString(R.string.wifi_autoconn_type),
                    checked ? getResources().getInteger(R.integer.wifi_autoconn_type_auto)
                            : getResources().getInteger(R.integer.wifi_autoconn_type_manual));

            String data2wifiValueAuto = getActivity().getString(
                    R.string.data_to_wifi_connect_value_auto);
            String data2wifiValueManually = getActivity().getString(
                    R.string.data_to_wifi_connect_value_manual);

            if (checked && !mCellularToWlanPref.getValue().equals(data2wifiValueAuto)) {
                mCellularToWlanPref.setValue(data2wifiValueAuto);
            } else if (!checked && mCellularToWlanPref.getValue().equals(data2wifiValueAuto)) {
                mCellularToWlanPref.setValue(data2wifiValueManually);
                mCellularToWlanHintPref.setChecked(true);
            }

            int value = Integer.parseInt(mCellularToWlanPref.getValue());
            Settings.System.putInt(getContentResolver(),
                    getActivity().getString(R.string.data_to_wifi_connect_key),
                    value);
            updateCellToWlanSummary(mCellularToWlanPref, value);
        }

        if (KEY_GSM_WIFI_CONNECT_TYPE.equals(key)) {
            Log.d(TAG, "Gsm to Wifi connect type is " + newValue);
            try {
                int value = Integer.parseInt(((String) newValue));
                String data2wifiKey = getActivity().getString(R.string.data_to_wifi_connect_key);
                Settings.System.putInt(getContentResolver(), data2wifiKey,
                        value);
                mCellularToWlanPref.setValue(String.valueOf(value));
                updateCellToWlanSummary(mCellularToWlanPref, value);

                String data2wifiValueAuto = getActivity().getString(
                        R.string.data_to_wifi_connect_value_auto);
                if (((String) newValue).equals(data2wifiValueAuto) && !AutoPref.isChecked()) {
                    AutoPref.setChecked(true);
                } else if (!((String) newValue).equals(data2wifiValueAuto) &&
                        AutoPref.isChecked()) {
                    AutoPref.setChecked(false);
                }
                if (!((String) newValue).equals(data2wifiValueAuto)
                        && !mCellularToWlanHintPref.isChecked()) {
                    mCellularToWlanHintPref.setChecked(true);
                }
                Settings.System.putInt(
                        getContentResolver(),
                        getResources().getString(R.string.wifi_autoconn_type),
                        AutoPref.isChecked() ? getResources().getInteger(
                                R.integer.wifi_autoconn_type_auto)
                                : getResources().getInteger(R.integer.wifi_autoconn_type_manual));
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.wifi_setting_connect_type_error,
                        Toast.LENGTH_SHORT).show();
            }
        }

        if (KEY_SELECT_IN_SSIDS_TYPE.equals(key)) {
            try {
                String selectBySSIDKey = getActivity().getString(R.string.select_in_ssids_key);
                Settings.System.putInt(getContentResolver(), selectBySSIDKey,
                        Integer.parseInt(((String) newValue)));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (KEY_PRIORITY_TYPE.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            Settings.System.putInt(getContentResolver(),
                    getResources().getString(R.string.wifi_priority_type),
                    checked ? getResources().getInteger(R.integer.wifi_priority_type_manual)
                            : getResources().getInteger(R.integer.wifi_priority_type_default));
        }

        if (KEY_CONNECT_CMCC_NOTIFY.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            Settings.System.putInt(getContentResolver(),
                    getResources().getString(R.string.prompt_when_connect_cmcc),
                    checked ? getResources().getInteger(R.integer.notify_user_to_connect_cmcc)
                            : getResources().getInteger(
                                    R.integer.do_not_notify_user_to_connect_cmcc));
        }

        if (KEY_CELLULAR_WLAN_HINT.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            setCellularToWlanHintEnable(checked);
            if (!checked) {
                Toast.makeText(getActivity(), R.string.cellular_wlan_hint_toast,
                        Toast.LENGTH_LONG).show();
            }
        }
        return true;
    }

    private void setCellularToWlanHintEnable(boolean needNotify) {
        if (needNotify) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    CELLULAR_WLAN_HINT, NOTIFY_USER);
        } else {
            Settings.System.putInt(getActivity().getContentResolver(),
                    CELLULAR_WLAN_HINT, DO_NOT_NOTIFY_USER);
        }
    }

    private void refreshWifiInfo() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        Preference wifiMacAddressPref = findPreference(KEY_MAC_ADDRESS);
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        wifiMacAddressPref.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress
                : getActivity().getString(R.string.status_unavailable));

        Preference wifiIpAddressPref = findPreference(KEY_CURRENT_IP_ADDRESS);
        String ipAddress = Utils.getWifiIpAddresses(getActivity());
        wifiIpAddressPref.setSummary(ipAddress == null ?
                getActivity().getString(R.string.status_unavailable) : ipAddress);
        Preference wifiGatewayPref = findPreference(KEY_CURRENT_GATEWAY);
        String gateway = null;
        Preference wifiNetmaskPref = findPreference(KEY_CURRENT_NETMASK);
        String netmask = null;
        if (getResources().getBoolean(R.bool.config_netinfo)) {
            DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();
            if (wifiInfo != null) {
                if (dhcpInfo != null) {
                    gateway = Formatter.formatIpAddress(dhcpInfo.gateway);
                    netmask = Formatter.formatIpAddress(dhcpInfo.netmask);
                }
            }
            if (wifiGatewayPref != null) {
                wifiGatewayPref.setSummary(gateway == null ?
                        getString(R.string.status_unavailable) : gateway);
            }
            if (wifiNetmaskPref != null) {
                wifiNetmaskPref.setSummary(netmask == null ?
                        getString(R.string.status_unavailable) : netmask);
            }
        } else {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen != null) {
                if (wifiGatewayPref != null) {
                    screen.removePreference(wifiGatewayPref);
                }
                if (wifiNetmaskPref != null) {
                    screen.removePreference(wifiNetmaskPref);
                }
            }
        }
    }

}

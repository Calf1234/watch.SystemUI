/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.app.AlarmManager;
import android.app.StatusBarManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.telecom.TelecomManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.CastController.CastDevice;
import com.android.systemui.statusbar.policy.HotspotController;

/**
 * This class contains all of the policy about which icons are installed in the status
 * bar at boot time.  It goes through the normal API for icons, even though it probably
 * strictly doesn't need to.
 */
public class PhoneStatusBarPolicy {
    private static final String TAG = "PhoneStatusBarPolicy";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final boolean SHOW_SYNC_ICON = false;

    private static final String SLOT_SYNC_ACTIVE = "sync_active";
    private static final String SLOT_CAST = "cast";
    private static final String SLOT_HOTSPOT = "hotspot";
    private static final String SLOT_BLUETOOTH = "bluetooth";
    private static final String SLOT_TTY = "tty";
    private static final String SLOT_ZEN = "zen";
    private static final String SLOT_VOLUME = "volume";
    private static final String SLOT_CDMA_ERI = "cdma_eri";
    private static final String SLOT_ALARM_CLOCK = "alarm_clock";
    private static final String SLOT_HEADSET = "headset";

    private final Context mContext;
    private final StatusBarManager mService;
    private final Handler mHandler = new Handler();
    private final CastController mCast;
    private final HotspotController mHotspot;

    private static final int UPDATE_BT_CONNECTING_ICON = 6;
    private static final boolean UXFEATURE ="ux".equals(SystemProperties.get("ro.sf3g.feature"));
    private boolean mBTConnecting= false;
    private String  contentDescriptionUX = null;

    // Assume it's all good unless we hear otherwise.  We don't always seem
    // to get broadcasts that it *is* there.
    IccCardConstants.State mSimState = IccCardConstants.State.READY;

    private boolean mZenVisible;
    private boolean mVolumeVisible;

    private int mZen;

    private boolean mBluetoothEnabled = false;


    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)) {
                updateAlarm();
            }
            else if (action.equals(Intent.ACTION_SYNC_STATE_CHANGED)) {
                updateSyncState(intent);
            }
            else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) ||
                    action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                if(!UXFEATURE){
                    updateBluetooth();
                }else{
                    updateBluetoothUX(intent);
                }
            }
            else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION) ||
                    action.equals(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION)) {
                updateVolumeZen();
            }
            else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                updateSimState(intent);
            }
            else if (action.equals(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED)) {
                updateTTY(intent);
            }
            else if (action.equals(Intent.ACTION_USER_SWITCHED)) {
                updateAlarm();
            }
            else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                updateHeadset(intent);
            }
        }
    };

    public PhoneStatusBarPolicy(Context context, CastController cast, HotspotController hotspot) {
        mContext = context;
        mCast = cast;
        mHotspot = hotspot;
        mService = (StatusBarManager)context.getSystemService(Context.STATUS_BAR_SERVICE);

        // listen for broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        filter.addAction(Intent.ACTION_SYNC_STATE_CHANGED);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mIntentReceiver, filter, null, mHandler);

        // TTY status
        mService.setIcon(SLOT_TTY,  R.drawable.stat_sys_tty_mode, 0, null);
        mService.setIconVisibility(SLOT_TTY, false);

        // Cdma Roaming Indicator, ERI
        mService.setIcon(SLOT_CDMA_ERI, R.drawable.stat_sys_roaming_cdma_0, 0, null);
        mService.setIconVisibility(SLOT_CDMA_ERI, false);

        // bluetooth status
        updateBluetooth();

        // Alarm clock
        mService.setIcon(SLOT_ALARM_CLOCK, R.drawable.stat_sys_alarm, 0, null);
        mService.setIconVisibility(SLOT_ALARM_CLOCK, false);

        // Sync state
        mService.setIcon(SLOT_SYNC_ACTIVE, R.drawable.stat_sys_sync, 0, null);
        mService.setIconVisibility(SLOT_SYNC_ACTIVE, false);
        // "sync_failing" is obsolete: b/1297963

        // zen
        mService.setIcon(SLOT_ZEN, R.drawable.stat_sys_zen_important, 0, null);
        mService.setIconVisibility(SLOT_ZEN, false);

        // volume
        mService.setIcon(SLOT_VOLUME, R.drawable.stat_sys_ringer_vibrate, 0, null);
        mService.setIconVisibility(SLOT_VOLUME, false);
        updateVolumeZen();

        // cast
        mService.setIcon(SLOT_CAST, R.drawable.stat_sys_cast, 0, null);
        mService.setIconVisibility(SLOT_CAST, false);
        mCast.addCallback(mCastCallback);

        // hotspot
        mService.setIcon(SLOT_HOTSPOT, R.drawable.stat_sys_hotspot, 0, null);
        mService.setIconVisibility(SLOT_HOTSPOT, mHotspot.isHotspotEnabled());
        mHotspot.addCallback(mHotspotCallback);

        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mService.setIcon(SLOT_HEADSET, android.R.drawable.stat_sys_headset, 0, null);
        mService.setIconVisibility(SLOT_HEADSET, audioManager.isWiredHeadsetOn());
    }

    public void setZenMode(int zen) {
        mZen = zen;
        updateVolumeZen();
    }

    private void updateAlarm() {
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        boolean alarmSet = alarmManager.getNextAlarmClock(UserHandle.USER_CURRENT) != null;
        mService.setIconVisibility(SLOT_ALARM_CLOCK, alarmSet);
    }

    private final void updateSyncState(Intent intent) {
        if (!SHOW_SYNC_ICON) return;
        boolean isActive = intent.getBooleanExtra("active", false);
        mService.setIconVisibility(SLOT_SYNC_ACTIVE, isActive);
    }

    private final void updateSimState(Intent intent) {
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            mSimState = IccCardConstants.State.ABSENT;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR.equals(stateExtra)) {
            mSimState = IccCardConstants.State.CARD_IO_ERROR;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            mSimState = IccCardConstants.State.READY;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason =
                    intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
            if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PIN_REQUIRED;
            }
            else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PUK_REQUIRED;
            }
            else {
                mSimState = IccCardConstants.State.NETWORK_LOCKED;
            }
        } else {
            mSimState = IccCardConstants.State.UNKNOWN;
        }
    }

    private final void updateVolumeZen() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        boolean zenVisible = false;
        int zenIconId = 0;
        String zenDescription = null;

        boolean volumeVisible = false;
        int volumeIconId = 0;
        String volumeDescription = null;

        if (mZen == Global.ZEN_MODE_NO_INTERRUPTIONS) {
            zenVisible = true;
            zenIconId = R.drawable.stat_sys_zen_none;
            zenDescription = mContext.getString(R.string.zen_no_interruptions);
        } else if (mZen == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS) {
            zenVisible = true;
            zenIconId = R.drawable.stat_sys_zen_important;
            zenDescription = mContext.getString(R.string.zen_important_interruptions);
        }

        if (mZen != Global.ZEN_MODE_NO_INTERRUPTIONS &&
                audioManager.getRingerModeInternal() == AudioManager.RINGER_MODE_VIBRATE) {
            volumeVisible = true;
            volumeIconId = R.drawable.stat_sys_ringer_vibrate;
            volumeDescription = mContext.getString(R.string.accessibility_ringer_vibrate);
        }

        if (zenVisible) {
            mService.setIcon(SLOT_ZEN, zenIconId, 0, zenDescription);
        }
        if (zenVisible != mZenVisible) {
            mService.setIconVisibility(SLOT_ZEN, zenVisible);
            mZenVisible = zenVisible;
        }

        if (volumeVisible) {
            mService.setIcon(SLOT_VOLUME, volumeIconId, 0, volumeDescription);
        }
        if (volumeVisible != mVolumeVisible) {
            mService.setIconVisibility(SLOT_VOLUME, volumeVisible);
            mVolumeVisible = volumeVisible;
        }
    }

    private final void updateBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        int iconId = R.drawable.stat_sys_data_bluetooth;
        String contentDescription =
                mContext.getString(R.string.accessibility_bluetooth_disconnected);
        if (adapter != null) {
            mBluetoothEnabled = (adapter.getState() == BluetoothAdapter.STATE_ON);
            if (adapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED) {
                if(!UXFEATURE){
                    iconId = R.drawable.stat_sys_data_bluetooth_connected;
                    contentDescription = mContext.getString(R.string.accessibility_bluetooth_connected);
                }else{
                    contentDescriptionUX = mContext.getString(R.string.accessibility_bluetooth_connected);
                    updateBTConnectIcon(btHandler,0,false);
                }
            }
        } else {
            mBluetoothEnabled = false;
            if(UXFEATURE){
                contentDescriptionUX = contentDescription;
                updateBTConnectIcon(btHandler,1,false);
            }
        }
        if(!UXFEATURE){
            mService.setIcon(SLOT_BLUETOOTH, iconId, 0, contentDescription);
            mService.setIconVisibility(SLOT_BLUETOOTH, mBluetoothEnabled);
        }
    }

    private final void updateBluetoothUX(Intent intent) {
        int iconId = R.drawable.stat_sys_data_bluetooth;
        String contentDescription = null;
        String action = intent.getAction();
        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            mBluetoothEnabled = state == BluetoothAdapter.STATE_ON;
            updateBTConnectIcon(btHandler,1,false);
        } else if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                BluetoothAdapter.STATE_DISCONNECTED);
            if (state == BluetoothAdapter.STATE_CONNECTED) {
                updateBTConnectIcon(btHandler,0,false);
                contentDescriptionUX = mContext.getString(R.string.accessibility_bluetooth_connected);
            } else if (state == BluetoothAdapter.STATE_CONNECTING){
                updateBTConnectIcon(btHandler,0,true);
            } else {
                contentDescriptionUX = mContext.getString(
                        R.string.accessibility_bluetooth_disconnected);
                updateBTConnectIcon(btHandler,1,false);
            }
        } else {
            return;
        }

    }

    private Handler btHandler = new Handler() {
        public void handleMessage(Message msg1) {
           switch (msg1.what) {
                case UPDATE_BT_CONNECTING_ICON:
                    synchronized(btHandler){
                        int id = msg1.arg1;
                        btHandler.removeMessages(UPDATE_BT_CONNECTING_ICON);
                        Message msg2 = btHandler.obtainMessage(UPDATE_BT_CONNECTING_ICON);
                        if(id == 0){
                            msg2.arg1 = 1;
                            mService.setIcon(SLOT_BLUETOOTH, R.drawable.stat_sys_data_bluetooth_connected, 0, contentDescriptionUX);
                            mService.setIconVisibility(SLOT_BLUETOOTH, mBluetoothEnabled);
                        }else{
                            msg2.arg1 = 0;
                            mService.setIcon(SLOT_BLUETOOTH, R.drawable.stat_sys_data_bluetooth, 0, contentDescriptionUX);
                            mService.setIconVisibility(SLOT_BLUETOOTH, mBluetoothEnabled);
                        }
                        if(mBTConnecting){
                            btHandler.sendMessageDelayed(msg2, 500);
                        }
                     break;
                 }
                 default:
                     break;
           }
        }
    };

    private void updateBTConnectIcon(Handler mhandler,int arg,boolean connecting){
        mBTConnecting = connecting;
        mhandler.removeMessages(UPDATE_BT_CONNECTING_ICON);
        Message msg = mhandler.obtainMessage(UPDATE_BT_CONNECTING_ICON);
        msg.arg1 = arg;  //0:Bluetooth connected;1:Bluetooth not connected
        mhandler.sendMessage(msg);
    }

    private final void updateTTY(Intent intent) {
        int currentTtyMode = intent.getIntExtra(TelecomManager.EXTRA_CURRENT_TTY_MODE,
                TelecomManager.TTY_MODE_OFF);
        boolean enabled = currentTtyMode != TelecomManager.TTY_MODE_OFF;

        if (DEBUG) Log.v(TAG, "updateTTY: enabled: " + enabled);

        if (enabled) {
            // TTY is on
            if (DEBUG) Log.v(TAG, "updateTTY: set TTY on");
            mService.setIcon(SLOT_TTY, R.drawable.stat_sys_tty_mode, 0,
                    mContext.getString(R.string.accessibility_tty_enabled));
            mService.setIconVisibility(SLOT_TTY, true);
        } else {
            // TTY is off
            if (DEBUG) Log.v(TAG, "updateTTY: set TTY off");
            mService.setIconVisibility(SLOT_TTY, false);
        }
    }

    private final void updateHeadset(Intent intent) {
        final int state = intent.getIntExtra("state",0);
        mService.setIconVisibility(SLOT_HEADSET,(state != 0));
    }

    private void updateCast() {
        boolean isCasting = false;
        for (CastDevice device : mCast.getCastDevices()) {
            if (device.state == CastDevice.STATE_CONNECTING
                    || device.state == CastDevice.STATE_CONNECTED) {
                isCasting = true;
                break;
            }
        }
        if (DEBUG) Log.v(TAG, "updateCast: isCasting: " + isCasting);
        if (isCasting) {
            mService.setIcon(SLOT_CAST, R.drawable.stat_sys_cast, 0,
                    mContext.getString(R.string.accessibility_casting));
        }
        mService.setIconVisibility(SLOT_CAST, isCasting);
    }

    private final HotspotController.Callback mHotspotCallback = new HotspotController.Callback() {
        @Override
        public void onHotspotChanged(boolean enabled) {
            mService.setIconVisibility(SLOT_HOTSPOT, enabled);
        }
    };

    private final CastController.Callback mCastCallback = new CastController.Callback() {
        @Override
        public void onCastDevicesChanged() {
            updateCast();
        }
    };
}

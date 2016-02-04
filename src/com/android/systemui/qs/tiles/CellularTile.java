/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.os.SystemProperties;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import com.android.systemui.qs.SignalTileView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.MobileDataController;
import com.android.systemui.statusbar.policy.NetworkController.MobileDataController.DataUsageInfo;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

/** Quick settings tile: Cellular **/
public class CellularTile extends QSTile<QSTile.SignalState> {
    private static final Intent CELLULAR_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$DataUsageSummaryActivity"));

    private final NetworkController mController;
    private final MobileDataController mDataController;
    private final CellularDetailAdapter mDetailAdapter;
    private SubscriptionManager mSubscriptionManager;
    private int mSlotId = -1;

    private boolean mUxFlag;

    public CellularTile(Host host) {
        super(host);
        mController = host.getNetworkController();
        mDataController = mController.getMobileDataController();
        mDetailAdapter = new CellularDetailAdapter();
    }

    public CellularTile(Host host, int slotId) {
        super(host);
        mController = host.getNetworkController();
        mDataController = mController.getMobileDataController();
        mDetailAdapter = new CellularDetailAdapter();
        mSlotId = slotId;

        mSubscriptionManager = SubscriptionManager.from(mContext);
        mUxFlag = "ux".equals(SystemProperties.get("ro.sf3g.feature"));
    }

    @Override
    protected SignalState newTileState() {
        return new SignalState();
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addNetworkSignalChangedCallback(mCallback);
        } else {
            mController.removeNetworkSignalChangedCallback(mCallback);
        }
    }

    @Override
    public QSTileView createTileView(Context context) {
        return new SignalTileView(context);
    }

    @Override
    protected void handleClick() {
        int[] subId = SubscriptionManager.getSubId(mSlotId);
        if(!SubscriptionManager.isValidSubscriptionId(subId[0])) {
            // Do nothing as no SIM
            return;
        }

        if (mDataController.isMobileDataSupported()) {
            showDetail(true);
        } else {
            mHost.startSettingsActivity(CELLULAR_SETTINGS);
        }
    }

    @Override
    protected void handleUpdateState(SignalState state, Object arg) {
        state.visible = mController.hasMobileDataFeature();
        if (!state.visible) return;
        final CallbackInfo cb = (CallbackInfo) arg;
        if (cb == null) return;

        final Resources r = mContext.getResources();
        final int iconId = cb.noSim ? R.drawable.ic_qs_no_sim
                : !cb.enabled || cb.airplaneModeEnabled ? R.drawable.ic_qs_signal_disabled
                : cb.mobileSignalIconId > 0 ? cb.mobileSignalIconId
                : R.drawable.ic_qs_signal_no_signal;
        state.icon = ResourceIcon.get(iconId);
        state.isOverlayIconWide = cb.isDataTypeIconWide;
        state.autoMirrorDrawable = !cb.noSim;
        state.overlayIconId = cb.enabled && (cb.dataTypeIconId > 0) ? cb.dataTypeIconId : 0;
        state.filter = iconId != R.drawable.ic_qs_no_sim;
        state.activityIn = cb.enabled && cb.activityIn;
        state.activityOut = cb.enabled && cb.activityOut;

        state.label = cb.enabled
                ? removeTrailingPeriod(cb.enabledDesc)
                : r.getString(R.string.quick_settings_rssi_emergency_only);

        final String signalContentDesc = cb.enabled && (cb.mobileSignalIconId > 0)
                ? cb.signalContentDescription
                : r.getString(R.string.accessibility_no_signal);
        final String dataContentDesc = cb.enabled && (cb.dataTypeIconId > 0) && !cb.wifiEnabled
                ? cb.dataContentDescription
                : r.getString(R.string.accessibility_no_data);
        state.contentDescription = r.getString(
                R.string.accessibility_quick_settings_mobile,
                signalContentDesc, dataContentDesc,
                state.label);
        state.label = updateStateLabel(state.label);
    }
     private String updateStateLabel(String name){
        final Resources r = mContext.getResources();
        String providerName = name;
       if(name.contains("UNICOM")){
            providerName = r.getString(R.string.network_unicom);
       }else if(name.contains("MOBILE")){
           providerName =  r.getString(R.string.network_mobile);
       }else if(name.contains("CTNET")){
          providerName =  r.getString(R.string.network_ctnet);
       }
       return providerName;
    }


    // Remove the period from the network name
    public static String removeTrailingPeriod(String string) {
        if (string == null) return null;
        final int length = string.length();
        if (string.endsWith(".")) {
            return string.substring(0, length - 1);
        }
        return string;
    }

    private static final class CallbackInfo {
        boolean enabled;
        boolean wifiEnabled;
        boolean wifiConnected;
        boolean airplaneModeEnabled;
        int mobileSignalIconId;
        String signalContentDescription;
        int dataTypeIconId;
        String dataContentDescription;
        boolean activityIn;
        boolean activityOut;
        String enabledDesc;
        boolean noSim;
        boolean isDataTypeIconWide;
    }

    private final NetworkSignalChangedCallback mCallback = new NetworkSignalChangedCallback() {
        private final CallbackInfo mInfo = new CallbackInfo();

        @Override
        public void onWifiSignalChanged(boolean enabled, boolean connected, int wifiSignalIconId,
                boolean activityIn, boolean activityOut,
                String wifiSignalContentDescriptionId, String description) {
            mInfo.wifiEnabled = enabled;
            mInfo.wifiConnected = connected;
            refreshState(mInfo);
        }

        @Override
        public void onMobileDataSignalChanged(boolean enabled,
                int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description,
                boolean isDataTypeIconWide) {
            onMobileDataSignalChanged(enabled,
                    mobileSignalIconId,
                    mobileSignalContentDescriptionId, dataTypeIconId,
                    activityIn, activityOut,
                    dataTypeContentDescriptionId, description,
                    isDataTypeIconWide, -1);
        }

        @Override
        public void onMobileDataSignalChanged(boolean enabled,
                int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description,
                boolean isDataTypeIconWide, int slotId) {
            if (mSlotId == slotId) {
                mInfo.enabled = enabled;
                mInfo.mobileSignalIconId = mobileSignalIconId;
                mInfo.signalContentDescription = mobileSignalContentDescriptionId;
                mInfo.dataTypeIconId = dataTypeIconId;
                mInfo.dataContentDescription = dataTypeContentDescriptionId;
                mInfo.activityIn = activityIn;
                mInfo.activityOut = activityOut;
                mInfo.enabledDesc = description;
                mInfo.isDataTypeIconWide = isDataTypeIconWide;
                refreshState(mInfo);
            }
        }

        @Override
        public void onNoSimVisibleChanged(boolean visible) {
            onNoSimVisibleChanged(visible, -1);
        }

        @Override
        public void onNoSimVisibleChanged(boolean visible, int slotId) {
            if (mSlotId == slotId) {
                mInfo.noSim = visible;
                if (mInfo.noSim) {
                    // Make sure signal gets cleared out when no sims.
                    mInfo.mobileSignalIconId = 0;
                    mInfo.dataTypeIconId = 0;
                    // Show a No SIMs description to avoid emergency calls message.
                    mInfo.enabled = true;
                    mInfo.enabledDesc = mContext.getString(
                            R.string.keyguard_missing_sim_message_short);
                    mInfo.signalContentDescription = mInfo.enabledDesc;
                }
                refreshState(mInfo);
            }
        }

        @Override
        public void onAirplaneModeChanged(boolean enabled) {
            mInfo.airplaneModeEnabled = enabled;
            refreshState(mInfo);
        }

        public void onMobileDataEnabled(boolean enabled) {
            mDetailAdapter.setMobileDataEnabled(enabled);
        }
    };

    private final class CellularDetailAdapter implements DetailAdapter {
        private int mCurrentSubId = -1;
        private int mAnotherSubId = -1;
        private String mCarrierName;

        @Override
        public int getTitle() {
            int sourceId = 0;
            if (!mUxFlag) {
                return R.string.quick_settings_cellular_detail_title;
            }
            sourceId = (mSlotId == 0)
                    ? R.string.quick_settings_cellular_detail_title_slot_one
                    : R.string.quick_settings_cellular_detail_title_slot_two;
            int[] subIds = SubscriptionManager.getSubId(mSlotId);
            if (subIds != null && subIds.length > 0) {
                mCurrentSubId = subIds[0];
            }
            int anotherSlotId = (mSlotId == 0) ? 1 : 0;
            int[] anotherSubIds = SubscriptionManager.getSubId(anotherSlotId);
            if (anotherSubIds != null && anotherSubIds.length > 0) {
                mAnotherSubId = anotherSubIds[0];
            }
            SubscriptionManager subscriptionManager = SubscriptionManager.from(mContext);
            SubscriptionInfo subsm = subscriptionManager.getActiveSubscriptionInfo(mCurrentSubId);
            if (subsm != null) {
                mCarrierName = updateStateLabel((String) subsm.getCarrierName());
            }
            return sourceId;
        }

        @Override
        public Boolean getToggleState() {
            boolean ret = mUxFlag ? mDataController.isMobileDataEnabled(mCurrentSubId)
                    : mDataController.isMobileDataEnabled();
            return mDataController.isMobileDataSupported()
                    ? ret
                    : null;
        }

        @Override
        public Intent getSettingsIntent() {
            return CELLULAR_SETTINGS;
        }

        @Override
        public void setToggleState(boolean state) {
            if (mUxFlag) {
                try {
                    SubscriptionManager subscriptionManager = SubscriptionManager.from(mContext);
                    if( state && (mCurrentSubId != subscriptionManager.getDefaultDataSubId())){
                        subscriptionManager.setDefaultDataSubId( mCurrentSubId );
                    }
                } catch ( Exception e ) {
                }

                mDataController.setMobileDataEnabled(mCurrentSubId, state, true);
                if (state && mAnotherSubId != -1) {
                    mDataController.setMobileDataEnabled(mAnotherSubId, false, false);
                }
            } else {
                mDataController.setMobileDataEnabled(state);
            }
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final DataUsageDetailView v = (DataUsageDetailView) (convertView != null
                    ? convertView
                    : LayoutInflater.from(mContext).inflate(R.layout.data_usage, parent, false));
            DataUsageInfo info = null;
            if (mUxFlag) {
                info = mDataController.getDataUsageInfo(mCurrentSubId, mCarrierName);
            } else {
                info = mDataController.getDataUsageInfo();
            }
            if (info == null) return v;
            v.bind(info);
            return v;
        }

        public void setMobileDataEnabled(boolean enabled) {
            fireToggleStateChanged(enabled);
        }
    }
}

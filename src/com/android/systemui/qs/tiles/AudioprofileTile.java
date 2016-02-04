package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.telephony.SubscriptionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.SystemProperties;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import com.android.systemui.qs.SignalTileView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.MobileDataController;
import com.android.systemui.statusbar.policy.NetworkController.MobileDataController.DataUsageInfo;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;


/** Quick settings tile: Cellular **/
public class AudioprofileTile extends QSTile<QSTile.SignalState> {
    private static final Intent AUDIOPROFILE_SETTINGS = new Intent("android.settings.AUDIO_SETTINGS");
    private final NetworkController mController;
    private final MobileDataController mDataController;
    private final CellularDetailAdapter mDetailAdapter;
    private int mSlotId = -1;
    private boolean mUxFlag;

    public AudioprofileTile(Host host) {
        super(host);
        mController = host.getNetworkController();
        mDataController = mController.getMobileDataController();
        mDetailAdapter = new CellularDetailAdapter();
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
        mHost.startSettingsActivity(AUDIOPROFILE_SETTINGS);
    }

    @Override
    protected void handleUpdateState(SignalState state, Object arg) {
        state.visible = mController.hasMobileDataFeature();
        if (!state.visible) return;
        final CallbackInfo cb = (CallbackInfo) arg;
        if (cb == null) return;
        final Resources r = mContext.getResources();
        state.icon = ResourceIcon.get(R.drawable.ic_settings_profiles_am);
        state.label = r.getString(R.string.quick_settings_audio_profile_settings_title);
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

        public void onMobileDataEnabled(int subId, boolean enabled) {
            mDetailAdapter.setMobileDataEnabled(enabled);
        }
    };

    private final class CellularDetailAdapter implements DetailAdapter {

        @Override
        public int getTitle() {
            return R.string.quick_settings_audio_profile_settings_title;
        }

        @Override
        public Boolean getToggleState() {
            return mDataController.isMobileDataSupported()
                    ? mDataController.isMobileDataEnabled()
                    : null;
        }

        @Override
        public Intent getSettingsIntent() {
            return AUDIOPROFILE_SETTINGS;
        }

        @Override
        public void setToggleState(boolean state) {
            mDataController.setMobileDataEnabled(state);
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final DataUsageDetailView v = (DataUsageDetailView) (convertView != null
                    ? convertView
                    : LayoutInflater.from(mContext).inflate(R.layout.data_usage, parent, false));
            DataUsageInfo info = null;
            if (mUxFlag) {
                info = mDataController.getDataUsageInfo(SubscriptionManager.getDefaultDataSubId(), null);
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

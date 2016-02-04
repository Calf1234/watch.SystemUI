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

package com.android.systemui.statusbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.os.SystemProperties;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.statusbar.policy.WifiIcons;

import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.List;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkControllerImpl.SignalCluster,
        SecurityController.SecurityControllerCallback {

    static final String TAG = "SignalClusterView";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean UXFEATURE ="ux".equals(SystemProperties.get("ro.sf3g.feature"));
    private static final long DEFAULT_DURATION = new ValueAnimator().getDuration();
    private static final long SHORT_DURATION = DEFAULT_DURATION / 3;

    NetworkControllerImpl mNC;
    SecurityController mSC;

    private boolean mNoSimsVisible = false;
    private boolean mVpnVisible = false;
    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private int mAirplaneContentDescription;
    private String mWifiDescription;
    private ArrayList<PhoneState> mPhoneStates = new ArrayList<PhoneState>();
    private boolean mWifi_activityIn = false, mWifi_activityOut = false;

    ViewGroup mWifiGroup;
    ViewGroup mWifiTrafficGroup;
    ImageView mVpn, mWifi, mAirplane, mNoSims;
    private ImageView mIn;
    private ImageView mOut;

    View mWifiAirplaneSpacer;
    View mWifiSignalSpacer;
    LinearLayout mMobileSignalGroup;

    private int mWideTypeIconStartPadding;
    private int mSecondaryTelephonyPadding;
    private int mEndPadding;
    private int mEndPaddingNothingVisible;

    private WifiManager mWifiManager;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public void setNetworkController(NetworkControllerImpl nc) {
        if (DEBUG) Log.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    public void setSecurityController(SecurityController sc) {
        if (DEBUG) Log.d(TAG, "SecurityController=" + sc);
        mSC = sc;
        mSC.addCallback(this);
        mVpnVisible = mSC.isVpnEnabled();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mWideTypeIconStartPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.wide_type_icon_start_padding);
        mSecondaryTelephonyPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.secondary_telephony_padding);
        mEndPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.signal_cluster_battery_padding);
        mEndPaddingNothingVisible = getContext().getResources().getDimensionPixelSize(
                R.dimen.no_signal_cluster_battery_padding);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mVpn            = (ImageView) findViewById(R.id.vpn);
        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifiTrafficGroup = (ViewGroup) findViewById(R.id.wifi_traffic_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mIn             = (ImageView) findViewById(R.id.wifi_traffic_in);
        mOut            = (ImageView) findViewById(R.id.wifi_traffic_out);
        mAirplane       = (ImageView) findViewById(R.id.airplane);
        mNoSims         = (ImageView) findViewById(R.id.no_sims);
        mWifiAirplaneSpacer =         findViewById(R.id.wifi_airplane_spacer);
        mWifiSignalSpacer =           findViewById(R.id.wifi_signal_spacer);
        mMobileSignalGroup = (LinearLayout) findViewById(R.id.mobile_signal_group);
        for (PhoneState state : mPhoneStates) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mVpn            = null;
        mWifiGroup      = null;
        mWifi           = null;
        mAirplane       = null;
        mMobileSignalGroup.removeAllViews();
        mMobileSignalGroup = null;

        super.onDetachedFromWindow();
    }

    // From SecurityController.
    @Override
    public void onStateChanged() {
        post(new Runnable() {
            @Override
            public void run() {
                mVpnVisible = mSC.isVpnEnabled();
                apply();
            }
        });
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, String contentDescription,boolean activityIn, boolean activityOut) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        mWifiDescription = contentDescription;
        mWifi_activityIn = activityIn;
        mWifi_activityOut =activityOut;

        apply();
    }

    @Override
    public void setMobileDataIndicators(boolean visible, int strengthIcon, int typeIcon,
            String contentDescription, String typeContentDescription, boolean isTypeIconWide,
            int subId) {
        PhoneState state = getOrInflateState(subId);
        state.mMobileVisible = visible;
        state.mMobileStrengthId = strengthIcon;
        state.mMobileTypeId = typeIcon;
        state.mMobileDescription = contentDescription;
        state.mMobileTypeDescription = typeContentDescription;
        state.mIsMobileTypeIconWide = isTypeIconWide;

        apply();
    }

    /**@hide*/
    @Override
    public void setMobileDataIndicatorsUX(boolean visible, int strengthIcon, int typeIcon,
        String contentDescription, String typeContentDescription, boolean isTypeIconWide,
        int subId,boolean activityIn, boolean activityOut,boolean inoutShow){
        PhoneState state = getOrInflateState(subId);
        state.mMobileVisible = visible;
        state.mMobileStrengthId = strengthIcon;
        state.mMobileTypeId = typeIcon;
        state.mMobileDescription = contentDescription;
        state.mMobileTypeDescription = typeContentDescription;
        state.mIsMobileTypeIconWide = isTypeIconWide;
        state.mMobile_activityIn = activityIn;
        state.mMobile_activityOut = activityOut;
        state.showMobileDataInout = inoutShow;

        apply();
    }

    @Override
    public void setNoSims(boolean show) {
        mNoSimsVisible = show;
    }

    @Override
    public void setSubs(List<SubscriptionInfo> subs) {
        // Clear out all old subIds.
        mPhoneStates.clear();
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.removeAllViews();
        }
        final int n = subs.size();
        for (int i = 0; i < n; i++) {
            inflatePhoneState(subs.get(i).getSubscriptionId());
        }
    }

    private PhoneState getOrInflateState(int subId) {
        for (PhoneState state : mPhoneStates) {
            if (state.mSubId == subId) {
                return state;
            }
        }
        return inflatePhoneState(subId);
    }

    private PhoneState inflatePhoneState(int subId) {
        PhoneState state = new PhoneState(subId, mContext);
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }
        mPhoneStates.add(state);
        return state;
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId, int contentDescription) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;
        mAirplaneContentDescription = contentDescription;

        apply();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup != null && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        for (PhoneState state : mPhoneStates) {
            state.populateAccessibilityEvent(event);
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
        }

        for (PhoneState state : mPhoneStates) {
            if (state.mMobile != null) {
                state.mMobile.setImageDrawable(null);
            }
            if (state.mMobileType != null) {
                state.mMobileType.setImageDrawable(null);
            }
        }

        if(mAirplane != null) {
            mAirplane.setImageDrawable(null);
        }

        apply();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void setVisibility(View view, boolean shown, boolean visible) {
        final float newAlpha = shown && visible ? 1 : 0;
        if (view.getAlpha() == newAlpha) return;
        if (shown) {
            view.animate()
                .setDuration(visible ? SHORT_DURATION : DEFAULT_DURATION)
                .alpha(newAlpha)
                .start();
        } else {
            view.setAlpha(newAlpha);
        }
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        mVpn.setVisibility(mVpnVisible ? View.VISIBLE : View.GONE);
        if (DEBUG) Log.d(TAG, String.format("vpn: %s", mVpnVisible ? "VISIBLE" : "GONE"));
        if (mWifiVisible) {
            mWifi.setImageResource(mWifiStrengthId);
            mWifiGroup.setContentDescription(mWifiDescription);
            mWifiGroup.setVisibility(View.VISIBLE);

            mWifiTrafficGroup.setVisibility(View.VISIBLE);
            setVisibility(mIn, true, mWifi_activityIn);
            setVisibility(mOut, true, mWifi_activityOut);
        } else {
            if((mIsAirplaneMode)&&(!mWifiManager.isWifiEnabled())){
                mWifiGroup.setVisibility(View.GONE);
            }
            else {
               mWifiGroup.setVisibility(View.VISIBLE);
               mWifi.setImageResource(WifiIcons.QS_WIFI_NO_NETWORK);
               mWifiTrafficGroup.setVisibility(View.GONE);
            }
        }

        if (DEBUG) Log.d(TAG,
                String.format("wifi: %s sig=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId));

        boolean anyMobileVisible = false;
        int firstMobileTypeId = 0;
        for (PhoneState state : mPhoneStates) {
            if (state.apply(anyMobileVisible)) {
                if (!anyMobileVisible) {
                    firstMobileTypeId = state.mMobileTypeId;
                    anyMobileVisible = true;
                }
            }
        }

        if (mIsAirplaneMode) {
            mAirplane.setImageResource(mAirplaneIconId);
            mAirplane.setContentDescription(mAirplaneContentDescription != 0 ?
                    mContext.getString(mAirplaneContentDescription) : null);
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (mIsAirplaneMode && mWifiVisible) {
            mWifiAirplaneSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiAirplaneSpacer.setVisibility(View.GONE);
        }

        if (((anyMobileVisible && firstMobileTypeId != 0) || mNoSimsVisible) && mWifiVisible) {
            mWifiSignalSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiSignalSpacer.setVisibility(View.GONE);
        }

        if (SystemProperties.getBoolean("persist.nomodem_ui", false))
        {
            mNoSims.setVisibility(View.GONE);
        } else {
            mNoSims.setVisibility(mNoSimsVisible ? View.VISIBLE : View.GONE);
        }

        boolean anythingVisible = mNoSimsVisible || mWifiVisible || mIsAirplaneMode
                || anyMobileVisible || mVpnVisible;
        setPaddingRelative(0, 0, anythingVisible ? mEndPadding : mEndPaddingNothingVisible, 0);
    }

    private class PhoneState {
        private final int mSubId;
        private boolean mMobile_activityIn = false,mMobile_activityOut = false;
        private boolean mMobileVisible = false,showMobileDataInout = false;
        private int mMobileStrengthId = 0, mMobileTypeId = 0;
        private boolean mIsMobileTypeIconWide;
        private String mMobileDescription, mMobileTypeDescription;
        private ViewGroup mMobileTrafficGroup;
        private ImageView mDataIn,mDataOut;

        private ViewGroup mMobileGroup;
        private ImageView mMobile, mMobileType;

        public PhoneState(int subId, Context context) {
            ViewGroup root = (ViewGroup) LayoutInflater.from(context)
                    .inflate(R.layout.mobile_signal_group, null);
            setViews(root);
            mSubId = subId;
        }

        public void setViews(ViewGroup root) {
            mMobileGroup    = root;
            mMobile         = (ImageView) root.findViewById(R.id.mobile_signal);
            mMobileType     = (ImageView) root.findViewById(R.id.mobile_type);
            if(UXFEATURE){
                mMobileTrafficGroup = (ViewGroup) root.findViewById(R.id.mobile_traffic_combo);
                mDataIn             = (ImageView) root.findViewById(R.id.mobile_traffic_in);
                mDataOut            = (ImageView) root.findViewById(R.id.mobile_traffic_out);
            }
        }

        public boolean apply(boolean isSecondaryIcon) {
            if (mMobileVisible && !mIsAirplaneMode) {
                mMobile.setImageResource(mMobileStrengthId);
                mMobileType.setImageResource(mMobileTypeId);
                mMobileGroup.setContentDescription(mMobileTypeDescription
                        + " " + mMobileDescription);
                mMobileGroup.setVisibility(View.VISIBLE);
                if(UXFEATURE){
                    if(showMobileDataInout){
                        mMobileTrafficGroup.setVisibility(!mWifiVisible?View.VISIBLE:View.GONE);
                        mDataIn.setVisibility(!mWifiVisible?View.VISIBLE:View.GONE);
                        mDataOut.setVisibility(!mWifiVisible?View.VISIBLE:View.GONE);
                        setVisibility(mDataIn, true, mMobile_activityIn);
                        setVisibility(mDataOut, true, mMobile_activityOut);
                    }else{
                        mMobileTrafficGroup.setVisibility(View.GONE);
                    }
                }
            } else {
                mMobileGroup.setVisibility(View.GONE);
                if(UXFEATURE){
                    mMobileTrafficGroup.setVisibility(View.GONE);
                    mDataIn.setVisibility(View.GONE);
                    mDataOut.setVisibility(View.GONE);
                }
            }

            // When this isn't next to wifi, give it some extra padding between the signals.
            mMobileGroup.setPaddingRelative(isSecondaryIcon ? mSecondaryTelephonyPadding : 0,
                    0, 0, 0);
            mMobile.setPaddingRelative(mIsMobileTypeIconWide ? mWideTypeIconStartPadding : 0,
                    0, 0, 0);

            if (DEBUG) Log.d(TAG, String.format("mobile: %s sig=%d typ=%d",
                        (mMobileVisible ? "VISIBLE" : "GONE"), mMobileStrengthId, mMobileTypeId));

            mMobileType.setVisibility(mMobileTypeId != 0 ? View.VISIBLE : View.GONE);

            return mMobileVisible;
        }

        public void populateAccessibilityEvent(AccessibilityEvent event) {
            if (mMobileVisible && mMobileGroup != null
                    && mMobileGroup.getContentDescription() != null) {
                event.getText().add(mMobileGroup.getContentDescription());
            }
        }
    }
}


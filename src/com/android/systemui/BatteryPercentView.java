package com.android.systemui;
/* <Added by hubohua 001267 at 20150522 begin */
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.TextView;

public class BatteryPercentView extends TextView {
    public static final String TAG = BatteryPercentView.class.getSimpleName();

    public BatteryPercentView(Context context) {
        super(context);
    }

    public BatteryPercentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BatteryPercentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private BatteryTracker mTracker = new BatteryTracker();

    private class BatteryTracker extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                int level = (int)(100f
                        * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                        / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                if(level <= 0){
                    level = 1;
                }else if(level > 100){
                    level = 100;
                }
                setText(String.valueOf(level) + "%");
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        final Intent sticky = getContext().registerReceiver(mTracker, filter);
        if (sticky != null) {
            // preload the battery level
            mTracker.onReceive(getContext(), sticky);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        getContext().unregisterReceiver(mTracker);
    }
}
/* Added by hubohua 001267 at 20150522 end.> */

package com.yaaddahani.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Phone restarted, app updated or clock changed: set all alarms again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Alarms.ensureChannel(c);
        Alarms.rescheduleAll(c);
    }
}

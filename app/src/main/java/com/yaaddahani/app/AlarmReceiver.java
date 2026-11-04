package com.yaaddahani.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Phone alarm went off for a task: hand it to the reminder service. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra("id");
        if (id == null) return;
        Intent svc = new Intent(c, ReminderService.class).putExtra("id", id);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
            else c.startService(svc);
        } catch (Exception e) {
            // Fallback: at least show the notification without voice.
            ReminderService.postNotification(c, id, false);
        }
    }
}

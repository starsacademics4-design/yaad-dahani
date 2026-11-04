package com.yaaddahani.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Buttons on the notification: Ho gaya / 5 min / 15 min. */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra("id");
        String action = intent.getStringExtra("action");
        if (id == null || action == null) return;
        ReminderService.stopSpeaking();
        switch (action) {
            case "done": Store.complete(c, id); break;
            case "snooze5": Store.snooze(c, id, 5); break;
            case "snooze15": Store.snooze(c, id, 15); break;
            default: return;
        }
        Alarms.clearNotification(c, id);
        Alarms.rescheduleAll(c);
        MainActivity.callJs("window.__reload&&window.__reload()");
    }
}

package com.yaaddahani.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Sets one phone alarm per pending task, so reminders ring even when the app is closed. */
final class Alarms {
    static final String CHANNEL = "yaad_alarms_v1";
    private static final String SCHEDULED = "scheduledIds";

    private Alarms() {}

    static int notifId(String id) {
        return id.hashCode();
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Task ka waqt aane par alert");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 800});
        ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        // The service plays its own beep and voice, so the notification itself stays quiet.
        ch.setSound(null, (AudioAttributes) null);
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent alarmIntent(Context c, String id, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction("com.yaaddahani.app.ALARM")
                .setData(Uri.parse("yaad://task/" + id))
                .putExtra("id", id);
        return PendingIntent.getBroadcast(c, 0, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openAppIntent(Context c, String alertId) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (alertId != null) {
            i.setData(Uri.parse("yaad://alert/" + alertId));
            i.putExtra("alert", alertId);
        }
        return PendingIntent.getActivity(c, alertId == null ? 1 : 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private static void set(Context c, String id, long when) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(c, id, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (canExact(c)) {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, openAppIntent(c, null)), pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    private static void cancel(Context c, String id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(c, id, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    /** Called after every change to the task list. */
    static synchronized void rescheduleAll(Context c) {
        JSONArray a = Store.tasks(c);
        long now = System.currentTimeMillis();
        Set<String> active = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject t = a.optJSONObject(i);
            if (t == null || t.optBoolean("done") || Store.isAlerted(t)) continue;
            String id = t.optString("id");
            if (id.isEmpty()) continue;
            long when = Math.max(t.optLong("due"), now + 3000); // missed ones ring in 3 seconds
            set(c, id, when);
            active.add(id);
        }
        Set<String> old = Store.prefs(c).getStringSet(SCHEDULED, new HashSet<>());
        for (String id : old) if (!active.contains(id)) cancel(c, id);
        Store.prefs(c).edit().putStringSet(SCHEDULED, active).apply();
    }

    static void clearNotification(Context c, String id) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(notifId(id));
    }
}

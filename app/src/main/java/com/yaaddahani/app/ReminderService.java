package com.yaaddahani.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Runs when a task is due: shows the alarm notification, beeps, and reads the task out loud.
 * Works even when the app is closed.
 */
public class ReminderService extends Service {
    private static ReminderService running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private PowerManager.WakeLock wakeLock;
    private String currentId;

    static void stopSpeaking() {
        ReminderService s = running;
        if (s != null) s.handler.post(s::finish);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static Notification build(Context c, String id, JSONObject t) {
        Alarms.ensureChannel(c);
        String time = new SimpleDateFormat("h:mm a", Locale.US).format(new Date(t.optLong("due")));
        String note = t.optString("note", "").trim();
        String text = (note.isEmpty() ? "Aap ke task ka waqt ho gaya" : note) + " • " + time;
        PendingIntent open = Alarms.openAppIntent(c, id);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, Alarms.CHANNEL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle("🔔 " + t.optString("title"))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setAutoCancel(true)
                .addAction(action(c, id, "done", "✓ Ho gaya"))
                .addAction(action(c, id, "snooze5", "⏰ 5 min"))
                .addAction(action(c, id, "snooze15", "⏰ 15 min"));
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_MAX).setVibrate(new long[]{0, 400, 200, 400, 200, 800});
        }
        return b.build();
    }

    private static Notification.Action action(Context c, String id, String act, String label) {
        Intent i = new Intent(c, ActionReceiver.class)
                .setData(Uri.parse("yaad://act/" + act + "/" + id))
                .putExtra("id", id)
                .putExtra("action", act);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(null, label, pi).build();
    }

    /** Notification only, no voice (used if the service could not start). */
    static void postNotification(Context c, String id, boolean ignored) {
        JSONObject t = Store.find(Store.tasks(c), id);
        if (t == null || t.optBoolean("done")) return;
        Store.markAlerted(c, id);
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        nm.notify(Alarms.notifId(id), build(c, id, t));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String id = intent == null ? null : intent.getStringExtra("id");
        JSONObject t = id == null ? null : Store.find(Store.tasks(this), id);

        // Android requires startForeground() right away, whatever happens next.
        Notification n;
        if (t != null) {
            n = build(this, id, t);
        } else {
            Alarms.ensureChannel(this);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, Alarms.CHANNEL) : new Notification.Builder(this);
            n = b.setSmallIcon(R.drawable.ic_stat_bell).setContentTitle("Yaad Dahani").build();
        }
        int nid = id == null ? 1 : Alarms.notifId(id);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(nid, n);
        }

        if (t == null || t.optBoolean("done") || Store.isAlerted(t) || t.optLong("due") > System.currentTimeMillis() + 60000) {
            stopForegroundCompat(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (MainActivity.isVisible()) {
            // App is open on screen: let the app show its own big alert and speak.
            stopForegroundCompat(true);
            MainActivity.callJs("window.__reload&&window.__reload()");
            stopSelf();
            return START_NOT_STICKY;
        }

        finishPrevious();
        running = this;
        currentId = id;
        Store.markAlerted(this, id);
        Alarms.rescheduleAll(this);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YaadDahani:reminder");
        wakeLock.acquire(90_000);

        JSONObject s = Store.settings(this);
        boolean beep = s.optBoolean("beep", true);
        if (beep) beep();
        final String text = Store.speechText(this, t);
        handler.postDelayed(() -> speak(text, s), beep ? 1300 : 200);
        handler.postDelayed(this::finish, 80_000); // safety stop
        return START_NOT_STICKY;
    }

    private void finishPrevious() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    private void beep() {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
            for (int k = 0; k < 3; k++) {
                handler.postDelayed(() -> tg.startTone(ToneGenerator.TONE_PROP_BEEP, 250), k * 350L);
            }
            handler.postDelayed(tg::release, 1300);
        } catch (Exception ignored) {}
    }

    private void speak(final String text, final JSONObject s) {
        final int times = Math.max(1, s.optInt("times", 2));
        final float rate = (float) s.optDouble("rate", 1.0);
        final String voiceName = s.optString("voice", "");
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) { finish(); return; }
            try {
                applyVoice(tts, voiceName);
                tts.setSpeechRate(rate);
                if (Build.VERSION.SDK_INT >= 21) {
                    tts.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String u) {}
                    @Override public void onDone(String u) { if (("u" + (times - 1)).equals(u)) handler.postDelayed(ReminderService.this::finish, 300); }
                    @Override public void onError(String u) { handler.post(ReminderService.this::finish); }
                });
                for (int i = 0; i < times; i++) {
                    Bundle p = new Bundle();
                    p.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
                    tts.speak(text, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, p, "u" + i);
                    if (i < times - 1) tts.playSilentUtterance(700, TextToSpeech.QUEUE_ADD, "gap" + i);
                }
            } catch (Exception e) {
                finish();
            }
        });
    }

    static void applyVoice(TextToSpeech tts, String voiceName) {
        if (voiceName == null || voiceName.isEmpty()) return;
        try {
            for (Voice v : tts.getVoices()) {
                if (v.getName().equals(voiceName)) { tts.setVoice(v); return; }
            }
        } catch (Exception ignored) {}
    }

    private void stopForegroundCompat(boolean remove) {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(remove ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(remove);
        }
    }

    private void finish() {
        if (running != this) return;
        running = null;
        finishPrevious();
        JSONObject t = currentId == null ? null : Store.find(Store.tasks(this), currentId);
        boolean stillPending = t != null && !t.optBoolean("done") && Store.isAlerted(t);
        if (stillPending) {
            // Keep the notification on screen (with its buttons) after speaking.
            stopForegroundCompat(false);
        } else {
            // Already marked done or snoozed from the notification buttons.
            stopForegroundCompat(true);
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        finishPrevious();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}

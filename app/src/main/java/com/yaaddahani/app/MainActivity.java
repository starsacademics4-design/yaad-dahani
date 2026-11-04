package com.yaaddahani.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Shows the Yaad Dahani screen (the web page) and gives it access to phone features. */
public class MainActivity extends Activity {
    private static WeakReference<MainActivity> ref = new WeakReference<>(null);
    private static volatile boolean visible;

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady;
    private boolean pageLoaded;
    private String pendingAlert;

    static boolean isVisible() {
        return visible && ref.get() != null;
    }

    static void callJs(final String js) {
        final MainActivity a = ref.get();
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (a.web != null && a.pageLoaded) a.web.evaluateJavascript(js, null);
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ref = new WeakReference<>(this);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().setStatusBarColor(Color.parseColor("#0f172a"));
        getWindow().setNavigationBarColor(Color.parseColor("#0f172a"));

        Alarms.ensureChannel(this);
        Alarms.rescheduleAll(this);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0f172a"));
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                showPendingAlert();
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) callJs("window.__voicesReady&&window.__voicesReady()");
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra("alert");
        if (id != null) {
            pendingAlert = id;
            setIntent(new Intent(this, MainActivity.class));
            showPendingAlert();
        }
    }

    private void showPendingAlert() {
        if (!pageLoaded || pendingAlert == null) return;
        String id = pendingAlert.replaceAll("[^A-Za-z0-9_-]", "");
        pendingAlert = null;
        web.evaluateJavascript("window.__showAlert&&window.__showAlert('" + id + "')", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        ref = new WeakReference<>(this);
        web.onResume();
        web.resumeTimers();
        callJs("window.__reload&&window.__reload();window.__banners&&window.__banners()");
    }

    @Override
    protected void onPause() {
        visible = false;
        web.onPause();
        web.pauseTimers();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // Keep the app alive in the background instead of closing it.
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (ref.get() == this) ref = new WeakReference<>(null);
        if (tts != null) tts.shutdown();
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        callJs("window.__banners&&window.__banners()");
    }

    private void speakNow(String text, int times) {
        if (!ttsReady) return;
        JSONObject s = Store.settings(this);
        ReminderService.applyVoice(tts, s.optString("voice", ""));
        tts.setSpeechRate((float) s.optDouble("rate", 1.0));
        for (int i = 0; i < Math.max(1, times); i++) {
            tts.speak(text, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "m" + i);
            if (i < times - 1) tts.playSilentUtterance(700, TextToSpeech.QUEUE_ADD, "g" + i);
        }
    }

    private void openSettings(String action, boolean withPackage) {
        try {
            Intent i = new Intent(action);
            if (withPackage) i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
        }
    }

    /** Functions the web page can call as window.Android.xxx() */
    private class Bridge {
        @JavascriptInterface
        public String get(String key) {
            return Store.get(MainActivity.this, key);
        }

        @JavascriptInterface
        public void set(String key, String value) {
            Store.set(MainActivity.this, key, value);
            if (Store.TASKS.equals(key)) Alarms.rescheduleAll(MainActivity.this);
        }

        @JavascriptInterface
        public void speak(String text, int times) {
            runOnUiThread(() -> speakNow(text, times));
        }

        @JavascriptInterface
        public void stopSpeak() {
            runOnUiThread(() -> { if (ttsReady) tts.stop(); });
            ReminderService.stopSpeaking();
        }

        @JavascriptInterface
        public String voices() {
            JSONArray out = new JSONArray();
            if (!ttsReady) return out.toString();
            try {
                List<Voice> list = new ArrayList<>(tts.getVoices());
                Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
                for (Voice v : list) {
                    if (v.isNetworkConnectionRequired()) continue;
                    JSONObject o = new JSONObject();
                    Locale l = v.getLocale();
                    o.put("name", v.getName());
                    o.put("lang", l.getLanguage() + (l.getCountry().isEmpty() ? "" : "-" + l.getCountry()));
                    o.put("label", l.getDisplayName(Locale.ENGLISH));
                    out.put(o);
                }
            } catch (Exception ignored) {}
            return out.toString();
        }

        @JavascriptInterface
        public String notifStatus() {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return "default";
            }
            return nm.areNotificationsEnabled() ? "granted" : "denied";
        }

        @JavascriptInterface
        public void requestNotif() {
            runOnUiThread(() -> {
                boolean asked = Store.prefs(MainActivity.this).getBoolean("askedNotif", false);
                if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        && (!asked || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                    Store.prefs(MainActivity.this).edit().putBoolean("askedNotif", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                } else if (Build.VERSION.SDK_INT >= 26) {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    try { startActivity(i); } catch (Exception e) { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true); }
                } else {
                    openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true);
                }
            });
        }

        @JavascriptInterface
        public boolean canExact() {
            return Alarms.canExact(MainActivity.this);
        }

        @JavascriptInterface
        public void openExact() {
            if (Build.VERSION.SDK_INT >= 31) openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true);
        }

        @JavascriptInterface
        public boolean batteryOk() {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }

        @JavascriptInterface
        public void openBattery() {
            openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true);
        }

        @JavascriptInterface
        public boolean fullScreenOk() {
            if (Build.VERSION.SDK_INT < 34) return true;
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return nm.canUseFullScreenIntent();
        }

        @JavascriptInterface
        public void openFullScreen() {
            if (Build.VERSION.SDK_INT >= 34) openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true);
        }

        @JavascriptInterface
        public void clearNotif(String id) {
            ReminderService.stopSpeaking();
            Alarms.clearNotification(MainActivity.this, id);
        }

        @JavascriptInterface
        public void keepScreen(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void testNotif() {
            // A real reminder 10 seconds from now, so the full alarm path can be tested with the app closed.
            try {
                JSONArray a = Store.tasks(MainActivity.this);
                JSONObject t = new JSONObject();
                t.put("id", "test" + Store.uid());
                t.put("title", "Test reminder");
                t.put("note", "");
                t.put("due", System.currentTimeMillis() + 10_000);
                t.put("repeat", "none");
                t.put("alerted", JSONObject.NULL);
                t.put("done", false);
                a.put(t);
                Store.saveTasks(MainActivity.this, a);
                Alarms.rescheduleAll(MainActivity.this);
            } catch (Exception ignored) {}
        }
    }
}

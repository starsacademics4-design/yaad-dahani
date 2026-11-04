package com.yaaddahani.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Random;

/** Saves tasks and settings on the phone, and holds the task rules (done, snooze, repeat). */
final class Store {
    static final String TASKS = "yaadDahani.tasks.v1";
    static final String SETTINGS = "yaadDahani.settings.v1";

    private Store() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("yaad_dahani", Context.MODE_PRIVATE);
    }

    static synchronized String get(Context c, String key) {
        return prefs(c).getString(key, null);
    }

    static synchronized void set(Context c, String key, String value) {
        prefs(c).edit().putString(key, value).commit();
    }

    static synchronized JSONArray tasks(Context c) {
        try {
            String s = get(c, TASKS);
            return s == null ? new JSONArray() : new JSONArray(s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static synchronized void saveTasks(Context c, JSONArray a) {
        set(c, TASKS, a.toString());
    }

    static JSONObject settings(Context c) {
        try {
            String s = get(c, SETTINGS);
            return s == null ? new JSONObject() : new JSONObject(s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static JSONObject find(JSONArray a, String id) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject t = a.optJSONObject(i);
            if (t != null && id.equals(t.optString("id"))) return t;
        }
        return null;
    }

    static boolean isAlerted(JSONObject t) {
        return !t.isNull("alerted") && t.optLong("alerted", -1) == t.optLong("due");
    }

    /** Text that is spoken, e.g. "Sir, you have to call Ammi. note" */
    static String speechText(Context c, JSONObject t) {
        String prefix = settings(c).optString("prefix", "Sir, you have to").trim();
        String note = t.optString("note", "").trim();
        return (prefix.isEmpty() ? "" : prefix + " ") + t.optString("title") + (note.isEmpty() ? "" : ". " + note);
    }

    static String uid() {
        return Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString(new Random().nextInt(36 * 36 * 36 * 36 * 36), 36);
    }

    static long nextDue(JSONObject t) {
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(t.optLong("due"));
        String rep = t.optString("repeat", "none");
        long now = System.currentTimeMillis();
        do {
            switch (rep) {
                case "daily": d.add(Calendar.DAY_OF_MONTH, 1); break;
                case "weekly": d.add(Calendar.DAY_OF_MONTH, 7); break;
                case "monthly": d.add(Calendar.MONTH, 1); break;
                case "weekdays":
                    do { d.add(Calendar.DAY_OF_MONTH, 1); }
                    while (d.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || d.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY);
                    break;
                default: return t.optLong("due");
            }
        } while (d.getTimeInMillis() <= now);
        return d.getTimeInMillis();
    }

    /** Same as completeTask() in the web page. */
    static synchronized void complete(Context c, String id) {
        try {
            JSONArray a = tasks(c);
            JSONObject t = find(a, id);
            if (t == null) return;
            String rep = t.optString("repeat", "none");
            long now = System.currentTimeMillis();
            if (!"none".equals(rep)) {
                JSONObject log = new JSONObject();
                log.put("id", uid());
                log.put("title", t.optString("title"));
                log.put("note", t.optString("note", ""));
                log.put("due", t.optLong("due"));
                log.put("repeat", "none");
                log.put("done", true);
                log.put("doneAt", now);
                log.put("log", true);
                a.put(log);
                t.put("due", nextDue(t));
                t.put("alerted", JSONObject.NULL);
                t.put("done", false);
            } else {
                t.put("done", true);
                t.put("doneAt", now);
            }
            saveTasks(c, a);
        } catch (Exception ignored) {}
    }

    static synchronized void snooze(Context c, String id, int minutes) {
        try {
            JSONArray a = tasks(c);
            JSONObject t = find(a, id);
            if (t == null) return;
            t.put("due", System.currentTimeMillis() + minutes * 60000L);
            t.put("alerted", JSONObject.NULL);
            saveTasks(c, a);
        } catch (Exception ignored) {}
    }

    static synchronized void markAlerted(Context c, String id) {
        try {
            JSONArray a = tasks(c);
            JSONObject t = find(a, id);
            if (t == null) return;
            t.put("alerted", t.optLong("due"));
            saveTasks(c, a);
        } catch (Exception ignored) {}
    }
}

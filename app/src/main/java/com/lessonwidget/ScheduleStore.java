package com.lessonwidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Хранение расписания на телефоне (SharedPreferences, JSON). */
final class ScheduleStore {

    private static final String PREFS = "schedule";
    private static final String KEY = "json";

    private ScheduleStore() {
    }

    static Schedule load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = p.getString(KEY, null);
        if (json == null) {
            return Schedule.defaults();
        }
        try {
            return fromJson(json);
        } catch (Exception e) {
            return Schedule.defaults();
        }
    }

    static void save(Context ctx, Schedule s) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, toJson(s)).apply();
        } catch (Exception ignored) {
        }
    }

    private static String toJson(Schedule s) throws Exception {
        JSONObject o = new JSONObject();
        JSONArray bells = new JSONArray();
        for (Schedule.Bell b : s.bells) {
            bells.put(new JSONArray().put(b.start).put(b.end));
        }
        o.put("bells", bells);
        JSONArray days = new JSONArray();
        for (int d = 0; d < Schedule.DAYS; d++) {
            JSONArray day = new JSONArray();
            for (Schedule.Item it : s.days.get(d)) {
                day.put(new JSONArray().put(it.subject).put(it.room));
            }
            days.put(day);
        }
        o.put("days", days);
        return o.toString();
    }

    private static Schedule fromJson(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        Schedule s = new Schedule();
        JSONArray bells = o.getJSONArray("bells");
        for (int i = 0; i < bells.length(); i++) {
            JSONArray b = bells.getJSONArray(i);
            s.bells.add(new Schedule.Bell(b.getInt(0), b.getInt(1)));
        }
        JSONArray days = o.getJSONArray("days");
        for (int d = 0; d < Schedule.DAYS && d < days.length(); d++) {
            JSONArray day = days.getJSONArray(d);
            for (int i = 0; i < day.length(); i++) {
                JSONArray it = day.getJSONArray(i);
                s.days.get(d).add(new Schedule.Item(it.getString(0), it.getString(1)));
            }
        }
        if (s.bells.isEmpty()) {
            return Schedule.defaults();
        }
        return s;
    }
}

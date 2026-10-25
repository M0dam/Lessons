package com.lessonwidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;

/** Собирает картинку виджета по текущему времени и ставит будильник на следующий звонок. */
final class WidgetUpdater {

    static final String ACTION_REFRESH = "com.lessonwidget.REFRESH";

    private static final String[] ON_DAY = {
            "в понедельник", "во вторник", "в среду", "в четверг",
            "в пятницу", "в субботу", "в воскресенье"};

    private WidgetUpdater() {
    }

    static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, LessonWidgetProvider.class));
        if (ids.length == 0) {
            cancelAlarm(ctx);
            return;
        }

        Schedule schedule = ScheduleStore.load(ctx);
        Calendar now = Calendar.getInstance();
        int day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7; // понедельник = 0
        int nowSec = now.get(Calendar.HOUR_OF_DAY) * 3600
                + now.get(Calendar.MINUTE) * 60
                + now.get(Calendar.SECOND);

        ScheduleLogic.State st = ScheduleLogic.compute(schedule, day, nowSec);
        mgr.updateAppWidget(ids, build(ctx, st, day, nowSec));
        scheduleNext(ctx, now, st);
    }

    private static RemoteViews build(Context ctx, ScheduleLogic.State st, int day, int nowSec) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);

        String status;
        String bell = "";
        boolean showCountdown = false;

        switch (st.kind) {
            case ScheduleLogic.LESSON: {
                ScheduleLogic.Lesson c = st.current;
                status = "Сейчас " + c.number + " урок · " + c.subject
                        + (c.room.isEmpty() ? "" : " · каб. " + c.room);
                bell = (st.next != null && st.next.dayOffset == 0)
                        ? "Звонок на перемену в " + ScheduleLogic.fmt(st.bellSec)
                        : "Звонок с урока в " + ScheduleLogic.fmt(st.bellSec);
                showCountdown = true;
                break;
            }
            case ScheduleLogic.BREAK:
                status = "Перемена";
                bell = "Звонок на урок в " + ScheduleLogic.fmt(st.bellSec);
                showCountdown = true;
                break;
            case ScheduleLogic.BEFORE:
                status = "Уроки ещё не начались";
                bell = "Звонок на урок в " + ScheduleLogic.fmt(st.bellSec);
                showCountdown = true;
                break;
            case ScheduleLogic.DONE:
                status = st.todayHadLessons ? "На сегодня уроки закончились" : "Сегодня уроков нет";
                break;
            default:
                status = "Расписание пустое";
                break;
        }

        rv.setTextViewText(R.id.status, status);
        rv.setTextViewText(R.id.bell, bell);

        if (showCountdown) {
            long base = SystemClock.elapsedRealtime() + (st.bellSec - nowSec) * 1000L;
            rv.setViewVisibility(R.id.countdown, View.VISIBLE);
            rv.setChronometerCountDown(R.id.countdown, true);
            rv.setChronometer(R.id.countdown, base, "ещё %s", true);
        } else {
            rv.setViewVisibility(R.id.countdown, View.GONE);
            rv.setChronometer(R.id.countdown, SystemClock.elapsedRealtime(), "%s", false);
        }

        ScheduleLogic.Lesson n = st.next;
        if (n == null) {
            rv.setTextViewText(R.id.nextLabel, st.kind == ScheduleLogic.EMPTY
                    ? "Откройте приложение и заполните расписание"
                    : "Дальше уроков нет");
            rv.setTextViewText(R.id.next, "");
            rv.setTextViewText(R.id.nextInfo, "");
        } else {
            String label;
            if (n.dayOffset == 0) {
                label = "Следующий урок · №" + n.number;
            } else if (n.dayOffset == 1) {
                label = "Завтра · урок №" + n.number;
            } else {
                label = ON_DAY[(day + n.dayOffset) % 7] + " · урок №" + n.number;
                label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
            }
            rv.setTextViewText(R.id.nextLabel, label);
            rv.setTextViewText(R.id.next, n.subject);
            rv.setTextViewText(R.id.nextInfo,
                    (n.room.isEmpty() ? "" : "каб. " + n.room + " · ")
                            + ScheduleLogic.fmt(n.start) + "–" + ScheduleLogic.fmt(n.end));
        }

        // Нажатие на виджет открывает редактор.
        Intent open = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.root, pi);
        return rv;
    }

    private static PendingIntent alarmIntent(Context ctx) {
        Intent i = new Intent(ctx, LessonWidgetProvider.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(ctx, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void cancelAlarm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(alarmIntent(ctx));
        }
    }

    private static void scheduleNext(Context ctx, Calendar now, ScheduleLogic.State st) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Calendar t = (Calendar) now.clone();
        t.set(Calendar.HOUR_OF_DAY, 0);
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        long trigger;
        if (st.nextChangeSec >= 0) {
            trigger = t.getTimeInMillis() + st.nextChangeSec * 1000L + 300;
        } else {
            t.add(Calendar.DAY_OF_YEAR, 1); // после полуночи меняются подписи «Завтра / в понедельник»
            trigger = t.getTimeInMillis() + 2000;
        }

        PendingIntent pi = alarmIntent(ctx);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }
}

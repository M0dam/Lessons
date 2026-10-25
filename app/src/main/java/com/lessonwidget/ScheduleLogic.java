package com.lessonwidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Вся логика «какой сейчас урок / какой следующий». Без зависимостей от Android,
 * поэтому проверяется обычным javac (см. tools/LogicCheck.java).
 */
public final class ScheduleLogic {

    /** Идёт урок. */
    public static final int LESSON = 0;
    /** Перемена (или «окно») между уроками сегодня. */
    public static final int BREAK = 1;
    /** Сегодня уроки ещё не начались. */
    public static final int BEFORE = 2;
    /** Сегодня уроки закончились (или сегодня их нет). */
    public static final int DONE = 3;
    /** В расписании на всей неделе нет ни одного урока. */
    public static final int EMPTY = 4;

    public static final class Lesson {
        public final int number;
        public final String subject;
        public final String room;
        public final int start;
        public final int end;
        /** 0 = сегодня, 1 = завтра, и т. д. */
        public final int dayOffset;

        Lesson(int number, String subject, String room, int start, int end, int dayOffset) {
            this.number = number;
            this.subject = subject;
            this.room = room;
            this.start = start;
            this.end = end;
            this.dayOffset = dayOffset;
        }
    }

    public static final class State {
        public int kind;
        /** Урок, который идёт прямо сейчас (только для LESSON). */
        public Lesson current;
        /** Следующий урок (может быть завтрашним или позже). */
        public Lesson next;
        /** Секунда суток ближайшего звонка: конец текущего урока или начало следующего. -1 если нет. */
        public int bellSec = -1;
        /** Секунда суток, когда нужно обновить виджет. -1 = до полуночи. */
        public int nextChangeSec = -1;
        public boolean todayHadLessons;
    }

    private ScheduleLogic() {
    }

    /** Уроки дня, у которых заполнен предмет и есть звонок с таким номером. */
    public static List<Lesson> lessonsOf(Schedule s, int day, int dayOffset) {
        List<Lesson> out = new ArrayList<>();
        List<Schedule.Item> items = s.days.get(day);
        int n = Math.min(items.size(), s.bells.size());
        for (int i = 0; i < n; i++) {
            Schedule.Item it = items.get(i);
            String subject = it.subject == null ? "" : it.subject.trim();
            if (subject.isEmpty()) {
                continue; // «окно»
            }
            Schedule.Bell b = s.bells.get(i);
            String room = it.room == null ? "" : it.room.trim();
            out.add(new Lesson(i + 1, subject, room, b.start, b.end, dayOffset));
        }
        return out;
    }

    /**
     * @param day    день недели, 0 = понедельник … 6 = воскресенье
     * @param nowSec текущее время в секундах от полуночи
     */
    public static State compute(Schedule s, int day, int nowSec) {
        State st = new State();
        List<Lesson> today = lessonsOf(s, day, 0);
        st.todayHadLessons = !today.isEmpty();

        Lesson cur = null;
        Lesson nxt = null;
        for (Lesson l : today) {
            if (nowSec >= l.start && nowSec < l.end) {
                cur = l;
            }
            if (l.start > nowSec && (st.nextChangeSec < 0 || l.start < st.nextChangeSec)) {
                st.nextChangeSec = l.start;
            }
            if (l.end > nowSec && (st.nextChangeSec < 0 || l.end < st.nextChangeSec)) {
                st.nextChangeSec = l.end;
            }
        }

        if (cur != null) {
            for (Lesson l : today) {
                if (l.number > cur.number) {
                    nxt = l;
                    break;
                }
            }
            st.kind = LESSON;
            st.bellSec = cur.end;
        } else {
            for (Lesson l : today) {
                if (l.start > nowSec) {
                    nxt = l;
                    break;
                }
            }
            if (nxt != null) {
                st.kind = nowSec < today.get(0).start ? BEFORE : BREAK;
                st.bellSec = nxt.start;
            } else {
                st.kind = DONE;
            }
        }

        if (nxt == null) {
            // Следующий урок — в ближайший из следующих дней (до 7 дней вперёд, включая тот же день недели).
            for (int off = 1; off <= 7; off++) {
                List<Lesson> later = lessonsOf(s, (day + off) % 7, off);
                if (!later.isEmpty()) {
                    nxt = later.get(0);
                    break;
                }
            }
        }

        st.current = cur;
        st.next = nxt;
        if (cur == null && nxt == null) {
            st.kind = EMPTY;
        }
        return st;
    }

    public static String fmt(int sec) {
        return String.format(Locale.US, "%02d:%02d", sec / 3600, (sec % 3600) / 60);
    }
}

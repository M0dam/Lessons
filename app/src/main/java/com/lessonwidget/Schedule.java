package com.lessonwidget;

import java.util.ArrayList;
import java.util.List;

/** Данные: расписание звонков + уроки на каждый день недели (0 = понедельник). Без зависимостей от Android. */
public final class Schedule {

    public static final int DAYS = 7;

    /** Звонок: начало и конец урока в секундах от полуночи. */
    public static final class Bell {
        public int start;
        public int end;

        public Bell(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /** Одна строка расписания дня. Номер урока = позиция в списке + 1. Пустой предмет = «окно». */
    public static final class Item {
        public String subject = "";
        public String room = "";

        public Item() {
        }

        public Item(String subject, String room) {
            this.subject = subject;
            this.room = room;
        }
    }

    public final List<Bell> bells = new ArrayList<>();
    public final List<List<Item>> days = new ArrayList<>();

    public Schedule() {
        for (int i = 0; i < DAYS; i++) {
            days.add(new ArrayList<Item>());
        }
    }

    private static void bell(Schedule s, int h1, int m1, int h2, int m2) {
        s.bells.add(new Bell(h1 * 3600 + m1 * 60, h2 * 3600 + m2 * 60));
    }

    private static final String[][] DEFAULT_LESSONS = {
            {"РОВ", "Русский язык", "Биология", "Английский", "Общество", "Русский", "Алгебра", "Общество"},
            {"Алгебра", "Уч. курс Русский", "Химия", "Физра", "Английский", "Общество", "Геометрия"},
            {"Уч. курс Биология", "ОБЖ", "Геометрия", "Геометрия", "Информатика", "География", "Обществознание"},
            {"Английский", "РМГ", "История", "История", "Вероятность", "Литература", "Литература"},
            {"Алгебра", "Алгебра", "Физика", "Физика", "Химия", "Русский язык", "Физра"},
            {},
            {}
    };

    /** Исходные данные (звонки и уроки, которые вы прислали). Кабинеты пустые — впишите в приложении. */
    public static Schedule defaults() {
        Schedule s = new Schedule();
        bell(s, 8, 0, 8, 45);
        bell(s, 8, 55, 9, 35);
        bell(s, 9, 50, 10, 30);
        bell(s, 10, 50, 11, 30);
        bell(s, 11, 40, 12, 20);
        bell(s, 12, 30, 13, 10);
        bell(s, 13, 20, 14, 0);
        bell(s, 14, 5, 14, 45);
        for (int d = 0; d < DAYS; d++) {
            for (String subject : DEFAULT_LESSONS[d]) {
                s.days.get(d).add(new Item(subject, ""));
            }
        }
        return s;
    }
}

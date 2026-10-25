import com.lessonwidget.Schedule;
import com.lessonwidget.ScheduleLogic;
import com.lessonwidget.ScheduleLogic.State;

/**
 * Проверка логики без Android:
 *   javac -d out app/src/main/java/com/lessonwidget/Schedule.java app/src/main/java/com/lessonwidget/ScheduleLogic.java tools/LogicCheck.java
 *   java -cp out LogicCheck
 */
public class LogicCheck {
    static int failures = 0;
    static int checks = 0;

    static int t(int h, int m, int s) {
        return h * 3600 + m * 60 + s;
    }

    static void eq(String name, Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failures++;
            System.out.println("FAIL " + name + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static String nextOf(State st) {
        return st.next == null ? null : st.next.subject + "#" + st.next.number + "+" + st.next.dayOffset;
    }

    public static void main(String[] args) {
        Schedule s = Schedule.defaults();
        final int MON = 0, TUE = 1, FRI = 4, SAT = 5, SUN = 6;

        // Понедельник до уроков
        State st = ScheduleLogic.compute(s, MON, t(7, 30, 0));
        eq("mon 07:30 kind", ScheduleLogic.BEFORE, st.kind);
        eq("mon 07:30 next", "РОВ#1+0", nextOf(st));
        eq("mon 07:30 bell", t(8, 0, 0), st.bellSec);
        eq("mon 07:30 change", t(8, 0, 0), st.nextChangeSec);

        // Ровно начало первого урока — урок уже идёт
        st = ScheduleLogic.compute(s, MON, t(8, 0, 0));
        eq("mon 08:00:00 kind", ScheduleLogic.LESSON, st.kind);

        // Идёт 1-й урок
        st = ScheduleLogic.compute(s, MON, t(8, 10, 0));
        eq("mon 08:10 kind", ScheduleLogic.LESSON, st.kind);
        eq("mon 08:10 current", "РОВ", st.current.subject);
        eq("mon 08:10 next", "Русский язык#2+0", nextOf(st));
        eq("mon 08:10 bell", t(8, 45, 0), st.bellSec);
        eq("mon 08:10 change", t(8, 45, 0), st.nextChangeSec);

        // Ровно конец урока — уже перемена
        st = ScheduleLogic.compute(s, MON, t(8, 45, 0));
        eq("mon 08:45:00 kind", ScheduleLogic.BREAK, st.kind);
        eq("mon 08:45 bell", t(8, 55, 0), st.bellSec);

        // Перемена между 1 и 2 уроками
        st = ScheduleLogic.compute(s, MON, t(8, 50, 0));
        eq("mon 08:50 kind", ScheduleLogic.BREAK, st.kind);
        eq("mon 08:50 next", "Русский язык#2+0", nextOf(st));
        eq("mon 08:50 bell", t(8, 55, 0), st.bellSec);

        // Последний урок понедельника
        st = ScheduleLogic.compute(s, MON, t(14, 10, 0));
        eq("mon 14:10 kind", ScheduleLogic.LESSON, st.kind);
        eq("mon 14:10 current", "Общество", st.current.subject);
        eq("mon 14:10 next", "Алгебра#1+1", nextOf(st));
        eq("mon 14:10 change", t(14, 45, 0), st.nextChangeSec);

        // После уроков
        st = ScheduleLogic.compute(s, MON, t(14, 45, 0));
        eq("mon 14:45 kind", ScheduleLogic.DONE, st.kind);
        eq("mon 14:45 next", "Алгебра#1+1", nextOf(st));
        eq("mon 14:45 change", -1, st.nextChangeSec);
        eq("mon 14:45 hadLessons", true, st.todayHadLessons);

        // Вторник: 4 урок физра
        st = ScheduleLogic.compute(s, TUE, t(11, 0, 0));
        eq("tue 11:00 kind", ScheduleLogic.LESSON, st.kind);
        eq("tue 11:00 current", "Физра", st.current.subject);
        eq("tue 11:00 next", "Английский#5+0", nextOf(st));

        // Пятница вечером -> понедельник через 3 дня
        st = ScheduleLogic.compute(s, FRI, t(20, 0, 0));
        eq("fri 20:00 kind", ScheduleLogic.DONE, st.kind);
        eq("fri 20:00 next", "РОВ#1+3", nextOf(st));

        // Суббота / воскресенье
        st = ScheduleLogic.compute(s, SAT, t(10, 0, 0));
        eq("sat kind", ScheduleLogic.DONE, st.kind);
        eq("sat hadLessons", false, st.todayHadLessons);
        eq("sat next", "РОВ#1+2", nextOf(st));
        st = ScheduleLogic.compute(s, SUN, t(10, 0, 0));
        eq("sun next", "РОВ#1+1", nextOf(st));

        // Окно: у урока №2 пустой предмет
        Schedule w = Schedule.defaults();
        w.days.get(MON).get(1).subject = "";
        st = ScheduleLogic.compute(w, MON, t(8, 10, 0));
        eq("window: lesson1 next skips 2", "Биология#3+0", nextOf(st));
        st = ScheduleLogic.compute(w, MON, t(9, 0, 0));
        eq("window: during gap kind", ScheduleLogic.BREAK, st.kind);
        eq("window: during gap next", "Биология#3+0", nextOf(st));
        eq("window: during gap bell", t(9, 50, 0), st.bellSec);
        eq("window: during gap change", t(9, 50, 0), st.nextChangeSec);

        // Пустая неделя
        Schedule e = new Schedule();
        e.bells.add(new Schedule.Bell(t(8, 0, 0), t(8, 45, 0)));
        st = ScheduleLogic.compute(e, MON, t(9, 0, 0));
        eq("empty kind", ScheduleLogic.EMPTY, st.kind);

        // Один-единственный урок в неделе, он уже прошёл -> тот же день на следующей неделе
        Schedule one = new Schedule();
        one.bells.add(new Schedule.Bell(t(8, 0, 0), t(8, 45, 0)));
        one.days.get(MON).add(new Schedule.Item("Тест", "101"));
        st = ScheduleLogic.compute(one, MON, t(12, 0, 0));
        eq("one-lesson next week kind", ScheduleLogic.DONE, st.kind);
        eq("one-lesson next week", "Тест#1+7", nextOf(st));
        eq("one-lesson room", "101", st.next.room);

        // Строк больше, чем звонков — лишние игнорируются
        Schedule extra = Schedule.defaults();
        extra.bells.remove(extra.bells.size() - 1);
        st = ScheduleLogic.compute(extra, MON, t(14, 10, 0));
        eq("extra lessons ignored kind", ScheduleLogic.DONE, st.kind);

        eq("fmt", "09:05", ScheduleLogic.fmt(t(9, 5, 30)));

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) {
            System.exit(1);
        }
    }
}

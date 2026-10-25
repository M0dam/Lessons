package com.lessonwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;

/** Редактор: уроки по дням недели и расписание звонков. Всё строится кодом, без XML-раскладок. */
public class MainActivity extends Activity {

    private static final String[] DAY_SHORT = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
    private static final String[] DAY_FULL = {"Понедельник", "Вторник", "Среда", "Четверг",
            "Пятница", "Суббота", "Воскресенье"};
    private static final int BLUE = 0xFF3D6FE0;
    private static final int GRAY = 0xFFE3E6EC;

    private Schedule schedule;
    private int selectedDay;
    private float density;

    private LinearLayout dayBar;
    private TextView dayTitle;
    private LinearLayout lessonsBox;
    private LinearLayout bellsBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        schedule = ScheduleStore.load(this);
        selectedDay = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;
        if (selectedDay > 4) {
            selectedDay = 0; // в выходные открываем понедельник
        }
        buildUi();
        renderAll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persist();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(0xFF1B2230);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        sv.addView(root);

        root.addView(text("Расписание уроков", 24, true));
        TextView hint = text("Номер строки — номер урока. Пустой предмет — «окно». "
                + "Виджет сам выбирает урок по времени звонка. Изменения сохраняются автоматически.", 13, false);
        hint.setTextColor(0xFF5B6475);
        hint.setPadding(0, dp(4), 0, dp(12));
        root.addView(hint);

        dayBar = new LinearLayout(this);
        dayBar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(dayBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dayTitle = text("", 18, true);
        dayTitle.setPadding(0, dp(16), 0, dp(4));
        root.addView(dayTitle);

        lessonsBox = new LinearLayout(this);
        lessonsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(lessonsBox);

        TextView bellsTitle = text("Звонки", 18, true);
        bellsTitle.setPadding(0, dp(24), 0, dp(4));
        root.addView(bellsTitle);
        TextView bellsHint = text("Нажмите на время, чтобы изменить. Одно расписание звонков на все дни.", 13, false);
        bellsHint.setTextColor(0xFF5B6475);
        root.addView(bellsHint);

        bellsBox = new LinearLayout(this);
        bellsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(bellsBox);

        LinearLayout bellButtons = new LinearLayout(this);
        bellButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button add = new Button(this);
        add.setText("+ Добавить урок");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addBell();
            }
        });
        Button remove = new Button(this);
        remove.setText("− Убрать последний");
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeLastBell();
            }
        });
        bellButtons.addView(add, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bellButtons.addView(remove, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(bellButtons);

        Button save = new Button(this);
        save.setText("Сохранить и обновить виджет");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                persist();
                Toast.makeText(MainActivity.this, "Сохранено", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        saveLp.topMargin = dp(24);
        root.addView(save, saveLp);

        Button reset = new Button(this);
        reset.setText("Вернуть исходные данные");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Заменить всё расписание исходным? Кабинеты и правки пропадут.")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Заменить", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                schedule = Schedule.defaults();
                                renderAll();
                                persist();
                            }
                        }).show();
            }
        });
        root.addView(reset);

        setContentView(sv);
    }

    private void renderAll() {
        renderDays();
        renderLessons();
        renderBells();
    }

    private void renderDays() {
        dayBar.removeAllViews();
        for (int d = 0; d < Schedule.DAYS; d++) {
            final int day = d;
            TextView chip = text(DAY_SHORT[d], 15, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(10), 0, dp(10));
            boolean sel = d == selectedDay;
            chip.setBackgroundColor(sel ? BLUE : GRAY);
            chip.setTextColor(sel ? Color.WHITE : 0xFF1B2230);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedDay = day;
                    renderDays();
                    renderLessons();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = d < Schedule.DAYS - 1 ? dp(4) : 0;
            dayBar.addView(chip, lp);
        }
    }

    private abstract static class Watch implements TextWatcher {
        abstract void on(String s);

        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void afterTextChanged(Editable e) {
            on(e.toString());
        }
    }

    private void renderLessons() {
        dayTitle.setText(DAY_FULL[selectedDay]);
        lessonsBox.removeAllViews();
        final List<Schedule.Item> items = schedule.days.get(selectedDay);
        while (items.size() < schedule.bells.size()) {
            items.add(new Schedule.Item());
        }
        for (int i = 0; i < schedule.bells.size(); i++) {
            final Schedule.Item item = items.get(i);
            Schedule.Bell b = schedule.bells.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView num = text((i + 1) + "\n" + ScheduleLogic.fmt(b.start), 12, false);
            num.setGravity(Gravity.CENTER);
            row.addView(num, new LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT));

            EditText subject = new EditText(this);
            subject.setHint("Предмет (пусто — окно)");
            subject.setSingleLine(true);
            subject.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            subject.setText(item.subject);
            subject.addTextChangedListener(new Watch() {
                @Override
                void on(String s) {
                    item.subject = s;
                }
            });
            row.addView(subject, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));

            EditText room = new EditText(this);
            room.setHint("Каб.");
            room.setSingleLine(true);
            room.setText(item.room);
            room.addTextChangedListener(new Watch() {
                @Override
                void on(String s) {
                    item.room = s;
                }
            });
            row.addView(room, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            lessonsBox.addView(row);
        }
    }

    private void renderBells() {
        bellsBox.removeAllViews();
        for (int i = 0; i < schedule.bells.size(); i++) {
            final Schedule.Bell b = schedule.bells.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView num = text("Урок " + (i + 1), 15, false);
            row.addView(num, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            row.addView(timeButton(b, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(timeButton(b, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            bellsBox.addView(row);
        }
    }

    private Button timeButton(final Schedule.Bell b, final boolean isStart) {
        final Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setText((isStart ? "с " : "до ") + ScheduleLogic.fmt(isStart ? b.start : b.end));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = isStart ? b.start : b.end;
                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(android.widget.TimePicker view, int h, int m) {
                        int sec = h * 3600 + m * 60;
                        if (isStart) {
                            b.start = sec;
                        } else {
                            b.end = sec;
                        }
                        renderLessons();
                        renderBells();
                    }
                }, cur / 3600, (cur % 3600) / 60, true).show();
            }
        });
        return btn;
    }

    private void addBell() {
        Schedule.Bell last = schedule.bells.get(schedule.bells.size() - 1);
        int start = Math.min(last.end + 10 * 60, 23 * 3600);
        int end = Math.min(start + 40 * 60, 23 * 3600 + 59 * 60);
        schedule.bells.add(new Schedule.Bell(start, end));
        renderLessons();
        renderBells();
    }

    private void removeLastBell() {
        if (schedule.bells.size() <= 1) {
            return;
        }
        schedule.bells.remove(schedule.bells.size() - 1);
        renderLessons();
        renderBells();
    }

    private void persist() {
        ScheduleStore.save(this, schedule);
        WidgetUpdater.updateAll(this);
    }
}

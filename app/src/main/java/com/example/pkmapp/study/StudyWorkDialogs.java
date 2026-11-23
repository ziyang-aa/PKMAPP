package com.example.pkmapp.study;

import static com.example.pkmapp.forest.ForestUi.*;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.FragmentManager;
import com.example.pkmapp.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;

final class StudyWorkDialogs {
    private final Context context;
    private final FragmentManager fragments;
    private final StudyStore store;
    private final StudySheet sheet;

    StudyWorkDialogs(Context context, FragmentManager fragments, StudyStore store, StudySheet sheet) {
        this.context = context;
        this.fragments = fragments;
        this.store = store;
        this.sheet = sheet;
    }

    StudySheet.Pane pane(String title, String detail) {
        return new StudySheet.Pane(context, title, detail, R.drawable.ic_study_book);
    }

    EditText input(LinearLayout parent, String label, String hint, String value, int limit) {
        add(parent, StudySheet.section(context, label), parent.getChildCount() == 0 ? 0 : 12);
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(hint);
        input.setContentDescription(label);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(limit) });
        input.setText(value);
        StudySheet.styleField(input);
        add(parent, input, 5);
        return input;
    }

    static String value(EditText input) { return input.getText().toString().trim(); }

    static long duration(EditText input, long original) {
        String value = value(input);
        return original > 0 && value.equals(StudyFormat.clock(original)) ? original : StudyFormat.parseClock(value);
    }

    static MaterialCheckBox check(Context context, String label) {
        return com.example.pkmapp.forest.ForestUi.check(context, label, false);
    }

    static MaterialCheckBox switchCheck(Context context, String label) {
        return com.example.pkmapp.forest.ForestUi.check(context, label, true);
    }

    MaterialButton choiceButton(String label) {
        MaterialButton button = button(context, label, false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(context, 48));
        button.setSingleLine(false);
        return button;
    }

    static final class SubjectChoice {
        StudySubject selected;
        final MaterialButton button;

        SubjectChoice(StudySubject selected, MaterialButton button) {
            this.selected = selected;
            this.button = button;
        }
    }

    SubjectChoice chooseSubject(LinearLayout parent, StudyPlan plan, StudySubject selected) {
        MaterialButton button = choiceButton("科目 · " + selected.title + " ▾");
        SubjectChoice choice = new SubjectChoice(selected, button);
        button.setOnClickListener(v -> subjectList(plan, choice.selected, subject -> {
            choice.selected = subject;
            button.setText("科目 · " + subject.title + " ▾");
        }));
        add(parent, button, 6);
        return choice;
    }

    private void subjectList(StudyPlan plan, StudySubject selected, Consumer<StudySubject> accept) {
        StudySheet.Pane subjects = pane("选择科目", plan.title);
        for (StudySubject subject : plan.books()) {
            boolean checked = subject.equals(selected);
            add(subjects.body, StudySheet.item(context, subject.title, subject.hint, 0,
                checked ? StudySheet.MOSS : 0, checked ? "✓" : null, checked, () -> {
                    accept.accept(subject);
                    sheet.pop();
                }), 4);
        }
        sheet.push(subjects);
    }

    static final class DateChoice {
        String day;
        Consumer<String> apply;
        DateChoice(String day) { this.day = day; }
    }

    DateChoice chooseDate(LinearLayout parent, String label, String current, boolean optional) {
        DateChoice date = new DateChoice(current);
        MaterialButton button = choiceButton(label + " · " + (current.isEmpty() ? "未设置" : current));
        Consumer<String> select = day -> {
            date.day = day;
            button.setText(label + " · " + (day.isEmpty() ? "未设置" : day));
        };
        date.apply = select;
        button.setOnClickListener(v -> calendar(label, date.day, select));
        add(parent, button, 6);
        if (optional) add(parent, StudySheet.link(context, "清除日期", MUTED, () -> select.accept("")), 0);
        return date;
    }

    private void calendar(String label, String day, Consumer<String> accept) {
        sheet.hideKeyboard();
        sheet.pushDatePicker(label, day, accept);
    }

    void recordForm(StudyPlan initial, StudyNotebook.Record record, Runnable saved) {
        boolean manual = record == null || record.manual;
        StudySheet.Pane pane = pane(record == null ? "补记学习" : "编辑学习记录",
            manual ? "补记计入学习统计" : "计时按完成日计入足迹");
        StudyPlan[] plan = { initial };
        StudySubject first = record == null ? initial.book(initial.selected) : record.subject;
        MaterialButton planButton = choiceButton(initial.title + " ▾");
        if (record == null) add(pane.body, planButton, 0);
        LinearLayout subjectSlot = column(context);
        pane.body.addView(subjectSlot);
        SubjectChoice[] subject = { chooseSubject(subjectSlot, initial, first) };
        planButton.setOnClickListener(v -> {
            StudySheet.Pane plans = pane("选择学习计划", null);
            for (StudyPlan candidate : store.notebook().plans()) {
                add(plans.body, StudySheet.item(context, candidate.title,
                    candidate.archived == 0 ? null : "已归档", 0, 0, "›", false, () -> {
                        plan[0] = candidate;
                        planButton.setText(candidate.title + " ▾");
                        subjectSlot.removeAllViews();
                        subject[0] = chooseSubject(subjectSlot, candidate, candidate.book(candidate.selected));
                        sheet.pop();
                    }), 4);
            }
            sheet.push(plans);
        });
        DateChoice date = manual ? chooseDate(pane.body, "学习日期", StudyNotebook.dayKey(
            record == null ? System.currentTimeMillis() : record.completedAt), false) : null;
        long before = record == null ? 0 : record.durationMillis;
        EditText time = input(pane.body, "学习时长（时:分:秒）", "例如 00:40:00",
            record == null ? "" : StudyFormat.clock(before), 16);
        EditText note = input(pane.body, "学习手记（选填）", "记一件学到的事", record == null ? "" : record.note, 120);
        pane.primary(record == null ? "保存补记" : "保存修改", () -> save(() -> {
            long now = System.currentTimeMillis();
            long when = record == null ? now : record.completedAt;
            if (date != null && date.day.compareTo(StudyNotebook.dayKey(now)) > 0)
                throw new IllegalArgumentException("学习日期不能晚于今天");
            if (date != null && !StudyNotebook.dayKey(when).equals(date.day)) {
                Calendar day = StudyFormat.day(date.day);
                day.set(Calendar.HOUR_OF_DAY, 12);
                when = day.getTimeInMillis();
            }
            long duration = duration(time, before);
            long completedAt = when;
            store.update(n -> record == null
                ? n.addRecord(plan[0].id, subject[0].selected.id, duration, completedAt, value(note))
                : n.editRecord(record.id, subject[0].selected.id, duration, completedAt, value(note), now));
            saved.run();
        }));
        sheet.push(pane);
    }

    void taskForm(StudyPlan plan, StudySubject initial, StudyWork.Task task, Runnable saved, Runnable start) {
        StudySheet.Pane pane = pane(task == null ? "新建待办" : "编辑待办", plan.title);

        // 1. Borderless editorial title input (Things 3 / Todoist style, no boxy frame)
        EditText title = new EditText(context);
        title.setSingleLine(false);
        title.setMaxLines(3);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setHint("准备做哪件事？例如 刷一套数据库模拟卷");
        title.setContentDescription("这次要做什么");
        title.setFilters(new InputFilter[] { new InputFilter.LengthFilter(120) });
        title.setText(task == null ? "" : task.title);
        title.setTextSize(17.5f);
        title.setTextColor(0xff243624);
        title.setHintTextColor(0xff9aa094);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setBackground(null);
        title.setPadding(dp(context, 2), dp(context, 6), dp(context, 2), dp(context, 10));
        pane.body.addView(title, new LinearLayout.LayoutParams(-1, -2));

        android.view.View titleRule = new android.view.View(context);
        titleRule.setBackgroundColor(0xffe5e1d2);
        pane.body.addView(titleRule, new LinearLayout.LayoutParams(-1, dp(context, 1)));

        // State holders
        StudySubject[] chosenSubject = { initial };
        String todayKey = StudyNotebook.dayKey(System.currentTimeMillis());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        String tomorrowKey = StudyNotebook.dayKey(cal.getTimeInMillis());
        cal.add(Calendar.DATE, 1);
        String afterTomorrowKey = StudyNotebook.dayKey(cal.getTimeInMillis());
        String[] chosenDay = { task == null ? todayKey : task.day };
        String[] chosenTime = { task == null ? "" : task.dueTime };
        int[] leadMinutes = { task == null ? 30 : task.remindLeadMinutes };
        boolean initialRemind = task != null && task.remind;

        // 2. Compact horizontal quick-property pill strip (Subject · Date · Time)
        android.widget.HorizontalScrollView pillScroll = new android.widget.HorizontalScrollView(context);
        pillScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout pillRow = row(context);

        TextView subjectPill = StudySheet.link(context, "科目 · " + chosenSubject[0].title + " ▾", 0xff243624, () -> {});
        subjectPill.setTextSize(12);
        subjectPill.setMinimumHeight(dp(context, 34));
        subjectPill.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        subjectPill.setBackground(ripple(context, rounded(context, 0xfff3efe3, 0xffdcd8c8, 999), false));
        if (task == null) {
            subjectPill.setOnClickListener(v -> subjectList(plan, chosenSubject[0], s -> {
                chosenSubject[0] = s;
                subjectPill.setText("科目 · " + s.title + " ▾");
            }));
        }
        pillRow.addView(subjectPill);

        TextView datePill = StudySheet.link(context, "", 0xff2e4f2f, () -> {});
        datePill.setTextSize(12);
        datePill.setMinimumHeight(dp(context, 34));
        datePill.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        LinearLayout.LayoutParams datePillLp = new LinearLayout.LayoutParams(-2, -2);
        datePillLp.setMarginStart(dp(context, 8));
        pillRow.addView(datePill, datePillLp);

        TextView timePill = StudySheet.link(context, "", 0xff4c594a, () -> {});
        timePill.setTextSize(12);
        timePill.setMinimumHeight(dp(context, 34));
        timePill.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        LinearLayout.LayoutParams timePillLp = new LinearLayout.LayoutParams(-2, -2);
        timePillLp.setMarginStart(dp(context, 8));
        pillRow.addView(timePill, timePillLp);

        pillScroll.addView(pillRow);
        add(pane.body, pillScroll, 12);

        // 3. Collapsible Date & Time inline panels (expand on pill tap, no 10 stacked boxes!)
        LinearLayout datePanel = column(context);
        datePanel.setVisibility(android.view.View.GONE);
        LinearLayout dayPresetsRow = row(context);
        String[][] dayPresets = {
            {"今天", todayKey}, {"明天", tomorrowKey}, {"后天", afterTomorrowKey}, {"选日期", "CAL"}, {"不限", ""}
        };

        LinearLayout timePanel = column(context);
        timePanel.setVisibility(android.view.View.GONE);
        LinearLayout timePresetsRow = row(context);
        EditText dueTimeInput = new EditText(context);
        dueTimeInput.setSingleLine(true);
        dueTimeInput.setInputType(InputType.TYPE_CLASS_TEXT);
        dueTimeInput.setHint("自定义截止时刻，例如 19:30");
        dueTimeInput.setText(chosenTime[0]);
        StudySheet.styleField(dueTimeInput);

        Runnable syncPills = () -> {
            String dLabel;
            if (chosenDay[0].isEmpty()) dLabel = "不限日期 ▾";
            else if (chosenDay[0].equals(todayKey)) dLabel = "今天 ▾";
            else if (chosenDay[0].equals(tomorrowKey)) dLabel = "明天 ▾";
            else if (chosenDay[0].equals(afterTomorrowKey)) dLabel = "后天 ▾";
            else dLabel = chosenDay[0] + " ▾";
            datePill.setText(dLabel);
            boolean hasDay = !chosenDay[0].isEmpty();
            datePill.setBackground(ripple(context, rounded(context,
                hasDay ? 0xffe6efe2 : 0xfff3efe3,
                hasDay ? 0xffb8cbb2 : 0xffdcd8c8, 999), false));

            String tVal = dueTimeInput.getText().toString().trim();
            chosenTime[0] = tVal;
            boolean hasTime = !tVal.isEmpty();
            timePill.setText(hasTime ? "截止 " + tVal + " ▾" : "设截止时间 ▾");
            timePill.setTextColor(hasTime ? 0xff8d4836 : 0xff4c594a);
            timePill.setBackground(ripple(context, rounded(context,
                hasTime ? 0xfff9efea : 0xfff3efe3,
                hasTime ? 0xffe6cfc5 : 0xffdcd8c8, 999), false));
        };

        for (int i = 0; i < dayPresets.length; i++) {
            String lbl = dayPresets[i][0];
            String val = dayPresets[i][1];
            TextView chip = StudySheet.link(context, lbl, DEEP, () -> {
                if ("CAL".equals(val)) {
                    calendar("安排日期", chosenDay[0], picked -> {
                        chosenDay[0] = picked;
                        syncPills.run();
                        datePanel.setVisibility(android.view.View.GONE);
                    });
                } else {
                    chosenDay[0] = val;
                    syncPills.run();
                    datePanel.setVisibility(android.view.View.GONE);
                }
            });
            chip.setTextSize(12);
            chip.setMinimumHeight(dp(context, 34));
            chip.setBackground(ripple(context, rounded(context, 0xfff3efe3, 0xffdcd8c8, 10), false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(context, 5));
            dayPresetsRow.addView(chip, lp);
        }
        datePanel.addView(dayPresetsRow);
        add(pane.body, datePanel, 8);

        String[] timePresets = {"12:00", "18:00", "21:00", "22:30", "不限"};
        for (int i = 0; i < timePresets.length; i++) {
            String preset = timePresets[i];
            TextView chip = StudySheet.link(context, preset, DEEP, () -> {
                dueTimeInput.setText("不限".equals(preset) ? "" : preset);
                syncPills.run();
                timePanel.setVisibility(android.view.View.GONE);
            });
            chip.setTextSize(12);
            chip.setMinimumHeight(dp(context, 34));
            chip.setBackground(ripple(context, rounded(context, 0xfff3efe3, 0xffdcd8c8, 10), false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(context, 5));
            timePresetsRow.addView(chip, lp);
        }
        timePanel.addView(timePresetsRow);
        add(timePanel, dueTimeInput, 8);
        add(pane.body, timePanel, 8);

        datePill.setOnClickListener(v -> {
            boolean show = datePanel.getVisibility() != android.view.View.VISIBLE;
            datePanel.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            if (show) timePanel.setVisibility(android.view.View.GONE);
        });
        timePill.setOnClickListener(v -> {
            boolean show = timePanel.getVisibility() != android.view.View.VISIBLE;
            timePanel.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            if (show) datePanel.setVisibility(android.view.View.GONE);
        });
        dueTimeInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { syncPills.run(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        syncPills.run();

        // 4. Clean hairline reminder toggle + inline lead-time strip (no nested card box!)
        StudySheet.addHairline(pane.body, 12, 2);

        MaterialCheckBox remindSwitch = com.example.pkmapp.forest.ForestUi.flatSwitchRow(
            context,
            "在截止时间前发送消息提醒",
            remindSubtitle(initialRemind, leadMinutes[0], plan.remindTime)
        );
        remindSwitch.setChecked(initialRemind);
        pane.body.addView(remindSwitch, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout leadRow = row(context);
        int[] leadValues = {15, 30, 60, 1440};
        String[] leadLabels = {"提前15分", "提前30分", "提前1小时", "前一天"};
        List<TextView> leadButtons = new ArrayList<>();
        Runnable updateLeadUi = () -> {
            boolean on = remindSwitch.isChecked();
            leadRow.setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE);
            com.example.pkmapp.forest.ForestUi.updateFlatSwitchSubtitle(
                remindSwitch,
                remindSubtitle(on, leadMinutes[0], plan.remindTime)
            );
            for (int i = 0; i < leadButtons.size(); i++) {
                StudySheet.styleChoiceChip(leadButtons.get(i), leadMinutes[0] == leadValues[i]);
            }
        };
        for (int i = 0; i < leadValues.length; i++) {
            int val = leadValues[i];
            TextView btn = StudySheet.link(context, leadLabels[i], DEEP, () -> {
                leadMinutes[0] = val;
                updateLeadUi.run();
            });
            btn.setTextSize(11);
            btn.setMinimumHeight(dp(context, 32));
            btn.setPadding(dp(context, 6), dp(context, 5), dp(context, 6), dp(context, 5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(context, 6));
            leadRow.addView(btn, lp);
            leadButtons.add(btn);
        }
        remindSwitch.setOnCheckedChangeListener((b, checked) -> updateLeadUi.run());
        updateLeadUi.run();
        add(pane.body, leadRow, 4);

        if (task != null) {
            LinearLayout extraRow = row(context);
            if (start != null) {
                TextView startBtn = StudySheet.link(context, "开始这项学习", DEEP, start);
                startBtn.setMinimumHeight(dp(context, 36));
                extraRow.addView(startBtn, new LinearLayout.LayoutParams(0, -2, 1));
            }
            TextView pinBtn = StudySheet.link(context, "设为今天第一项", DEEP, () -> save(() -> {
                store.update(n -> { n.prioritizeTask(task.id, System.currentTimeMillis()); return null; });
                saved.run();
            }));
            pinBtn.setMinimumHeight(dp(context, 36));
            extraRow.addView(pinBtn, new LinearLayout.LayoutParams(0, -2, 1));

            TextView delBtn = StudySheet.link(context, "删除", 0xff8d4836, () -> save(() -> {
                store.update(n -> { n.deleteTask(task.id); return null; });
                saved.run();
            }));
            delBtn.setMinimumHeight(dp(context, 36));
            extraRow.addView(delBtn, new LinearLayout.LayoutParams(-2, -2));
            add(pane.body, extraRow, 10);

            pane.secondary("标记完成", () -> save(() -> {
                String cleanTime = StudyWork.Task.validateTime(value(dueTimeInput));
                store.update(n -> {
                    n.changeTask(task.id, value(title), chosenDay[0], cleanTime,
                        remindSwitch.isChecked(), leadMinutes[0], true, System.currentTimeMillis());
                    return null;
                });
                saved.run();
            }));
        }
        pane.primary("保存事项", () -> save(() -> {
            String cleanTime = StudyWork.Task.validateTime(value(dueTimeInput));
            store.update(n -> {
                long now = System.currentTimeMillis();
                if (task == null) {
                    n.putTask(plan.id, chosenSubject[0].id, value(title), chosenDay[0],
                        cleanTime, remindSwitch.isChecked(), leadMinutes[0], now, false);
                } else {
                    n.changeTask(task.id, value(title), chosenDay[0], cleanTime,
                        remindSwitch.isChecked(), leadMinutes[0], false, now);
                }
                return null;
            });
            saved.run();
        }));
        sheet.push(pane);
    }

    private static String remindSubtitle(boolean enabled, int leadMinutes, String defaultTime) {
        if (!enabled) return "已关闭 · 开启后在截止时间前发送系统提醒";
        if (leadMinutes >= 1440) return "已开启 · 截止前一天 " + defaultTime + " 发送提醒";
        if (leadMinutes >= 60 && leadMinutes % 60 == 0)
            return "已开启 · 截止前 " + (leadMinutes / 60) + " 小时发送提醒";
        return "已开启 · 截止前 " + leadMinutes + " 分钟发送提醒";
    }

    void reviewForm(StudyNotebook.Record record, StudyWork.Review review, Runnable saved) {
        StudySheet.Pane pane = pane(review == null ? "加入待回顾" : "改约回顾", record.subject.title);
        EditText prompt = input(pane.body, "回顾问题", "例如 不看笔记，解释 B 树和 B+ 树的区别",
            review == null ? "回想这段学习的要点" : review.prompt, 120);
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DATE, 1);
        DateChoice date = chooseDate(pane.body, "回顾日期",
            StudyNotebook.dayKey(tomorrow.getTimeInMillis()), false);
        pane.primary("保存回顾", () -> save(() -> {
            store.update(n -> {
                n.changeReview(record.id, value(prompt), date.day, false, System.currentTimeMillis());
                return null;
            });
            saved.run();
        }));
        sheet.push(pane);
    }

    static List<StudySubject> deadlines(StudyPlan plan) {
        List<StudySubject> result = new ArrayList<>();
        String today = StudyNotebook.dayKey(System.currentTimeMillis());
        for (StudySubject subject : plan.books())
            if (!subject.due.isEmpty() && subject.due.compareTo(today) >= 0) result.add(subject);
        result.sort((a, b) -> a.due.compareTo(b.due));
        return result;
    }

    void save(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException error) {
            Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

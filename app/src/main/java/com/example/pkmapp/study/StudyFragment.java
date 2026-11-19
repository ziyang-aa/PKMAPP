package com.example.pkmapp.study;

import static com.example.pkmapp.forest.ForestUi.*;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.forest.ForestDialogBuilder;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.ForestSurface;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.navigation.AppDestination;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StudyFragment extends Fragment {

    private StudyStore store;
    private AppDestination page;
    private ScrollView scroll;
    private LinearLayout body;
    private TextView clock, today, wallet;
    private StudyClockFace dial;
    private StudyGardenView garden;
    private StudyGrowthProgress growthProgress;
    private MaterialButton waterButton;
    private MaterialButton subjectSelector;
    private BottomSheetDialog subjectSheet;
    private long lastControlClick;
    private int days = 7;
    private StudyChartView.Kind chart = StudyChartView.Kind.RING;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            handler.postDelayed(this, 1000);
        }
    };

    public static StudyFragment newInstance(AppDestination page) {
        StudyFragment fragment = new StudyFragment();
        Bundle args = new Bundle();
        args.putString("page", page.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        page = AppDestination.valueOf(
            requireArguments().getString("page", AppDestination.STUDY_FOCUS.name())
        );
        if (saved != null) {
            days = saved.getInt("days", 7);
            chart = StudyChartView.Kind.valueOf(
                saved.getString("chart", StudyChartView.Kind.RING.name())
            );
        }
    }

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup parent,
        @Nullable Bundle saved
    ) {
        store = StudyStore.get(requireContext());
        scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(requireContext(), 18));
        scroll.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            applyScrollInsets(insets);
            return insets;
        });
        androidx.core.view.WindowInsetsCompat initialInsets =
            androidx.core.view.ViewCompat.getRootWindowInsets(
                requireActivity().getWindow().getDecorView()
            );
        if (initialInsets != null) applyScrollInsets(initialInsets);
        LinearLayout host = column(requireContext());
        host.setClipChildren(false);
        scroll.addView(host);
        LinearLayout bar = row(requireContext());
        bar.setPadding(
            dp(requireContext(), 15),
            dp(requireContext(), 5),
            dp(requireContext(), 18),
            0
        );
        ImageButton back = new ImageButton(requireContext());
        back.setId(R.id.page_back_button);
        back.setImageResource(R.drawable.return_home_door);
        back.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        back.setBackgroundColor(0);
        back.setPadding(
            dp(requireContext(), 4),
            dp(requireContext(), 4),
            dp(requireContext(), 4),
            dp(requireContext(), 4)
        );
        back.setContentDescription("返回森林入口");
        back.setOnClickListener(v -> open(AppDestination.HOME));
        ForestMotion.bind(back);
        back.setBackground(
            ForestUi.ripple(requireContext(), rounded(requireContext(), 0, 0, 12), false)
        );
        bar.addView(
            back,
            new LinearLayout.LayoutParams(dp(requireContext(), 44), dp(requireContext(), 44))
        );
        TextView home = text(requireContext(), "森林小窝", 11, WOOD);
        bar.addView(home, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tag = text(requireContext(), "学习小屋", 11, DEEP);
        tag.setBackground(rounded(requireContext(), 0xffe8eddb, 0xffccd5b9, 18));
        tag.setPadding(
            dp(requireContext(), 10),
            dp(requireContext(), 5),
            dp(requireContext(), 10),
            dp(requireContext(), 5)
        );
        bar.addView(tag);
        host.addView(bar);
        body = column(requireContext());
        body.setClipChildren(false);
        host.addView(body);
        render();
        return scroll;
    }

    private Context ctx() {
        return requireContext();
    }

    private void applyScrollInsets(androidx.core.view.WindowInsetsCompat insets) {
        androidx.core.graphics.Insets bars = insets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars() |
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        );
        // Padding positions initial/final content safely; clipToPadding=false still lets content
        // pass beneath status icons and the floating navigation while the page scrolls.
        scroll.setPadding(0, bars.top, 0, bars.bottom + dp(requireContext(), 94));
    }

    private void open(AppDestination destination) {
        ((MainActivity) requireActivity()).showDestination(destination);
    }

    private void toast(String value) {
        Toast.makeText(ctx(), value, Toast.LENGTH_SHORT).show();
    }

    private void perform(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException error) {
            toast(error.getMessage());
        }
    }

    private void render() {
        if (body == null) return;
        int y = scroll.getScrollY();
        if (garden != null) garden.setActive(false);
        body.removeAllViews();
        clock = null;
        today = null;
        dial = null;
        garden = null;
        growthProgress = null;
        wallet = null;
        waterButton = null;
        subjectSelector = null;
        if (page == AppDestination.STUDY_FOCUS) focus();
        else if (page == AppDestination.STUDY_STATS) statistics();
        else garden();
        updateTimer();
        if (garden != null && isResumed()) garden.setActive(true);
        scroll.post(() -> {
            if (scroll != null) scroll.scrollTo(0, y);
        });
    }

    private LinearLayout content() {
        LinearLayout result = column(ctx());
        result.setPadding(dp(ctx(), 19), 0, dp(ctx(), 19), 0);
        return result;
    }

    private LinearLayout card() {
        LinearLayout card = column(ctx());
        card.setPadding(dp(ctx(), 16), dp(ctx(), 15), dp(ctx(), 16), dp(ctx(), 15));
        card.setBackground(new com.example.pkmapp.forest.CrayonCard());
        return card;
    }

    private void section(LinearLayout parent, String title, String detail) {
        LinearLayout row = row(ctx());
        row.addView(heading(ctx(), title, 17), new LinearLayout.LayoutParams(0, -2, 1));
        if (detail != null) row.addView(text(ctx(), detail, 10, MUTED));
        ForestUi.add(parent, row, 20);
    }

    private TextView note(String value) {
        TextView note = text(ctx(), value, 12, MUTED);
        note.setLineSpacing(dp(ctx(), 4), 1);
        return note;
    }

    private void addEqual(LinearLayout row, View child, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(ctx(), height), 1);
        lp.setMargins(dp(ctx(), 3), 0, dp(ctx(), 3), 0);
        row.addView(child, lp);
    }

    private void focus() {
        StudyNotebook n = store.notebook();
        FrameLayout hero = new FrameLayout(ctx());
        LinearLayout copy = column(ctx());
        copy.setGravity(Gravity.START);
        copy.addView(
            text(
                ctx(),
                new SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(new java.util.Date()),
                10,
                WOOD
            )
        );
        ForestUi.add(copy, heading(ctx(), "森林自习室", 24), 5);
        TextView welcome = text(ctx(), "让知识和小树，慢慢长大。", 12, WOOD);
        welcome.setGravity(Gravity.END);
        welcome.setLineSpacing(dp(ctx(), 4), 1);
        ForestUi.add(copy, welcome, 6);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            -2,
            -2,
            Gravity.TOP | Gravity.START
        );
        lp.setMargins(dp(ctx(), 22), dp(ctx(), 10), 0, 0);
        hero.addView(copy, lp);
        body.addView(hero, new LinearLayout.LayoutParams(-1, dp(ctx(), 104)));
        LinearLayout content = content();
        body.addView(content);
        LinearLayout timer = card();
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(-1, -2);
        timerParams.topMargin = dp(ctx(), 2);
        content.addView(timer, timerParams);
        String subjectLabel =
            n.activeSubject().title + (n.hasSession() ? "  · 本次科目" : "    切换科目 ›");
        android.text.SpannableString selectorText = new android.text.SpannableString(subjectLabel);
        selectorText.setSpan(
            new android.text.style.RelativeSizeSpan(.8f),
            n.activeSubject().title.length(),
            subjectLabel.length(),
            0
        );
        selectorText.setSpan(
            new android.text.style.ForegroundColorSpan(MUTED),
            n.activeSubject().title.length(),
            subjectLabel.length(),
            0
        );
        subjectSelector = button(ctx(), subjectLabel, false);
        subjectSelector.setText(selectorText);
        subjectSelector.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        subjectSelector.setIconPadding(dp(ctx(), 12));
        subjectSelector.setId(R.id.study_subject_selector);
        subjectSelector.setContentDescription("切换学习科目");
        subjectSelector.setIconResource(R.drawable.ic_study_book);
        subjectSelector.setIconTint(ColorStateList.valueOf(DEEP));
        subjectSelector.setIconSize(dp(ctx(), 20));
        subjectSelector.setBackgroundTintList(ColorStateList.valueOf(0));
        subjectSelector.setStrokeWidth(0);
        subjectSelector.setEnabled(!n.hasSession());
        subjectSelector.setAlpha(n.hasSession() ? .85f : 1f);
        subjectSelector.setOnClickListener(v -> showSubjectSheet());
        timer.addView(subjectSelector, new LinearLayout.LayoutParams(-1, dp(ctx(), 48)));
        FrameLayout dialHost = new FrameLayout(ctx());
        dial = new StudyClockFace();
        dialHost.setBackground(dial);
        LinearLayout dialCopy = column(ctx());
        dialCopy.setGravity(Gravity.CENTER);
        TextView dialLabel = text(
            ctx(),
            n.hasSession() ? (n.running() ? "正在专注" : "休息一下") : "准备好就开始",
            11,
            MUTED
        );
        dialLabel.setGravity(Gravity.CENTER);
        dialCopy.addView(dialLabel);
        clock = text(ctx(), StudyFormat.clock(store.elapsedMillis()), 27, DEEP);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setFontFeatureSettings("tnum");
        clock.setId(R.id.study_timer);
        clock.setGravity(Gravity.CENTER);
        clock.setContentDescription("本次学习计时");
        ForestUi.add(dialCopy, clock, 10);
        TextView subject = text(ctx(), "目标 " + n.activeGoal() + " 分钟", 12, MUTED);
        subject.setGravity(Gravity.CENTER);
        ForestUi.add(dialCopy, subject, 9);
        dialHost.addView(dialCopy, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(-1, dp(ctx(), 164));
        dialParams.topMargin = dp(ctx(), 3);
        timer.addView(dialHost, dialParams);
        LinearLayout goals = row(ctx());
        goals.addView(text(ctx(), "本次目标", 10, MUTED));
        for (int minutes : new int[] { 25, 45, 60 }) {
            MaterialButton chip = button(ctx(), minutes + "分钟", false);
            chip.setTextSize(11);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setEnabled(!n.hasSession());
            chip.setBackgroundTintList(
                ColorStateList.valueOf(n.activeGoal() == minutes ? 0xffdfe9ce : 0xfff1efe3)
            );
            chip.setOnClickListener(v ->
                perform(() -> {
                    store.update(book -> {
                        book.setSessionGoal(minutes);
                        return null;
                    });
                    render();
                })
            );
            addEqual(goals, chip, 35);
        }
        android.widget.ImageView meadow = image(ctx(), R.drawable.crayon_study_meadow, null);
        timer.addView(meadow, new LinearLayout.LayoutParams(-1, dp(ctx(), 70)));
        ForestUi.add(timer, goals, 2);
        LinearLayout controls = row(ctx());
        MaterialButton start = button(
            ctx(),
            n.hasSession() ? (n.running() ? "暂停一下" : "继续学习") : "开始专注，种下今天",
            true
        );
        start.setId(R.id.study_start_pause_button);
        start.setOnClickListener(v ->
            perform(() -> {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastControlClick < 280) return;
                lastControlClick = now;
                if (!store.notebook().hasSession()) store.start();
                else if (store.notebook().running()) store.pause();
                else store.resume();
                render();
                View action = body.findViewById(R.id.study_start_pause_button);
                if (action != null) ForestMotion.reveal(action);
            })
        );
        controls.addView(start, new LinearLayout.LayoutParams(0, dp(ctx(), 48), 1));
        if (n.hasSession()) {
            MaterialButton finish = button(ctx(), "完成学习", false);
            finish.setId(R.id.study_finish_button);
            finish.setOnClickListener(v -> perform(this::finishDialog));
            LinearLayout.LayoutParams finishParams = new LinearLayout.LayoutParams(
                dp(ctx(), 98),
                dp(ctx(), 48)
            );
            finishParams.leftMargin = dp(ctx(), 8);
            controls.addView(finish, finishParams);
        }
        ForestUi.add(timer, controls, 12);
        TextView gentle = text(
            ctx(),
            n.hasSession()
                ? "暂停时间不计入时长 · 完成后可以切换科目"
                : "正向计时 · 随时暂停 · 到达目标仍可继续",
            11,
            MUTED
        );
        gentle.setGravity(Gravity.CENTER);
        ForestUi.add(timer, gentle, 9);
        LinearLayout summary = row(ctx());
        today = text(ctx(), "", 13, DEEP);
        today.setId(R.id.study_today_summary);
        today.setLineSpacing(dp(ctx(), 5), 1);
        summary.addView(today, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton goal = button(ctx(), "调整目标", false);
        goal.setId(R.id.study_daily_goal_button);
        goal.setTextSize(11);
        goal.setOnClickListener(v -> dailyGoalDialog());
        summary.addView(goal);
        ForestUi.add(content, summary, 19);
    }

    private void showSubjectSheet() {
        if (store.notebook().hasSession() || subjectSheet != null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(ctx());
        subjectSheet = dialog;
        ScrollView sheetScroll = new ScrollView(ctx());
        LinearLayout picker = column(ctx());
        picker.setId(R.id.study_subject_picker);
        picker.setPadding(dp(ctx(), 20), dp(ctx(), 12), dp(ctx(), 20), dp(ctx(), 28));
        View handle = new View(ctx());
        handle.setBackground(rounded(ctx(), 0xffced5c0, 0, 3));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
            dp(ctx(), 30),
            dp(ctx(), 3)
        );
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        picker.addView(handle, handleParams);
        ForestUi.add(picker, heading(ctx(), "今天，想学点什么？", 23), 22);
        ForestUi.add(picker, note("选择一本书，从这一小段专注开始。"), 10);
        LinearLayout general = row(ctx());
        for (int i = 0; i < 3; i++) addEqual(general, subjectChoice(StudySubject.values()[i]), 76);
        ForestUi.add(picker, general, 22);
        ForestUi.add(picker, note("408 计算机学科专业基础"), 22);
        for (int row = 0; row < 2; row++) {
            LinearLayout professional = row(ctx());
            for (int i = 0; i < 2; i++) addEqual(
                professional,
                subjectChoice(StudySubject.values()[3 + row * 2 + i]),
                76
            );
            ForestUi.add(picker, professional, 10);
        }
        sheetScroll.addView(picker);
        dialog.setContentView(sheetScroll);
        dialog.setOnDismissListener(ignored -> subjectSheet = null);
        dialog.setOnShowListener(ignored -> {
            dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            dialog
                .getBehavior()
                .setMaxHeight((int) (getResources().getDisplayMetrics().heightPixels * .85f));
        });
        dialog.show();
    }

    private MaterialButton subjectChoice(StudySubject subject) {
        StudyNotebook n = store.notebook();
        MaterialButton choice = button(ctx(), subject.title + "\n" + subject.hint, false);
        android.text.SpannableString label = new android.text.SpannableString(
            subject.title + "\n" + subject.hint
        );
        int hintStart = subject.title.length() + 1;
        label.setSpan(new android.text.style.RelativeSizeSpan(.85f), hintStart, label.length(), 0);
        label.setSpan(
            new android.text.style.ForegroundColorSpan(MUTED),
            hintStart,
            label.length(),
            0
        );
        choice.setText(label);
        choice.setTextSize(13);
        choice.setMinWidth(0);
        choice.setMinimumWidth(0);
        choice.setMaxLines(2);
        choice.setLetterSpacing(0);
        choice.setBackgroundTintList(
            ColorStateList.valueOf(n.selected() == subject ? 0xffe4ecd7 : LIGHT)
        );
        choice.setStrokeColor(ColorStateList.valueOf(n.selected() == subject ? 0xff94ac7c : LINE));
        choice.setEnabled(!n.hasSession());
        choice.setContentDescription("选择" + subject.title);
        ForestUi.bookmark(choice, subject.color, n.selected() == subject);
        choice.setOnClickListener(v ->
            perform(() -> {
                store.update(book -> {
                    book.select(subject);
                    return null;
                });
                if (subjectSheet != null) subjectSheet.dismiss();
                render();
                if (subjectSelector != null) ForestMotion.reveal(subjectSelector);
            })
        );
        return choice;
    }

    private void updateTimer() {
        if (clock != null) clock.setText(StudyFormat.clock(store.elapsedMillis()));
        if (dial != null) dial.setProgress(
            (float) store.elapsedMillis() / (store.notebook().activeGoal() * 60_000L)
        );
        if (today != null) {
            StudyNotebook n = store.notebook();
            int count = 0;
            String day = StudyNotebook.dayKey(System.currentTimeMillis());
            for (StudyNotebook.Record r : n.records())
                if (StudyNotebook.dayKey(r.completedAt).equals(day)) count++;
            today.setText(
                "今日已专注 " +
                    StudyFormat.duration(n.totalForDay(System.currentTimeMillis())) +
                    "\n每日目标 " +
                    n.dailyGoal() +
                    "分钟 · " +
                    count +
                    "次学习"
            );
        }
    }

    private void dailyGoalDialog() {
        EditText input = new EditText(ctx());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(store.notebook().dailyGoal()));
        input.setSelectAllOnFocus(true);
        input.setContentDescription("每日学习目标分钟");
        LinearLayout view = column(ctx());
        view.setPadding(dp(ctx(), 24), dp(ctx(), 8), dp(ctx(), 24), 0);
        view.addView(note("目标只是温柔的路标。每天达标送一颗种子，每天最多一次。"));
        ForestUi.add(view, input, 12);
        TextView errorMessage = text(ctx(), "", 11, 0xffa5664c);
        errorMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ForestUi.add(view, errorMessage, 8);
        errorMessage.setVisibility(View.GONE);
        input.addTextChangedListener(
            new TextWatcher() {
                @Override
                public void beforeTextChanged(
                    CharSequence value,
                    int start,
                    int count,
                    int after
                ) {}

                @Override
                public void onTextChanged(CharSequence value, int start, int before, int count) {
                    errorMessage.setVisibility(View.GONE);
                }

                @Override
                public void afterTextChanged(Editable value) {}
            }
        );
        androidx.appcompat.app.AlertDialog dialog = new ForestDialogBuilder(ctx())
            .setTitle("给今天一个小期待")
            .setView(view)
            .setPositiveButton("保存目标", null)
            .setNegativeButton("取消", null)
            .create();
        dialog.setOnShowListener(v ->
            dialog.getButton(-1).setOnClickListener(button -> {
                try {
                    int value = Integer.parseInt(input.getText().toString().trim());
                    store.update(n -> {
                        n.setDailyGoal(value);
                        return null;
                    });
                    dialog.dismiss();
                    render();
                } catch (RuntimeException error) {
                    errorMessage.setText(
                        error instanceof NumberFormatException
                            ? "请输入10至720之间的分钟数"
                            : error.getMessage()
                    );
                    errorMessage.setVisibility(View.VISIBLE);
                }
            })
        );
        dialog.show();
    }

    private void finishDialog() {
        if (store.notebook().running()) store.pause();
        render();
        long elapsed = store.elapsedMillis();
        if (elapsed < 1000) {
            toast("先专注一小会儿，再保存这段学习");
            return;
        }
        LinearLayout fields = column(ctx());
        fields.setPadding(dp(ctx(), 24), dp(ctx(), 10), dp(ctx(), 24), 0);
        fields.addView(heading(ctx(), StudyFormat.duration(elapsed), 28));
        ForestUi.add(
            fields,
            note(store.notebook().activeSubject().title + " · 这一段专注值得被记住。"),
            10
        );
        EditText input = new EditText(ctx());
        input.setId(R.id.study_note_input);
        input.setHint("留一句学习手记（选填）");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(120) });
        input.setMinLines(2);
        ForestUi.add(fields, input, 14);
        ForestUi.add(fields, note("保存后累计水滴；不到25分钟的时间会留到下一次。"), 10);
        new ForestDialogBuilder(ctx())
            .setTitle("今天，又前进了一点")
            .setView(fields)
            .setPositiveButton("保存学习", (dialog, which) ->
                perform(() -> {
                    StudyNotebook.Completion reward = store.finish(input.getText().toString());
                    render();
                    new ForestDialogBuilder(ctx())
                        .setTitle("一分耕耘，一点新绿")
                        .setView(rewardReceipt(reward))
                        .setPositiveButton("去看看我的小树", (d, w) ->
                            open(AppDestination.STUDY_GARDEN)
                        )
                        .setNegativeButton("继续学习", null)
                        .show();
                })
            )
            .setNegativeButton("稍后继续", null)
            .setNeutralButton("放弃本次", (dialog, which) ->
                new ForestDialogBuilder(ctx())
                    .setTitle("放弃这段学习？")
                    .setMessage("本次未保存的计时将被删除，已保存的记录和奖励不受影响。")
                    .setPositiveButton("确认放弃", (d, w) ->
                        perform(() -> {
                            store.update(n -> {
                                n.discard();
                                return null;
                            });
                            render();
                        })
                    )
                    .setNegativeButton("保留计时", null)
                    .show()
            )
            .show();
    }

    private View rewardReceipt(StudyNotebook.Completion reward) {
        LinearLayout receipt = column(ctx());
        receipt.setPadding(dp(ctx(), 24), dp(ctx(), 5), dp(ctx(), 24), dp(ctx(), 4));
        LinearLayout summary = row(ctx());
        TextView subject = text(ctx(), reward.record.subject.title, 13, WOOD);
        summary.addView(subject, new LinearLayout.LayoutParams(0, -2, 1));
        TextView stamp = text(ctx(), "✓ 已收录", 10, GREEN);
        stamp.setPadding(dp(ctx(), 9), dp(ctx(), 5), dp(ctx(), 9), dp(ctx(), 5));
        stamp.setBackground(rounded(ctx(), 0xffedf1e3, 0xffcbd6bb, 9));
        summary.addView(stamp);
        ForestMotion.stamp(stamp);
        receipt.addView(summary);
        ForestUi.add(
            receipt,
            heading(ctx(), StudyFormat.duration(reward.record.durationMillis), 28),
            14
        );
        LinearLayout rewards = row(ctx());
        rewards.setGravity(Gravity.CENTER);
        rewards.setPadding(dp(ctx(), 14), dp(ctx(), 17), dp(ctx(), 14), dp(ctx(), 17));
        rewards.setBackground(
            new ForestSurface(ctx(), ForestSurface.Kind.PAPER, 0xffedf1e3, 0xffcbd6bb, 13)
        );
        android.widget.ImageView drop = new android.widget.ImageView(ctx());
        drop.setImageResource(R.drawable.ic_study_drop);
        rewards.addView(drop, new LinearLayout.LayoutParams(dp(ctx(), 23), dp(ctx(), 23)));
        TextView earned = text(
            ctx(),
            "  +" + reward.water + " 水滴" + (reward.seeds > 0 ? "    +1 种子" : ""),
            16,
            DEEP
        );
        rewards.addView(earned);
        ForestUi.add(receipt, rewards, 18);
        ForestUi.add(
            receipt,
            note(
                reward.water == 0
                    ? "时间已积攒，累计满25分钟就能收到水滴。"
                    : "把水滴带去林地，给小树一点滋养吧。"
            ),
            12
        );
        return receipt;
    }

    private void statistics() {
        LinearLayout content = content();
        body.addView(content);
        ForestUi.add(content, heading(ctx(), "学习足迹", 27), 15);
        ForestUi.add(content, note("走过的每一步，森林都记得。"), 9);
        LinearLayout periods = row(ctx());
        int[] periodDays = { 1, 7, 30 },
            periodIds = { R.id.study_date_today, R.id.study_date_week, R.id.study_date_month };
        String[] labels = { "今天", "近7天", "近30天" };
        for (int i = 0; i < labels.length; i++) {
            final int value = periodDays[i];
            MaterialButton chip = button(ctx(), labels[i], false);
            chip.setId(periodIds[i]);
            chip.setTextSize(12);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setBackgroundTintList(ColorStateList.valueOf(days == value ? 0xffdfe8ce : LIGHT));
            chip.setOnClickListener(v -> {
                days = value;
                render();
            });
            addEqual(periods, chip, 39);
        }
        ForestUi.add(content, periods, 19);
        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        end.add(Calendar.DATE, 1);
        Calendar start = (Calendar) end.clone();
        start.add(Calendar.DATE, -days);
        List<StudyNotebook.Record> records = store
            .notebook()
            .between(start.getTimeInMillis(), end.getTimeInMillis());
        long total = 0;
        Set<String> dates = new HashSet<>();
        for (StudyNotebook.Record record : records) {
            total += record.durationMillis;
            dates.add(StudyNotebook.dayKey(record.completedAt));
        }
        TextView range = note(
            days == 1
                ? "今天的每一小段努力"
                : "最近" +
                      days +
                      "天 · 截至" +
                      new SimpleDateFormat("M月d日", Locale.CHINA).format(new java.util.Date())
        );
        range.setGravity(Gravity.CENTER);
        ForestUi.add(content, range, 11);
        LinearLayout summary = row(ctx());
        summary.setGravity(Gravity.TOP);
        stat(summary, "累计专注", StudyFormat.duration(total), 1.55f);
        stat(summary, "学习次数", records.size() + "次", 1);
        stat(summary, "学习的日子", dates.size() + "天", 1);
        ForestUi.add(content, summary, 20);
        LinearLayout card = card();
        ForestUi.add(
            card,
            heading(
                ctx(),
                chart == StudyChartView.Kind.TREND ? "努力，有自己的节奏" : "每个科目，学了多久",
                17
            ),
            0
        );
        LinearLayout kinds = row(ctx());
        String[] titles = { "环形时长", "科目时长", "每日趋势" };
        int[] ids = { R.id.study_chart_ring, R.id.study_chart_bars, R.id.study_chart_trend };
        for (int i = 0; i < titles.length; i++) {
            final StudyChartView.Kind type = StudyChartView.Kind.values()[i];
            MaterialButton chip = button(ctx(), titles[i], false);
            chip.setId(ids[i]);
            chip.setTextSize(11);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setBackgroundTintList(ColorStateList.valueOf(chart == type ? 0xffe0e9d2 : LIGHT));
            chip.setOnClickListener(v -> {
                chart = type;
                render();
            });
            addEqual(kinds, chip, 37);
        }
        ForestUi.add(card, kinds, 12);
        StudyChartView graphic = new StudyChartView(ctx());
        graphic.setId(R.id.study_chart_view);
        graphic.setData(records, chart, start.getTimeInMillis(), days);
        ForestUi.add(card, graphic, 8);
        if (total > 0) {
            StringBuilder missing = new StringBuilder();
            for (StudySubject s : StudySubject.values()) {
                boolean found = false;
                for (StudyNotebook.Record r : records) if (r.subject == s) found = true;
                if (!found) {
                    if (missing.length() > 0) missing.append("、");
                    missing.append(s.title);
                }
            }
            if (missing.length() > 0) ForestUi.add(card, note("尚未开始：" + missing), 5);
        }
        ForestUi.add(card, note("不必每天一样多，有积累就很好。"), 12);
        ForestUi.add(content, card, 20);
        LinearLayout history = row(ctx());
        history.addView(heading(ctx(), "专注手记", 18), new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton all = button(ctx(), "查看全部", false);
        all.setTextSize(11);
        all.setOnClickListener(v -> allRecords());
        history.addView(all);
        ForestUi.add(content, history, 18);
        LinearLayout list = column(ctx());
        list.setId(R.id.study_records_container);
        ForestUi.add(content, list, 7);
        if (records.isEmpty()) list.addView(
            note("这里还留着空白。先完成一小段学习，留下第一片叶子吧。")
        );
        for (int i = 0; i < Math.min(10, records.size()); i++) recordRow(list, records.get(i));
        ForestUi.add(content, note("每次学习单独保存，暂停时间不计入时长。"), 14);
    }

    private void stat(LinearLayout summary, String title, String value, float weight) {
        LinearLayout column = column(ctx());
        column.addView(text(ctx(), title, 10, MUTED));
        TextView number = heading(ctx(), value, 18);
        number.setMaxLines(2);
        ForestUi.add(column, number, 8);
        summary.addView(column, new LinearLayout.LayoutParams(0, -2, weight));
    }

    private void recordRow(LinearLayout list, StudyNotebook.Record record) {
        LinearLayout row = row(ctx());
        row.setPadding(0, dp(ctx(), 11), 0, dp(ctx(), 11));
        TextView dot = text(ctx(), "●", 14, record.subject.color);
        row.addView(dot, new LinearLayout.LayoutParams(dp(ctx(), 24), -2));
        LinearLayout copy = column(ctx());
        copy.addView(text(ctx(), record.subject.title, 13, DEEP));
        TextView detail = text(
            ctx(),
            new SimpleDateFormat("M/d HH:mm", Locale.CHINA).format(
                new java.util.Date(record.completedAt)
            ) +
                " · " +
                (record.note.isEmpty() ? "又认真地向前走了一小步" : record.note),
            10,
            MUTED
        );
        detail.setMaxLines(2);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        ForestUi.add(copy, detail, 5);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView time = text(ctx(), StudyFormat.duration(record.durationMillis), 12, DEEP);
        time.setGravity(Gravity.END);
        row.addView(time, new LinearLayout.LayoutParams(dp(ctx(), 97), -2));
        row.setOnClickListener(v -> recordDetail(record));
        list.addView(row);
        View line = new View(ctx());
        line.setBackgroundColor(LINE);
        list.addView(line, new LinearLayout.LayoutParams(-1, dp(ctx(), 1)));
    }

    private void recordDetail(StudyNotebook.Record record) {
        new ForestDialogBuilder(ctx())
            .setTitle(record.subject.title + " · " + StudyFormat.duration(record.durationMillis))
            .setMessage(
                new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(
                    new java.util.Date(record.completedAt)
                ) +
                    "\n\n" +
                    (record.note.isEmpty() ? "又认真地向前走了一小步。" : record.note)
            )
            .setPositiveButton("收好足迹", null)
            .show();
    }

    private void allRecords() {
        List<StudyNotebook.Record> all = new ArrayList<>(store.notebook().records());
        all.sort((a, b) -> Long.compare(b.completedAt, a.completedAt));
        if (all.isEmpty()) {
            toast("还没有学习记录，从第一段专注开始吧");
            return;
        }
        String[] items = new String[all.size()];
        for (int i = 0; i < items.length; i++) {
            StudyNotebook.Record r = all.get(i);
            items[i] =
                new SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA).format(
                    new java.util.Date(r.completedAt)
                ) +
                "\n" +
                r.subject.title +
                " · " +
                StudyFormat.duration(r.durationMillis);
        }
        new ForestDialogBuilder(ctx())
            .setTitle("全部学习记录 · " + all.size() + "次")
            .setItems(items, (dialog, index) -> recordDetail(all.get(index)))
            .setNegativeButton("返回", null)
            .show();
    }

    private void garden() {
        StudyNotebook n = store.notebook();
        LinearLayout content = content();
        body.addView(content);
        ForestUi.add(content, heading(ctx(), "我的小林地", 27), 15);
        ForestUi.add(content, note("你在认真生活，森林就在悄悄长大。"), 9);
        wallet = text(ctx(), "水滴 " + n.water() + "     种子 " + n.seeds(), 13, WOOD);
        wallet.setId(R.id.study_wallet);
        LinearLayout supplies = row(ctx());
        supplies.addView(wallet, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton refill = button(ctx(), "+5 水滴", false);
        refill.setTextSize(11);
        refill.setContentDescription("补充5滴水，方便测试浇水");
        refill.setOnClickListener(v ->
            perform(() -> {
                if (garden != null && garden.busy()) {
                    toast("等小树吸收完，再补充水滴吧");
                    return;
                }
                store.update(book -> {
                    book.addWater();
                    return null;
                });
                render();
                toast("已补充5滴水");
            })
        );
        supplies.addView(refill);
        ForestUi.add(content, supplies, 12);
        garden = new StudyGardenView(ctx());
        garden.setId(R.id.study_garden_view);
        garden.setState(n.planted(), n.growth());
        garden.setOnClickListener(v -> {
            if (garden.busy()) return;
            if (store.notebook().planted()) {
                garden.greet();
                toast("叶子轻轻晃了晃：今天也一起加油。摸摸不消耗水滴。");
            } else toast("这片小小的空地，正等你种下一颗种子。");
        });
        LinearLayout.LayoutParams sceneParams = new LinearLayout.LayoutParams(-1, dp(ctx(), 248));
        sceneParams.topMargin = dp(ctx(), 14);
        content.addView(garden, sceneParams);
        TextView name = heading(
            ctx(),
            n.planted() ? StudyGrowth.name(n.growth()) : "种下一点新的期待",
            22
        );
        name.setGravity(Gravity.CENTER);
        ForestUi.add(content, name, 17);
        TextView subtitle = note(
            n.planted()
                ? "这是你的第" +
                      (n.trees() + 1) +
                      "棵树 · 成长值 " +
                      n.growth() +
                      " / " +
                      StudyGrowth.REQUIRED_WATER +
                      "\n再浇" +
                      (StudyGrowth.REQUIRED_WATER - n.growth()) +
                      "次，就能住进小森林。"
                : "用1颗种子，开始照顾一棵小树。\n只要愿意开始，什么时候都不算晚。"
        );
        subtitle.setGravity(Gravity.CENTER);
        ForestUi.add(content, subtitle, 9);
        growthProgress = new StudyGrowthProgress(ctx(), n.growth());
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(ctx(), 76));
        progressParams.topMargin = dp(ctx(), 11);
        content.addView(growthProgress, progressParams);
        if (n.planted()) {
            int nextStage = StudyGrowth.stage(n.growth()) + 1;
            TextView next = note(
                "距离「" +
                    StudyGrowth.name(StudyGrowth.milestone(nextStage)) +
                    "」还差 " +
                    (StudyGrowth.milestone(nextStage) - n.growth()) +
                    " 滴水"
            );
            next.setGravity(Gravity.CENTER);
            ForestUi.add(content, next, 5);
        }
        waterButton = button(
            ctx(),
            n.planted() ? "给小树浇水 · 1水滴" : "种下一颗种子 · 1种子",
            true
        );
        waterButton.setId(R.id.study_water_button);
        waterButton.setOnClickListener(v -> perform(this::care));
        ForestUi.add(content, waterButton, 14);
        TextView gentle = note(
            n.planted() ? "休息不会枯萎，小树会一直等你。" : "第一颗种子，送给愿意开始的你。"
        );
        gentle.setGravity(Gravity.CENTER);
        ForestUi.add(content, gentle, 10);
        section(content, "一点点，长成了森林", n.trees() + "棵收藏");
        LinearLayout collection = row(ctx());
        collection.setPadding(dp(ctx(), 12), dp(ctx(), 12), dp(ctx(), 12), dp(ctx(), 12));
        collection.setBackground(rounded(ctx(), 0xffe9eddd, 0xffd8dec6, 12));
        for (int i = 0; i < Math.min(3, n.trees()); i++) collection.addView(
            image(ctx(), R.drawable.savings_tree_stage_3, "已长成的小树"),
            new LinearLayout.LayoutParams(dp(ctx(), 34), dp(ctx(), 52))
        );
        TextView collected = text(
            ctx(),
            n.trees() == 0 ? "森林的故事，等你写下。" : "每一棵，都有你的时间。",
            12,
            DEEP
        );
        collection.addView(collected, new LinearLayout.LayoutParams(0, -2, 1));
        ForestUi.add(content, collection, 12);
        section(content, "森林的小小约定", "努力有回响");
        ForestUi.add(
            content,
            note(
                "每累计专注25分钟  →  +1水滴\n跨次累计，短短一段也不会浪费。\n\n每天达到" +
                    n.dailyGoal() +
                    "分钟目标  →  +1种子\n每天最多一次，休息和暂停不扣奖励。\n\n浇满100次水  →  收藏小树\n小树长大后，可以再种下新的期待。"
            ),
            12
        );
    }

    private void care() {
        if (garden == null || garden.busy()) return;
        StudyNotebook n = store.notebook();
        boolean wasPlanted = n.planted();
        int from = n.growth();
        boolean mature = wasPlanted
            ? store.update(StudyNotebook::waterTree)
            : store.update(book -> {
                  book.plant();
                  return false;
              });
        garden.setState(store.notebook().planted(), store.notebook().growth());
        wallet.setText(
            "水滴 " + store.notebook().water() + "     种子 " + store.notebook().seeds()
        );
        if (growthProgress != null) growthProgress.advanceTo(wasPlanted ? from + 1 : 0);
        waterButton.setEnabled(false);
        waterButton.setText("小树正在吸收你的照顾…");
        Runnable done = () -> {
            if (body == null || !isAdded()) return;
            ForestMotion.confirm(body);
            render();
            if (mature) new ForestDialogBuilder(ctx())
                .setTitle("你的小森林，多了一棵树")
                .setMessage(
                    "100次细心照顾，让一颗种子长大了。已收藏第" +
                        store.notebook().trees() +
                        "棵小树。"
                )
                .setPositiveButton("把这份成长收好", null)
                .show();
        };
        if (wasPlanted) garden.water(from, done);
        else garden.plant(done);
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
        handler.post(tick);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(tick);
        if (garden != null) garden.setActive(false);
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("days", days);
        state.putString("chart", chart.name());
    }

    @Override
    public void onDestroyView() {
        if (subjectSheet != null) subjectSheet.dismiss();
        handler.removeCallbacks(tick);
        if (garden != null) garden.setActive(false);
        body = null;
        scroll = null;
        clock = null;
        today = null;
        garden = null;
        growthProgress = null;
        dial = null;
        wallet = null;
        waterButton = null;
        super.onDestroyView();
    }
}

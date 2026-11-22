package com.example.pkmapp.study;

import static com.example.pkmapp.forest.ForestUi.*;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.ForestNotebookPad;
import com.example.pkmapp.forest.ForestSegmentedControl;
import com.example.pkmapp.forest.ForestSurface;
import com.example.pkmapp.forest.ForestTodoRow;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.todo.TodoNotifications;
import com.example.pkmapp.todo.TodoStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StudyFragment extends Fragment {

    private static final Pattern DURATION_UNITS = Pattern.compile("[小时分钟秒]+");
    private StudyStore store;
    private AppDestination page;
    private ScrollView scroll;
    private LinearLayout body;
    private TextView clock, today, wallet;
    private String renderedToday;
    private StudyClockFace dial;
    private StudyGardenView garden;
    private StudyGrowthProgress growthProgress;
    private MaterialButton waterButton;
    private MaterialButton subjectSelector;
    private boolean renderedRunning;
    private String renderedSession = "";
    private StudySheet activeSheet;
    private long lastControlClick;
    private int days = 7;
    private int todoFilter = 0;
    private String statsPlan;
    private static final String HISTORY_REQUEST = "study-history";
    private final ActivityResultLauncher<String> exportPicker = registerForActivityResult(
        new ActivityResultContracts.CreateDocument("application/json"), this::exportStudy);
    private final ActivityResultLauncher<String[]> importPicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), this::importStudy);
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (store != null && isAdded()) {
                StudyNotifications.refresh(ctx(), store);
                render();
                if (!granted) toast("可以在系统设置里开启学习通知");
            }
        });
    private StudyChartView.Kind chart = StudyChartView.Kind.RING;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            StudyNotebook notebook = store.notebook();
            if (page == AppDestination.STUDY_FOCUS && (notebook.running() != renderedRunning
                || !notebook.sessionId().equals(renderedSession))) render();
            else updateTimer();
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

    public static StudyFragment fromNotification(String action, String id) {
        StudyFragment fragment = newInstance(AppDestination.STUDY_FOCUS);
        fragment.requireArguments().putString("notificationAction", action);
        fragment.requireArguments().putString(StudyNotifications.SESSION, id);
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
            statsPlan = saved.getString("statsPlan");
            chart = StudyChartView.Kind.valueOf(
                saved.getString("chart", StudyChartView.Kind.RING.name())
            );
        }
        if (page == AppDestination.STUDY_STATS) {
            getParentFragmentManager().setFragmentResultListener(HISTORY_REQUEST, this,
                (key, result) -> {
                    statsPlan = result.getString("plan");
                    days = 0;
                    render();
                });
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
        scroll.setPadding(0, 0, 0, bars.bottom + dp(requireContext(), 112));
    }

    private void open(AppDestination destination) {
        ((MainActivity) requireActivity()).showDestination(destination);
    }

    private StudyPlanDialogs planDialogs() {
        return new StudyPlanDialogs(ctx(), store, newSheet(), this::render, this::openHistory, this::backupMenu);
    }

    private StudySheet newSheet() {
        if (activeSheet != null) activeSheet.dismiss();
        StudySheet sheet = new StudySheet(ctx());
        sheet.setOnDismissListener(d -> {
            if (activeSheet == sheet) activeSheet = null;
        });
        activeSheet = sheet;
        return sheet;
    }

    private StudySheet.Pane sheetPane(String title, String detail, int glyph) {
        return new StudySheet.Pane(ctx(), title, detail, glyph);
    }

    private void returnToTimer(String planId) {
        perform(() -> {
            store.update(n -> { n.switchPlan(planId); return null; });
            if (page == AppDestination.STUDY_FOCUS) render();
            else open(AppDestination.STUDY_FOCUS);
        });
    }

    private void openHistory(String planId) {
        Bundle request = new Bundle();
        request.putString("plan", planId);
        getParentFragmentManager().setFragmentResult(HISTORY_REQUEST, request);
        open(AppDestination.STUDY_STATS);
    }

    private void backupMenu(StudySheet sheet) {
        StudySheet.Pane pane = sheetPane("学习备份", "备份包含计划、科目、记录、计时与小林地进度。",
            R.drawable.ic_leaf);
        ForestUi.add(pane.body, StudySheet.item(ctx(), "导出学习备份", "保存为一个 JSON 文件",
            R.drawable.ic_study_book, StudySheet.MOSS, "›", false, () -> {
                sheet.dismiss();
                exportPicker.launch("芽叶记-学习备份-"
                    + StudyNotebook.dayKey(System.currentTimeMillis()) + ".json");
            }), 0);
        ForestUi.add(pane.body, StudySheet.item(ctx(), "恢复学习备份", "选取文件，合并两份学习记录",
            R.drawable.ic_study_trail, StudySheet.OAT, "›", false, () -> {
                sheet.dismiss();
                importPicker.launch(new String[] {"application/json", "application/octet-stream", "text/plain"});
            }), 8);
        sheet.push(pane);
    }

    private void exportStudy(@Nullable Uri uri) {
        if (uri == null || !isAdded()) return;
        try (java.io.OutputStream out = ctx().getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new java.io.IOException();
            out.write(store.exportBackup().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            toast("学习备份导出失败，请检查文件位置和存储空间");
            return;
        }
        toast("已导出学习备份");
    }

    private void importStudy(@Nullable Uri uri) {
        if (uri == null || !isAdded()) return;
        final StudyNotebook backup;
        try (java.io.InputStream in = ctx().getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException();
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int length;
            while ((length = in.read(chunk)) != -1) {
                if (bytes.size() + length > 32 * 1024 * 1024) {
                    throw new IllegalArgumentException("学习备份超过 32 MB，无法读取");
                }
                bytes.write(chunk, 0, length);
            }
            backup = StudyNotebook.fromJson(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            toast(e.getMessage());
            return;
        } catch (java.io.IOException | SecurityException e) {
            toast("无法读取这个学习备份");
            return;
        }
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("恢复学习备份", "恢复将合并学习记录。请选择保留哪份计划设置和林地。",
            R.drawable.ic_study_trail);
        LinearLayout stats = row(ctx());
        stats.setPadding(dp(ctx(), 16), dp(ctx(), 15), dp(ctx(), 16), dp(ctx(), 15));
        stats.setBackground(rounded(ctx(), StudySheet.MOSS, 0, 18));
        stat(stats, "备份中的计划", backup.plans().size() + " 个", 1);
        stat(stats, "备份中的记录", backup.records().size() + " 条", 1);
        pane.body.addView(stats);
        pane.primary("保留本机设置和林地", () -> restoreLearning(sheet, backup, false));
        pane.secondary("使用备份设置和林地", () -> restoreLearning(sheet, backup, true));
        sheet.push(pane);
    }

    private void restoreLearning(StudySheet dialog, StudyNotebook backup, boolean preferBackup) {
        perform(() -> {
            int added = store.importBackup(backup, preferBackup);
            dialog.dismiss();
            render();
            toast("学习备份已恢复，新增 " + added + " 条记录");
        });
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
        renderedToday = null;
        dial = null;
        garden = null;
        growthProgress = null;
        wallet = null;
        waterButton = null;
        subjectSelector = null;
        StudyNotebook notebook = store.notebook();
        renderedRunning = notebook.running();
        renderedSession = notebook.sessionId();
        if (notebook.hasSession() && page == AppDestination.STUDY_FOCUS && !notebook.currentHasSession()) {
            LinearLayout banner = content();
            StudyPlan active = notebook.plan(notebook.activePlanId());
            MaterialButton back = button(ctx(), "计时归属 · " + active.title + " ›", false);
            back.setOnClickListener(v -> returnToTimer(active.id));
            banner.addView(back);
            body.addView(banner);
        }
        if (page == AppDestination.STUDY_FOCUS) focus();
        else if (page == AppDestination.STUDY_TODO) todoPage();
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
        StudyPlan plan = n.currentPlan();
        StudyPlan.Partner partner = plan == null ? StudyPlan.Partner.SQUIRREL : plan.partner;
        boolean active = n.currentHasSession();
        FrameLayout hero = new FrameLayout(ctx());
        LinearLayout copy = column(ctx());
        copy.setGravity(Gravity.START);
        TextView datePartner = text(
            ctx(),
            new SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(new java.util.Date())
                + " · 伙伴·" + partner.title,
            11,
            WOOD
        );
        datePartner.setSingleLine(true);
        datePartner.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(datePartner, new LinearLayout.LayoutParams(-1, -2));
        ForestUi.add(copy, heading(ctx(), "森林自习室", 24), 4);
        if (plan != null) {
            int pillFill = plan.partner == StudyPlan.Partner.TREECKO ? 0xffe3ecd9
                : plan.partner == StudyPlan.Partner.BULBASAUR ? 0xffe5efea : 0xffe9efe3;
            TextView plans = StudySheet.link(
                ctx(),
                "当前计划 · " + plan.title + " ▾",
                0xff243624,
                () -> planDialogs().manager()
            );
            plans.setTextSize(12);
            plans.setMinimumHeight(dp(ctx(), 32));
            plans.setPadding(dp(ctx(), 12), dp(ctx(), 5), dp(ctx(), 12), dp(ctx(), 5));
            plans.setBackground(ripple(ctx(), rounded(ctx(), pillFill, 0xffbfd0b9, 999), false));
            LinearLayout.LayoutParams planLp = new LinearLayout.LayoutParams(-2, -2);
            planLp.topMargin = dp(ctx(), 8);
            copy.addView(plans, planLp);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            -1,
            -2,
            Gravity.TOP | Gravity.START
        );
        lp.setMargins(dp(ctx(), 22), dp(ctx(), 8), dp(ctx(), 112), 0);
        hero.addView(copy, lp);
        int companion = partner == StudyPlan.Partner.TREECKO ? R.drawable.treecko_chart
            : partner == StudyPlan.Partner.BULBASAUR ? R.drawable.bulbasaur_sleeping
            : R.drawable.crayon_study_companion;
        FrameLayout.LayoutParams mascot = new FrameLayout.LayoutParams(dp(ctx(), 90), dp(ctx(), 84),
            Gravity.END | Gravity.CENTER_VERTICAL);
        mascot.setMarginEnd(dp(ctx(), 21));
        hero.addView(image(ctx(), companion, null), mascot);
        body.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout content = content();
        body.addView(content);
        if (plan == null) {
            LinearLayout empty = card();
            empty.addView(heading(ctx(), "还没有学习计划", 19));
            ForestUi.add(empty, note("新建计划并添加科目后，就可以开始计时。"), 8);
            MaterialButton create = button(ctx(), "新建计划", true);
            create.setOnClickListener(v -> planDialogs().newPlan());
            ForestUi.add(empty, create, 14);
            empty.addView(StudySheet.link(ctx(), "学习备份", DEEP, () -> backupMenu(newSheet())));
            ForestUi.add(content, empty, 8);
            return;
        }
        if (!plan.due.isEmpty() || !plan.previous.isEmpty()) {
            LinearLayout metaRow = row(ctx());
            if (!plan.due.isEmpty()) {
                String due = plan.due.compareTo(StudyNotebook.dayKey(System.currentTimeMillis())) <= 0
                    ? "预计结束日已到 · " : "预计结束 · ";
                metaRow.addView(note(due + plan.due), new LinearLayout.LayoutParams(0, -2, 1));
            } else {
                metaRow.addView(new View(ctx()), new LinearLayout.LayoutParams(0, 1, 1));
            }
            if (!plan.previous.isEmpty()) {
                StudyPlan previous = n.plan(plan.previous);
                TextView prior = StudySheet.link(ctx(), "上一轮 · " + previous.title + " ›", DEEP,
                    () -> openHistory(previous.id));
                prior.setTextSize(11);
                prior.setMinimumHeight(dp(ctx(), 28));
                prior.setPadding(dp(ctx(), 10), dp(ctx(), 3), dp(ctx(), 10), dp(ctx(), 3));
                prior.setBackground(ripple(ctx(), rounded(ctx(), 0xfff3efe3, 0xffdcd8c8, 999), false));
                metaRow.addView(prior);
            }
            ForestUi.add(content, metaRow, 4);
        }
        if (plan.archived != 0) {
            ForestUi.add(content, note("此计划已归档，可以查看足迹或恢复学习。"), 12);
            MaterialButton details = button(ctx(), "查看学习足迹", false);
            details.setOnClickListener(v -> openHistory(plan.id));
            ForestUi.add(content, details, 8);
            MaterialButton restore = button(ctx(), "恢复计划", true);
            restore.setOnClickListener(v -> perform(() -> {
                store.update(book -> { book.restore(plan.id); return null; });
                render();
            }));
            ForestUi.add(content, restore, 8);
            return;
        }
        StudyWork.Task next = !n.hasSession() ? n.nextTask(plan.id) : null;
        List<StudySubject> upcoming = StudyWorkDialogs.deadlines(plan);
        LinearLayout timer = card();
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(-1, -2);
        timerParams.topMargin = dp(ctx(), 8);
        content.addView(timer, timerParams);
        StudySubject currentSubject = active ? n.activeSubject() : n.selected();
        String subjectLabel =
            currentSubject.title + (active ? "  · 本次科目" : "    切换科目 ›");
        android.text.SpannableString selectorText = new android.text.SpannableString(subjectLabel);
        selectorText.setSpan(
            new android.text.style.RelativeSizeSpan(.8f),
            currentSubject.title.length(),
            subjectLabel.length(),
            0
        );
        selectorText.setSpan(
            new android.text.style.ForegroundColorSpan(MUTED),
            currentSubject.title.length(),
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
        subjectSelector.setEnabled(!active);
        subjectSelector.setAlpha(active ? .85f : 1f);
        subjectSelector.setOnClickListener(v -> showSubjectSheet());
        timer.addView(subjectSelector, new LinearLayout.LayoutParams(-1, dp(ctx(), 44)));
        StudyWork.Task activeTask = active ? n.task(n.activeTaskId()) : null;
        if (activeTask != null) ForestUi.add(timer, note("本次目标 · " + activeTask.title), 0);
        FrameLayout dialHost = new FrameLayout(ctx());
        dial = new StudyClockFace();
        dialHost.setBackground(dial);
        LinearLayout dialCopy = column(ctx());
        dialCopy.setGravity(Gravity.CENTER);
        TextView dialLabel = text(
            ctx(),
            active ? (n.running() ? "正在专注" : "休息一下") : "准备好就开始",
            11,
            MUTED
        );
        dialLabel.setGravity(Gravity.CENTER);
        dialCopy.addView(dialLabel);
        clock = text(ctx(), StudyFormat.clock(active ? store.elapsedMillis() : 0), 27, DEEP);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setFontFeatureSettings("tnum");
        clock.setId(R.id.study_timer);
        clock.setGravity(Gravity.CENTER);
        clock.setContentDescription("本次学习计时");
        ForestUi.add(dialCopy, clock, 10);
        int focusGoal = active ? n.activeGoal() : n.sessionGoal();
        TextView subject = text(ctx(), "目标 " + focusGoal + " 分钟", 12, MUTED);
        subject.setGravity(Gravity.CENTER);
        ForestUi.add(dialCopy, subject, 9);
        dialHost.addView(dialCopy, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(-1, dp(ctx(), 164));
        dialParams.topMargin = dp(ctx(), 4);
        timer.addView(dialHost, dialParams);
        LinearLayout goals = row(ctx());
        goals.setBackground(rounded(ctx(), 0xffebe7da, 0xffdcd8c8, 12));
        goals.setPadding(dp(ctx(), 4), dp(ctx(), 4), dp(ctx(), 4), dp(ctx(), 4));
        int[] presetGoals = new int[] { 25, 45, 60 };
        boolean isCustomGoal = focusGoal != 25 && focusGoal != 45 && focusGoal != 60;
        for (int minutes : presetGoals) {
            boolean selectedGoal = focusGoal == minutes;
            TextView chip = text(ctx(), minutes + "分钟", 12, selectedGoal ? 0xff243624 : 0xff6b7568);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(Typeface.create(selectedGoal ? "sans-serif-medium" : "sans-serif",
                selectedGoal ? Typeface.BOLD : Typeface.NORMAL));
            chip.setEnabled(!active);
            chip.setAlpha(active ? 0.65f : 1f);
            chip.setBackground(ripple(ctx(), rounded(ctx(),
                selectedGoal ? 0xfffffcf3 : Color.TRANSPARENT,
                selectedGoal ? 0xffc6d4c0 : 0, 9), false));
            chip.setOnClickListener(v ->
                perform(() -> {
                    store.update(book -> {
                        book.setSessionGoal(minutes);
                        return null;
                    });
                    render();
                })
            );
            addEqual(goals, chip, 36);
        }
        String customLabel = isCustomGoal ? focusGoal + "分钟 ✎" : "自定义";
        TextView customChip = text(ctx(), customLabel, 12, isCustomGoal ? 0xff243624 : 0xff6b7568);
        customChip.setGravity(Gravity.CENTER);
        customChip.setTypeface(Typeface.create(isCustomGoal ? "sans-serif-medium" : "sans-serif",
            isCustomGoal ? Typeface.BOLD : Typeface.NORMAL));
        customChip.setEnabled(!active);
        customChip.setAlpha(active ? 0.65f : 1f);
        customChip.setBackground(ripple(ctx(), rounded(ctx(),
            isCustomGoal ? 0xfffffcf3 : Color.TRANSPARENT,
            isCustomGoal ? 0xffc6d4c0 : 0, 9), false));
        customChip.setOnClickListener(v -> customSessionGoalDialog());
        addEqual(goals, customChip, 36);
        ForestUi.add(timer, goals, 10);
        LinearLayout controls = row(ctx());
        MaterialButton start = button(
            ctx(),
            active ? (n.running() ? "暂停一下" : "继续学习")
                : n.hasSession() ? "返回原计划保存计时" : "开始专注，种下今天",
            true
        );
        start.setId(R.id.study_start_pause_button);
        start.setCornerRadius(dp(ctx(), 12));
        start.setOnClickListener(v ->
            perform(() -> {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastControlClick < 280) return;
                lastControlClick = now;
                if (store.notebook().hasSession() && !store.notebook().currentHasSession()) {
                    returnToTimer(store.notebook().activePlanId());
                    return;
                }
                if (!store.notebook().hasSession()) store.start();
                else if (store.notebook().running()) store.pause();
                else store.resume();
                render();
                View action = body.findViewById(R.id.study_start_pause_button);
                if (action != null) ForestMotion.reveal(action);
            })
        );
        controls.addView(start, new LinearLayout.LayoutParams(0, dp(ctx(), 48), 1));
        if (active) {
            MaterialButton finish = button(ctx(), "完成学习", false);
            finish.setId(R.id.study_finish_button);
            finish.setCornerRadius(dp(ctx(), 12));
            finish.setOnClickListener(v -> perform(this::finishDialog));
            LinearLayout.LayoutParams finishParams = new LinearLayout.LayoutParams(
                dp(ctx(), 98),
                dp(ctx(), 48)
            );
            finishParams.leftMargin = dp(ctx(), 8);
            controls.addView(finish, finishParams);
        }
        ForestUi.add(timer, controls, 12);
        if (active) {
            boolean earlyAccidentalWindow = store.elapsedMillis() < 3 * 60_000L;
            if (earlyAccidentalWindow) {
                TextView withdraw = StudySheet.link(ctx(), "误触开始？撤回本次专注", 0xff8d4836,
                    () -> confirmDiscard(newSheet()));
                withdraw.setTextSize(12);
                withdraw.setMinimumHeight(dp(ctx(), 34));
                withdraw.setPadding(dp(ctx(), 12), dp(ctx(), 5), dp(ctx(), 12), dp(ctx(), 5));
                withdraw.setBackground(ripple(ctx(), rounded(ctx(), 0xfff9efea, 0xffe6cfc5, 10), false));
                ForestUi.add(timer, withdraw, 8);
            } else {
                TextView withdraw = StudySheet.link(ctx(), "放弃本次专注", 0xff8f8578,
                    () -> confirmDiscard(newSheet()));
                withdraw.setTextSize(11);
                withdraw.setMinimumHeight(dp(ctx(), 32));
                withdraw.setPadding(dp(ctx(), 10), dp(ctx(), 4), dp(ctx(), 10), dp(ctx(), 4));
                ForestUi.add(timer, withdraw, 4);
            }
        }
        TextView gentle = text(
            ctx(),
            active
                ? "暂停时间不计入时长 · 完成后可以切换科目"
                : "正向计时 · 随时暂停 · 到达目标仍可继续",
            11,
            MUTED
        );
        gentle.setGravity(Gravity.CENTER);
        ForestUi.add(timer, gentle, 8);
        if (!n.hasSession()) {
            TextView addGoalLink = StudySheet.link(ctx(), "添加本次目标", DEEP, this::sessionGoalDialog);
            addGoalLink.setMinimumHeight(dp(ctx(), 36));
            ForestUi.add(timer, addGoalLink, 2);
        } else if (active && !StudyNotifications.permitted(ctx())) {
            TextView notifyLink = StudySheet.link(ctx(), "启用通知栏控制", DEEP, this::allowStudyNotifications);
            notifyLink.setMinimumHeight(dp(ctx(), 36));
            ForestUi.add(timer, notifyLink, 2);
        }
        addAgendaPreview(content, plan, next, active ? java.util.Collections.emptyList() : upcoming);
    }

    private void customSessionGoalDialog() {
        if (store.notebook().currentHasSession()) return;
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("自定义专注时长", "设置单次专注目标（5 至 360 分钟），满 25 分钟累计水滴。",
            R.drawable.ic_study_book);
        EditText input = new EditText(ctx());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(store.notebook().sessionGoal()));
        input.setSelectAllOnFocus(true);
        input.setContentDescription("自定义专注目标分钟");
        StudySheet.styleField(input);
        pane.body.addView(StudySheet.section(ctx(), "单次目标时长（分钟）"));
        ForestUi.add(pane.body, input, 7);

        LinearLayout presets = row(ctx());
        int[] presetValues = { 15, 30, 90, 120 };
        for (int i = 0; i < presetValues.length; i++) {
            int minutes = presetValues[i];
            TextView option = StudySheet.link(ctx(), minutes + " 分钟", DEEP,
                () -> input.setText(String.valueOf(minutes)));
            option.setTextSize(12);
            option.setMinimumHeight(dp(ctx(), 36));
            option.setBackground(ripple(ctx(), rounded(ctx(), 0xfff5f2e6, 0xffd8d5c2, 10), false));
            LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) presetParams.setMarginStart(dp(ctx(), 6));
            presets.addView(option, presetParams);
        }
        ForestUi.add(pane.body, presets, 8);

        TextView errorMessage = text(ctx(), "", 12, 0xff8d4836);
        errorMessage.setVisibility(View.GONE);
        ForestUi.add(pane.body, errorMessage, 8);

        pane.secondary("取消", sheet::dismiss);
        pane.primary("保存时长", () -> {
            try {
                int value = Integer.parseInt(input.getText().toString().trim());
                store.update(n -> { n.setSessionGoal(value); return null; });
                sheet.dismiss();
                render();
            } catch (IllegalArgumentException | IllegalStateException error) {
                errorMessage.setText(error instanceof NumberFormatException
                    ? "请输入 5 至 360 之间的分钟数" : error.getMessage());
                errorMessage.setVisibility(View.VISIBLE);
            }
        });
        sheet.push(pane);
    }

    private StudyWorkDialogs workDialogs(StudySheet sheet) {
        return new StudyWorkDialogs(ctx(), getParentFragmentManager(), store, sheet);
    }

    private void startTask(String id) {
        store.start(id, "");
        if (activeSheet != null) activeSheet.dismiss();
        render();
        scroll.post(() -> {
            if (scroll != null && subjectSelector != null) {
                android.graphics.Rect bounds = new android.graphics.Rect();
                subjectSelector.getDrawingRect(bounds);
                scroll.offsetDescendantRectToMyCoords(subjectSelector, bounds);
                scroll.scrollTo(0, Math.max(0, bounds.top - scroll.getPaddingTop() - dp(ctx(), 14)));
            }
        });
    }

    private void sessionGoalDialog() {
        StudySheet sheet = newSheet();
        StudyWorkDialogs work = workDialogs(sheet);
        StudySheet.Pane pane = work.pane("本次目标", store.notebook().selected().title);
        EditText goal = work.input(pane.body, "这次要做什么", "例如 图的遍历 · 第 5–8 题", "", 120);
        pane.primary("以此开始专注", () -> work.save(() -> {
            String title = StudyPlan.required(StudyWorkDialogs.value(goal), 120, "学习目标");
            store.start("", title);
            sheet.dismiss();
            render();
        }));
        sheet.push(pane);
    }

    private void allowStudyNotifications() {
        if (StudyNotifications.permitted(ctx())) return;
        android.content.SharedPreferences flags = ctx().getSharedPreferences("study_ui", Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 33 && (!flags.getBoolean("askedNotifications", false)
            || shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))) {
            flags.edit().putBoolean("askedNotifications", true).apply();
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx().getPackageName()));
    }

    private String planRemindSubtitle(StudyPlan plan) {
        if (!plan.remindDeadlines) return "已关闭 · 开启后在截止前发送森林自习提醒";
        return "已开启 · " + plan.remindTime + " 发送森林自习提醒";
    }

    private void addAgendaPreview(
        LinearLayout parent,
        StudyPlan plan,
        @Nullable StudyWork.Task next,
        List<StudySubject> upcoming
    ) {
        LinearLayout preview = statsPanel(LIGHT, LINE);
        LinearLayout summary = row(ctx());
        today = text(ctx(), "", 13, 0xff243624);
        today.setId(R.id.study_today_summary);
        today.setLineSpacing(dp(ctx(), 4), 1);
        summary.addView(today, new LinearLayout.LayoutParams(0, -2, 1));
        TextView goal = StudySheet.link(ctx(), "调整目标", 0xff355336, this::dailyGoalDialog);
        goal.setId(R.id.study_daily_goal_button);
        goal.setTextSize(12);
        goal.setMinimumHeight(dp(ctx(), 34));
        goal.setPadding(dp(ctx(), 12), dp(ctx(), 6), dp(ctx(), 12), dp(ctx(), 6));
        goal.setBackground(ripple(ctx(), rounded(ctx(), 0xfff3efe3, 0xffdcd8c8, 10), false));
        summary.addView(goal);
        preview.addView(summary);

        View divider = new View(ctx());
        divider.setBackgroundColor(0xffe6e2d3);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(ctx(), 1));
        divLp.topMargin = dp(ctx(), 12);
        divLp.bottomMargin = dp(ctx(), 6);
        preview.addView(divider, divLp);

        int pendingCount = TodoStore.get(ctx()).pending().size();
        LinearLayout agendaHeader = row(ctx());
        agendaHeader.setMinimumHeight(dp(ctx(), 36));
        TextView agendaTitle = text(ctx(), "待办清单与提醒  ›", 13, DEEP);
        agendaTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        agendaHeader.addView(agendaTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView agendaBadge = text(
            ctx(),
            pendingCount == 0 ? "去新建待办" : pendingCount + " 项待办",
            11,
            MUTED
        );
        agendaHeader.addView(agendaBadge);
        agendaHeader.setOnClickListener(v -> open(AppDestination.STUDY_TODO));
        agendaHeader.setFocusable(true);
        agendaHeader.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false));
        ForestMotion.bind(agendaHeader);
        preview.addView(agendaHeader, new LinearLayout.LayoutParams(-1, -2));

        if (next != null) {
            LinearLayout nextRow = StudySheet.item(
                ctx(),
                "接着学 · " + plan.book(next.subjectId).title,
                next.title,
                R.drawable.ic_study_book,
                StudySheet.MOSS,
                "›",
                false,
                () -> perform(() -> startTask(next.id))
            );
            ForestUi.add(preview, nextRow, 6);
        }
        if (!upcoming.isEmpty()) {
            StudySubject closest = upcoming.get(0);
            ForestUi.add(
                preview,
                StudySheet.item(
                    ctx(),
                    "最近截止 · " + closest.title,
                    closest.due,
                    0,
                    0xfff5f1e6,
                    "›",
                    false,
                    this::showAgenda
                ),
                6
            );
        }
        ForestUi.add(parent, preview, 12);
    }

    private void todoPage() {
        LinearLayout content = content();
        body.addView(content);
        TodoStore todos = TodoStore.get(ctx());

        // 1. Minimalist editorial header (standalone, no plan or subject binding)
        LinearLayout headerRow = row(ctx());
        headerRow.addView(heading(ctx(), "待办", 27), new LinearLayout.LayoutParams(0, -2, 1));

        TextView remindPill = StudySheet.link(
            ctx(),
            todos.remindEnabled() ? "提醒 " + todos.defaultRemindTime() : "提醒已关",
            todos.remindEnabled() ? 0xff2e4f2f : MUTED,
            this::todoReminderSettingsDialog
        );
        remindPill.setTextSize(12);
        remindPill.setMinimumHeight(dp(ctx(), 34));
        remindPill.setPadding(dp(ctx(), 12), dp(ctx(), 5), dp(ctx(), 12), dp(ctx(), 5));
        remindPill.setBackground(ripple(ctx(), rounded(ctx(),
            todos.remindEnabled() ? 0xffe6efe2 : 0xfff0ece0,
            todos.remindEnabled() ? 0xffb8cbb2 : 0xffdcd8c8, 999), false));
        headerRow.addView(remindPill);

        TextView addTodoBtn = StudySheet.link(ctx(), "+ 新建", 0xfffffcf3, () -> todoEditorSheet(null));
        addTodoBtn.setTextSize(12);
        addTodoBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        addTodoBtn.setMinimumHeight(dp(ctx(), 34));
        addTodoBtn.setPadding(dp(ctx(), 14), dp(ctx(), 5), dp(ctx(), 14), dp(ctx(), 5));
        addTodoBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xff456444, 0xff345439, 999), true));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-2, -2);
        addLp.setMarginStart(dp(ctx(), 8));
        headerRow.addView(addTodoBtn, addLp);
        ForestUi.add(content, headerRow, 14);

        ForestUi.add(
            content,
            text(
                ctx(),
                new SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(new java.util.Date()),
                12,
                WOOD
            ),
            4
        );

        // 2. Motion-Primitives Sliding Pill Filter Bar + Handcrafted Draft-Paper Notebook Pad
        ForestNotebookPad notebookPad = new ForestNotebookPad(ctx());
        ForestSegmentedControl filterBar = new ForestSegmentedControl(
            ctx(),
            new String[] {"全部", "今天", "未来", "已完成"},
            null,
            todoFilter,
            idx -> {
                todoFilter = idx;
                populateStandaloneTodos(notebookPad, todos);
            }
        );
        ForestUi.add(content, filterBar, 14);
        ForestUi.add(content, notebookPad, 12);
        populateStandaloneTodos(notebookPad, todos);
    }

    private void populateStandaloneTodos(LinearLayout host, TodoStore todos) {
        host.removeAllViews();
        String todayKey = TodoStore.dayKey(System.currentTimeMillis());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        String tomorrowKey = TodoStore.dayKey(cal.getTimeInMillis());

        if (todoFilter == 3) {
            List<TodoStore.TodoItem> doneList = todos.completed();
            if (doneList.isEmpty()) {
                TextView empty = note("还没有已完成的待办。\n在草稿本左侧打勾划掉后，会静静留在这里。");
                empty.setPadding(dp(ctx(), 50), dp(ctx(), 16), dp(ctx(), 8), dp(ctx(), 16));
                host.addView(empty);
                return;
            }
            for (TodoStore.TodoItem item : doneList) {
                ForestTodoRow row = new ForestTodoRow(
                    ctx(),
                    item.title,
                    0,
                    null,
                    item.day.isEmpty() ? "已完成" : item.day + " · 已完成",
                    false,
                    true,
                    () -> {
                        todos.toggleDone(item.id, false);
                        populateStandaloneTodos(host, todos);
                    },
                    () -> todoEditorSheet(item)
                );
                host.addView(row);
            }
            return;
        }

        List<TodoStore.TodoItem> pendingList = todos.pending();
        List<TodoStore.TodoItem> filtered = new ArrayList<>();
        for (TodoStore.TodoItem t : pendingList) {
            if (todoFilter == 1 && !t.day.equals(todayKey)) continue;
            if (todoFilter == 2 && (t.day.isEmpty() || t.day.compareTo(todayKey) <= 0)) continue;
            filtered.add(t);
        }
        if (filtered.isEmpty()) {
            LinearLayout emptyBox = column(ctx());
            emptyBox.setPadding(dp(ctx(), 50), dp(ctx(), 16), dp(ctx(), 8), dp(ctx(), 12));
            TextView title = heading(ctx(), todoFilter == 1 ? "今天暂时没有待办" : "草稿纸还留着空白", 17);
            emptyBox.addView(title);
            ForestUi.add(emptyBox, note("点右上角「+ 新建」，把待办一条条写在这页草稿本上，并设好截止提醒。"), 6);
            host.addView(emptyBox);
            return;
        }

        Map<String, List<TodoStore.TodoItem>> grouped = new LinkedHashMap<>();
        for (TodoStore.TodoItem item : filtered) {
            String header;
            if (item.day.isEmpty()) header = "未限日期";
            else if (item.day.compareTo(todayKey) < 0) header = "已逾期 · " + item.day;
            else if (item.day.equals(todayKey)) header = "今天 · " + todayKey;
            else if (item.day.equals(tomorrowKey)) header = "明天 · " + tomorrowKey;
            else header = "日期 · " + item.day;
            grouped.computeIfAbsent(header, k -> new ArrayList<>()).add(item);
        }

        int groupIdx = 0;
        for (Map.Entry<String, List<TodoStore.TodoItem>> entry : grouped.entrySet()) {
            String headerText = entry.getKey();
            boolean isOverdue = headerText.startsWith("已逾期");
            LinearLayout dateHeader = row(ctx());
            dateHeader.setPadding(dp(ctx(), 50), dp(ctx(), groupIdx == 0 ? 6 : 14), dp(ctx(), 4), dp(ctx(), 6));
            TextView dateTitle = text(ctx(), headerText, 12, isOverdue ? 0xff9e4c37 : 0xff3b543c);
            dateTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            dateHeader.addView(dateTitle, new LinearLayout.LayoutParams(0, -2, 1));
            dateHeader.addView(text(ctx(), entry.getValue().size() + " 项", 11, MUTED));
            host.addView(dateHeader, new LinearLayout.LayoutParams(-1, -2));
            groupIdx++;

            for (TodoStore.TodoItem item : entry.getValue()) {
                StringBuilder meta = new StringBuilder();
                if (!item.dueTime.isEmpty()) meta.append(item.dueTime).append(" 截止");
                if (item.remind && !item.day.isEmpty()) {
                    if (meta.length() > 0) meta.append(" · ");
                    meta.append(item.leadLabel()).append("提醒");
                }
                if (!item.note.isEmpty()) {
                    if (meta.length() > 0) meta.append(" · ");
                    meta.append(item.note);
                }
                boolean urgent = isOverdue || (item.day.equals(todayKey) && !item.dueTime.isEmpty());
                ForestTodoRow todoRow = new ForestTodoRow(
                    ctx(),
                    item.title,
                    0,
                    null,
                    meta.toString(),
                    urgent,
                    false,
                    () -> {
                        todos.toggleDone(item.id, true);
                        toast("已完成「" + item.title + "」");
                        populateStandaloneTodos(host, todos);
                    },
                    () -> todoEditorSheet(item)
                );
                host.addView(todoRow);
            }
        }
    }

    private void todoEditorSheet(@Nullable TodoStore.TodoItem existing) {
        TodoStore todos = TodoStore.get(ctx());
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane(existing == null ? "新建待办" : "编辑待办", null, R.drawable.ic_list);

        // 1. Borderless editorial title input
        EditText title = new EditText(ctx());
        title.setSingleLine(false);
        title.setMaxLines(3);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setHint("准备做哪件事？");
        title.setFilters(new InputFilter[] { new InputFilter.LengthFilter(120) });
        title.setText(existing == null ? "" : existing.title);
        title.setTextSize(18f);
        title.setTextColor(0xff243624);
        title.setHintTextColor(0xff9aa094);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setBackground(null);
        title.setPadding(dp(ctx(), 2), dp(ctx(), 4), dp(ctx(), 2), dp(ctx(), 6));
        pane.body.addView(title, new LinearLayout.LayoutParams(-1, -2));

        EditText noteInput = new EditText(ctx());
        noteInput.setSingleLine(false);
        noteInput.setMaxLines(3);
        noteInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        noteInput.setHint("备注（选填）");
        noteInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(200) });
        noteInput.setText(existing == null ? "" : existing.note);
        noteInput.setTextSize(13.5f);
        noteInput.setTextColor(MUTED);
        noteInput.setHintTextColor(0xffa7ad9f);
        noteInput.setBackground(null);
        noteInput.setPadding(dp(ctx(), 2), dp(ctx(), 2), dp(ctx(), 2), dp(ctx(), 10));
        pane.body.addView(noteInput, new LinearLayout.LayoutParams(-1, -2));

        View titleRule = new View(ctx());
        titleRule.setBackgroundColor(0xffe5e1d2);
        pane.body.addView(titleRule, new LinearLayout.LayoutParams(-1, dp(ctx(), 1)));

        String todayKey = TodoStore.dayKey(System.currentTimeMillis());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        String tomorrowKey = TodoStore.dayKey(cal.getTimeInMillis());
        cal.add(Calendar.DATE, 1);
        String afterTomorrowKey = TodoStore.dayKey(cal.getTimeInMillis());

        String[] chosenDay = { existing == null ? todayKey : existing.day };
        int[] leadMinutes = { existing == null ? 30 : existing.remindLeadMinutes };
        boolean initialRemind = existing != null && existing.remind;

        // 2. Standalone property pill strip: Date + Deadline Time ONLY (no subject!)
        LinearLayout pillRow = row(ctx());
        TextView datePill = StudySheet.link(ctx(), "", 0xff2e4f2f, () -> {});
        datePill.setTextSize(12);
        datePill.setMinimumHeight(dp(ctx(), 34));
        datePill.setPadding(dp(ctx(), 13), dp(ctx(), 6), dp(ctx(), 13), dp(ctx(), 6));
        pillRow.addView(datePill);

        TextView timePill = StudySheet.link(ctx(), "", 0xff4c594a, () -> {});
        timePill.setTextSize(12);
        timePill.setMinimumHeight(dp(ctx(), 34));
        timePill.setPadding(dp(ctx(), 13), dp(ctx(), 6), dp(ctx(), 13), dp(ctx(), 6));
        LinearLayout.LayoutParams timePillLp = new LinearLayout.LayoutParams(-2, -2);
        timePillLp.setMarginStart(dp(ctx(), 8));
        pillRow.addView(timePill, timePillLp);
        ForestUi.add(pane.body, pillRow, 12);

        // 3. Collapsible inline Date & Time panels
        LinearLayout datePanel = column(ctx());
        datePanel.setVisibility(View.GONE);
        LinearLayout dayPresetsRow = row(ctx());
        String[][] dayPresets = {
            {"今天", todayKey}, {"明天", tomorrowKey}, {"后天", afterTomorrowKey}, {"选日期", "CAL"}, {"不限", ""}
        };

        LinearLayout timePanel = column(ctx());
        timePanel.setVisibility(View.GONE);
        LinearLayout timePresetsRow = row(ctx());
        EditText dueTimeInput = new EditText(ctx());
        dueTimeInput.setSingleLine(true);
        dueTimeInput.setInputType(InputType.TYPE_CLASS_TEXT);
        dueTimeInput.setHint("自定义截止时刻，例如 19:30");
        dueTimeInput.setText(existing == null ? "" : existing.dueTime);
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
            datePill.setBackground(ripple(ctx(), rounded(ctx(),
                hasDay ? 0xffe6efe2 : 0xfff3efe3,
                hasDay ? 0xffb8cbb2 : 0xffdcd8c8, 999), false));

            String tVal = dueTimeInput.getText().toString().trim();
            boolean hasTime = !tVal.isEmpty();
            timePill.setText(hasTime ? "截止 " + tVal + " ▾" : "设截止时间 ▾");
            timePill.setTextColor(hasTime ? 0xff8d4836 : 0xff4c594a);
            timePill.setBackground(ripple(ctx(), rounded(ctx(),
                hasTime ? 0xfff9efea : 0xfff3efe3,
                hasTime ? 0xffe6cfc5 : 0xffdcd8c8, 999), false));
        };

        for (int i = 0; i < dayPresets.length; i++) {
            String lbl = dayPresets[i][0];
            String val = dayPresets[i][1];
            TextView chip = StudySheet.link(ctx(), lbl, DEEP, () -> {
                if ("CAL".equals(val)) {
                    Calendar local = TodoStore.parseDay(chosenDay[0]);
                    Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                    utc.clear();
                    utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
                    com.google.android.material.datepicker.MaterialDatePicker<Long> picker =
                        com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                            .setTitleText("选择待办日期")
                            .setSelection(utc.getTimeInMillis())
                            .build();
                    picker.addOnPositiveButtonClickListener(ms -> {
                        utc.setTimeInMillis(ms);
                        chosenDay[0] = String.format(Locale.ROOT, "%04d-%02d-%02d",
                            utc.get(Calendar.YEAR), utc.get(Calendar.MONTH) + 1, utc.get(Calendar.DAY_OF_MONTH));
                        syncPills.run();
                        datePanel.setVisibility(View.GONE);
                    });
                    sheet.hideKeyboard();
                    picker.show(getParentFragmentManager(), "todo-calendar");
                } else {
                    chosenDay[0] = val;
                    syncPills.run();
                    datePanel.setVisibility(View.GONE);
                }
            });
            chip.setTextSize(12);
            chip.setMinimumHeight(dp(ctx(), 34));
            chip.setBackground(ripple(ctx(), rounded(ctx(), 0xfff3efe3, 0xffdcd8c8, 10), false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(ctx(), 5));
            dayPresetsRow.addView(chip, lp);
        }
        datePanel.addView(dayPresetsRow);
        ForestUi.add(pane.body, datePanel, 8);

        String[] timePresets = {"12:00", "18:00", "21:00", "22:30", "不限"};
        for (int i = 0; i < timePresets.length; i++) {
            String preset = timePresets[i];
            TextView chip = StudySheet.link(ctx(), preset, DEEP, () -> {
                dueTimeInput.setText("不限".equals(preset) ? "" : preset);
                syncPills.run();
                timePanel.setVisibility(View.GONE);
            });
            chip.setTextSize(12);
            chip.setMinimumHeight(dp(ctx(), 34));
            chip.setBackground(ripple(ctx(), rounded(ctx(), 0xfff3efe3, 0xffdcd8c8, 10), false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(ctx(), 5));
            timePresetsRow.addView(chip, lp);
        }
        timePanel.addView(timePresetsRow);
        ForestUi.add(timePanel, dueTimeInput, 8);
        ForestUi.add(pane.body, timePanel, 8);

        datePill.setOnClickListener(v -> {
            boolean show = datePanel.getVisibility() != View.VISIBLE;
            datePanel.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) timePanel.setVisibility(View.GONE);
        });
        timePill.setOnClickListener(v -> {
            boolean show = timePanel.getVisibility() != View.VISIBLE;
            timePanel.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) datePanel.setVisibility(View.GONE);
        });
        dueTimeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { syncPills.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        syncPills.run();

        // 4. Morphing Leaf Switch card (matches HTML design C · 森林叶片旋转胶囊开关)
        MaterialCheckBox remindSwitch = ForestUi.switchWithSubtitle(
            ctx(),
            "截止时间森林提醒",
            todoLeadSubtitle(initialRemind, leadMinutes[0], todos.defaultRemindTime())
        );
        remindSwitch.setChecked(initialRemind);
        ForestUi.add(pane.body, remindSwitch, 14);

        LinearLayout leadRow = row(ctx());
        int[] leadValues = {15, 30, 60, 1440};
        String[] leadLabels = {"提前15分", "提前30分", "提前1小时", "前一天傍晚"};
        List<TextView> leadButtons = new ArrayList<>();
        Runnable updateLeadUi = () -> {
            boolean on = remindSwitch.isChecked();
            leadRow.setVisibility(on ? View.VISIBLE : View.GONE);
            ForestUi.updateSwitchSubtitle(
                remindSwitch,
                todoLeadSubtitle(on, leadMinutes[0], todos.defaultRemindTime())
            );
            for (int i = 0; i < leadButtons.size(); i++) {
                boolean active = leadMinutes[0] == leadValues[i];
                TextView btn = leadButtons.get(i);
                btn.setTextColor(active ? 0xff243624 : 0xff6b7568);
                btn.setBackground(ripple(ctx(), rounded(ctx(),
                    active ? 0xffe6efe2 : 0xfff3efe3,
                    active ? 0xff96b392 : 0xffdcd8c8, 999), false));
            }
        };
        for (int i = 0; i < leadValues.length; i++) {
            int val = leadValues[i];
            TextView btn = StudySheet.link(ctx(), leadLabels[i], DEEP, () -> {
                leadMinutes[0] = val;
                updateLeadUi.run();
            });
            btn.setTextSize(11);
            btn.setMinimumHeight(dp(ctx(), 32));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(ctx(), 5));
            leadRow.addView(btn, lp);
            leadButtons.add(btn);
        }
        remindSwitch.setOnCheckedChangeListener((b, checked) -> updateLeadUi.run());
        updateLeadUi.run();
        ForestUi.add(pane.body, leadRow, 2);

        if (existing != null) {
            pane.secondary("删除待办", () -> {
                todos.delete(existing.id);
                sheet.dismiss();
                render();
            });
        } else {
            pane.secondary("取消", sheet::dismiss);
        }
        pane.primary("保存待办", () -> perform(() -> {
            String cleanTime = TodoStore.TodoItem.validateTime(dueTimeInput.getText().toString());
            if (existing == null) {
                todos.add(
                    title.getText().toString(),
                    noteInput.getText().toString(),
                    chosenDay[0],
                    cleanTime,
                    remindSwitch.isChecked(),
                    leadMinutes[0]
                );
            } else {
                todos.update(
                    existing.id,
                    title.getText().toString(),
                    noteInput.getText().toString(),
                    chosenDay[0],
                    cleanTime,
                    remindSwitch.isChecked(),
                    leadMinutes[0],
                    existing.completed > 0
                );
            }
            if (remindSwitch.isChecked()) allowStudyNotifications();
            sheet.dismiss();
            render();
        }));
        sheet.push(pane);
    }

    private static String todoLeadSubtitle(boolean enabled, int leadMinutes, String defaultTime) {
        if (!enabled) return "已关闭 · 轻触唤醒森林提醒";
        if (leadMinutes >= 1440) return "已开启 · 截止前一天 " + defaultTime + " 发送森林提醒";
        if (leadMinutes >= 60 && leadMinutes % 60 == 0)
            return "已开启 · 截止前 " + (leadMinutes / 60) + " 小时森林提醒";
        return "已开启 · 截止前 " + leadMinutes + " 分钟森林提醒";
    }

    private void todoReminderSettingsDialog() {
        TodoStore todos = TodoStore.get(ctx());
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("待办提醒设置", "独立管理待办任务的截止消息提醒。", R.drawable.ic_list);
        MaterialCheckBox masterSwitch = ForestUi.switchWithSubtitle(
            ctx(),
            "开启待办截止提醒",
            todos.remindEnabled()
                ? "已开启 · 未设具体时刻时于 " + todos.defaultRemindTime() + " 提醒"
                : "已关闭 · 开启后在待办截止前自动发送消息"
        );
        masterSwitch.setChecked(todos.remindEnabled());
        pane.body.addView(masterSwitch);

        EditText timeInput = new EditText(ctx());
        timeInput.setSingleLine(true);
        timeInput.setText(todos.defaultRemindTime());
        timeInput.setHint("例如 18:00");
        StudySheet.styleField(timeInput);
        ForestUi.add(pane.body, StudySheet.section(ctx(), "默认每日提醒时间（HH:mm）"), 14);
        ForestUi.add(pane.body, timeInput, 6);

        LinearLayout presets = row(ctx());
        String[] times = {"09:00", "12:00", "18:00", "20:30"};
        for (int i = 0; i < times.length; i++) {
            String t = times[i];
            TextView chip = StudySheet.link(ctx(), t, DEEP, () -> {
                timeInput.setText(t);
                masterSwitch.setChecked(true);
                ForestUi.updateSwitchSubtitle(masterSwitch, "已开启 · 未设具体时刻时于 " + t + " 提醒");
            });
            chip.setTextSize(12);
            chip.setMinimumHeight(dp(ctx(), 36));
            chip.setBackground(ripple(ctx(), rounded(ctx(), 0xfff3efe3, 0xffdcd8c8, 10), false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(ctx(), 6));
            presets.addView(chip, lp);
        }
        ForestUi.add(pane.body, presets, 8);

        masterSwitch.setOnCheckedChangeListener((b, checked) -> {
            String t = timeInput.getText().toString().trim();
            if (t.isEmpty()) t = todos.defaultRemindTime();
            ForestUi.updateSwitchSubtitle(
                masterSwitch,
                checked
                    ? "已开启 · 未设具体时刻时于 " + t + " 提醒"
                    : "已关闭 · 开启后在待办截止前自动发送消息"
            );
        });

        TextView testBtn = StudySheet.link(ctx(), "立即发送一条测试提醒消息", 0xff355336, () -> {
            allowStudyNotifications();
            boolean sent = TodoNotifications.sendImmediate(
                ctx(),
                "待办即将截止提醒",
                "提醒功能已就绪！将在待办截止时间前自动通知你。"
            );
            toast(sent ? "已发送测试提醒通知，请查看顶部通知栏" : "请先允许应用发送系统通知");
        });
        testBtn.setMinimumHeight(dp(ctx(), 40));
        testBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xffeaf1e5, 0xffb8cbb2, 11), false));
        ForestUi.add(pane.body, testBtn, 12);

        pane.secondary("取消", sheet::dismiss);
        pane.primary("保存提醒设置", () -> perform(() -> {
            todos.setReminderConfig(masterSwitch.isChecked(), timeInput.getText().toString());
            if (masterSwitch.isChecked()) allowStudyNotifications();
            sheet.dismiss();
            render();
        }));
        sheet.push(pane);
    }

    private void addTodoHairline(LinearLayout parent) {
        View hairline = new View(ctx());
        hairline.setBackgroundColor(0xffeae6d9);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(ctx(), 1));
        lp.setMarginStart(dp(ctx(), 46));
        parent.addView(hairline, lp);
    }

    private Map<String, List<StudyWork.Task>> groupTasksByDate(
        List<StudyWork.Task> tasks,
        String todayKey,
        String tomorrowKey
    ) {
        Map<String, List<StudyWork.Task>> groups = new LinkedHashMap<>();
        List<StudyWork.Task> sorted = new ArrayList<>(tasks);
        sorted.sort((a, b) -> {
            String da = a.day.isEmpty() ? "9999-99-99" : a.day;
            String db = b.day.isEmpty() ? "9999-99-99" : b.day;
            int cmp = da.compareTo(db);
            if (cmp != 0) return cmp;
            String ta = a.dueTime.isEmpty() ? "99:99" : a.dueTime;
            String tb = b.dueTime.isEmpty() ? "99:99" : b.dueTime;
            int tcmp = ta.compareTo(tb);
            return tcmp != 0 ? tcmp : Long.compare(b.priority, a.priority);
        });
        for (StudyWork.Task task : sorted) {
            String header;
            if (task.day.isEmpty()) {
                header = "日常待办 · 未限日期";
            } else if (task.day.compareTo(todayKey) < 0) {
                header = "已逾期 · " + task.day;
            } else if (task.day.equals(todayKey)) {
                header = "今天 · " + todayKey;
            } else if (task.day.equals(tomorrowKey)) {
                header = "明天 · " + tomorrowKey;
            } else {
                header = "日期 · " + task.day;
            }
            groups.computeIfAbsent(header, k -> new ArrayList<>()).add(task);
        }
        return groups;
    }

    private String formatTaskMeta(
        @Nullable StudySubject subject,
        StudyWork.Task task,
        String todayKey,
        String tomorrowKey
    ) {
        StringBuilder sb = new StringBuilder();
        if (subject != null) sb.append(subject.title);
        if (!task.day.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            if (task.day.equals(todayKey)) sb.append("今天");
            else if (task.day.equals(tomorrowKey)) sb.append("明天");
            else sb.append(task.day);
        }
        if (!task.dueTime.isEmpty()) {
            sb.append(" ").append(task.dueTime).append(" 截止");
        }
        if (task.remind) {
            sb.append(" · ").append(task.leadLabel()).append("提醒");
        }
        return sb.toString();
    }

    private void reminderSettingsDialog(StudyPlan plan, @Nullable Runnable afterSave) {
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("提醒设置", "在考试、科目截止日与待办任务到期前发送通知。",
            R.drawable.ic_leaf);
        MaterialCheckBox masterSwitch = ForestUi.switchWithSubtitle(
            ctx(),
            "在考试或截止前一天傍晚提醒",
            planRemindSubtitle(plan)
        );
        masterSwitch.setChecked(plan.remindDeadlines);
        pane.body.addView(masterSwitch);

        EditText timeInput = new EditText(ctx());
        timeInput.setSingleLine(true);
        timeInput.setText(plan.remindTime);
        timeInput.setHint("例如 18:00");
        timeInput.setContentDescription("每日提醒时间");
        StudySheet.styleField(timeInput);
        ForestUi.add(pane.body, StudySheet.section(ctx(), "默认提醒时间（HH:mm）"), 14);
        ForestUi.add(pane.body, timeInput, 6);

        LinearLayout presets = row(ctx());
        String[] times = {"09:00", "12:00", "18:00", "20:30"};
        for (int i = 0; i < times.length; i++) {
            String t = times[i];
            TextView chip = StudySheet.link(ctx(), t, DEEP, () -> {
                timeInput.setText(t);
                masterSwitch.setChecked(true);
                ForestUi.updateSwitchSubtitle(masterSwitch, "已开启 · " + t + " 发送森林自习提醒");
            });
            chip.setTextSize(12);
            chip.setMinimumHeight(dp(ctx(), 36));
            chip.setBackground(ripple(ctx(), rounded(ctx(), 0xfff3efe3, 0xffdcd8c8, 10), false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(ctx(), 6));
            presets.addView(chip, lp);
        }
        ForestUi.add(pane.body, presets, 8);

        masterSwitch.setOnCheckedChangeListener((b, checked) -> {
            String currentT = timeInput.getText().toString().trim();
            if (currentT.isEmpty()) currentT = plan.remindTime;
            ForestUi.updateSwitchSubtitle(
                masterSwitch,
                checked ? "已开启 · " + currentT + " 发送森林自习提醒" : "已关闭 · 开启后在截止前发送森林自习提醒"
            );
        });

        TextView testBtn = StudySheet.link(ctx(), "立即发送一条测试提醒消息", 0xff355336, () -> {
            allowStudyNotifications();
            boolean sent = StudyNotifications.sendImmediateReminder(
                ctx(),
                plan.id,
                "森林自习室 · 待办与截止提醒",
                "提醒功能已就绪！将在待办截止时间前自动通知你。"
            );
            toast(sent ? "已发送测试提醒通知，请查看顶部通知栏" : "请先允许应用发送系统通知");
        });
        testBtn.setMinimumHeight(dp(ctx(), 40));
        testBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xffeaf1e5, 0xffb8cbb2, 11), false));
        ForestUi.add(pane.body, testBtn, 12);

        pane.secondary("取消", sheet::dismiss);
        pane.primary("保存提醒设置", () -> perform(() -> {
            String clean = StudyWork.Task.validateTime(timeInput.getText().toString());
            if (clean.isEmpty()) throw new IllegalArgumentException("请输入时间，例如 18:00");
            store.update(n -> {
                n.remindDeadlines(plan.id, masterSwitch.isChecked());
                n.setRemindTime(plan.id, clean);
                return null;
            });
            if (masterSwitch.isChecked()) allowStudyNotifications();
            sheet.dismiss();
            if (afterSave != null) afterSave.run();
        }));
        sheet.push(pane);
    }

    private void showAgenda() {
        StudyPlan plan = store.notebook().currentPlan();
        if (plan == null || plan.archived != 0) return;
        StudySheet sheet = newSheet();
        StudyWorkDialogs work = workDialogs(sheet);
        StudySheet.Pane pane = work.pane("今天安排", plan.title);
        Runnable refresh = () -> {
            sheet.dismiss();
            render();
            showAgenda();
        };
        pane.primary("添加事项", () -> work.taskForm(plan, plan.book(plan.selected), null, refresh, null));
        pane.secondary("设置科目日期", () -> chooseDeadline(work, sheet, plan, refresh));

        MaterialCheckBox remind = ForestUi.switchWithSubtitle(
            ctx(),
            "在截止前一天傍晚提醒",
            planRemindSubtitle(plan)
        );
        remind.setChecked(plan.remindDeadlines);
        remind.setOnClickListener(v -> perform(() -> {
            store.update(n -> { n.remindDeadlines(plan.id, remind.isChecked()); return null; });
            ForestUi.updateSwitchSubtitle(remind, planRemindSubtitle(plan));
            if (remind.isChecked()) allowStudyNotifications();
        }));
        ForestUi.add(pane.body, remind, 0);

        LinearLayout remindActions = row(ctx());
        TextView editTimeBtn = StudySheet.link(
            ctx(),
            "设置提醒时间 · " + plan.remindTime,
            DEEP,
            () -> reminderSettingsDialog(plan, refresh)
        );
        editTimeBtn.setTextSize(12);
        editTimeBtn.setMinimumHeight(dp(ctx(), 34));
        editTimeBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xfff3efe3, 0xffdcd8c8, 10), false));
        remindActions.addView(editTimeBtn, new LinearLayout.LayoutParams(0, -2, 1));

        TextView testRemindBtn = StudySheet.link(ctx(), "测试提醒通知", DEEP, () -> {
            allowStudyNotifications();
            boolean ok = StudyNotifications.sendImmediateReminder(
                ctx(),
                plan.id,
                "待办即将截止提醒",
                "这是来自森林自习室的截止提醒消息预览"
            );
            toast(ok ? "已发送提醒通知" : "请先开启系统通知权限");
        });
        testRemindBtn.setTextSize(12);
        testRemindBtn.setMinimumHeight(dp(ctx(), 34));
        testRemindBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xffeaf1e5, 0xffb8cbb2, 10), false));
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(0, -2, 1);
        testLp.setMarginStart(dp(ctx(), 8));
        remindActions.addView(testRemindBtn, testLp);
        ForestUi.add(pane.body, remindActions, 6);

        if (plan.remindDeadlines && !StudyNotifications.permitted(ctx())) {
            ForestUi.add(pane.body, StudySheet.link(ctx(), "开启系统通知", DEEP,
                this::allowStudyNotifications), 2);
        }

        List<StudyWork.Task> tasks = store.notebook().pendingTasks(plan.id);
        String todayKey = StudyNotebook.dayKey(System.currentTimeMillis());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        String tomorrowKey = StudyNotebook.dayKey(cal.getTimeInMillis());

        int[] filterMode = {0}; // 0: 全部按日期, 1: 今天, 2: 未来安排, 3: 已完成
        LinearLayout filterTabs = row(ctx());
        filterTabs.setBackground(rounded(ctx(), 0xffebe7da, 0xffdcd8c8, 12));
        filterTabs.setPadding(dp(ctx(), 4), dp(ctx(), 4), dp(ctx(), 4), dp(ctx(), 4));
        String[] filterLabels = {"按日期分类", "今天", "未来安排", "已完成"};
        List<TextView> tabViews = new ArrayList<>();
        LinearLayout taskListContainer = column(ctx());

        Runnable renderTasks = () -> {
            for (int i = 0; i < tabViews.size(); i++) {
                boolean active = filterMode[0] == i;
                TextView tv = tabViews.get(i);
                tv.setTextColor(active ? 0xff243624 : 0xff6b7568);
                tv.setTypeface(Typeface.create(active ? "sans-serif-medium" : "sans-serif",
                    active ? Typeface.BOLD : Typeface.NORMAL));
                tv.setBackground(ripple(ctx(), rounded(ctx(),
                    active ? 0xfffffcf3 : Color.TRANSPARENT,
                    active ? 0xffc6d4c0 : 0, 9), false));
            }
            taskListContainer.removeAllViews();
            if (filterMode[0] == 3) {
                List<StudyWork.Task> doneTasks = store.notebook().completedTasks(plan.id, 0, Long.MAX_VALUE);
                if (doneTasks.isEmpty()) {
                    ForestUi.add(taskListContainer, note("还没有已完成的待办事项"), 6);
                } else {
                    for (StudyWork.Task doneTask : doneTasks) {
                        StudySubject subj = plan.book(doneTask.subjectId);
                        MaterialCheckBox cb = ForestUi.checkWithSubtitle(
                            ctx(),
                            doneTask.title,
                            (subj == null ? "" : subj.title + " · ") + "已完成"
                        );
                        cb.setChecked(true);
                        cb.setOnClickListener(v -> perform(() -> {
                            store.update(n -> {
                                n.changeTask(doneTask.id, doneTask.title, doneTask.day,
                                    doneTask.dueTime, doneTask.remind, doneTask.remindLeadMinutes,
                                    false, System.currentTimeMillis());
                                return null;
                            });
                            refresh.run();
                        }));
                        ForestUi.add(taskListContainer, cb, 6);
                    }
                }
                return;
            }

            List<StudyWork.Task> filtered = new ArrayList<>();
            for (StudyWork.Task t : tasks) {
                if (filterMode[0] == 1 && !t.day.equals(todayKey)) continue;
                if (filterMode[0] == 2 && t.day.compareTo(todayKey) <= 0) continue;
                filtered.add(t);
            }
            if (filtered.isEmpty()) {
                ForestUi.add(taskListContainer, note("还没有待做事项"), 6);
                return;
            }
            Map<String, List<StudyWork.Task>> grouped = groupTasksByDate(filtered, todayKey, tomorrowKey);
            for (Map.Entry<String, List<StudyWork.Task>> entry : grouped.entrySet()) {
                TextView dateHeader = text(
                    ctx(),
                    entry.getKey() + " · " + entry.getValue().size() + " 项",
                    12,
                    WOOD
                );
                dateHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                ForestUi.add(taskListContainer, dateHeader, 10);
                for (StudyWork.Task task : entry.getValue()) {
                    StudySubject subject = plan.book(task.subjectId);
                    String detail = formatTaskMeta(subject, task, todayKey, tomorrowKey);
                    Runnable start = store.notebook().hasSession() ? null : () -> perform(() -> startTask(task.id));
                    ForestUi.add(
                        taskListContainer,
                        StudySheet.item(
                            ctx(),
                            task.title,
                            detail,
                            0,
                            0xfff7f4ea,
                            "›",
                            false,
                            () -> work.taskForm(plan, subject, task, refresh, start)
                        ),
                        5
                    );
                }
            }
        };

        for (int i = 0; i < filterLabels.length; i++) {
            int idx = i;
            TextView tab = text(ctx(), filterLabels[i], 11, 0xff6b7568);
            tab.setGravity(Gravity.CENTER);
            tab.setOnClickListener(v -> {
                filterMode[0] = idx;
                renderTasks.run();
            });
            tabViews.add(tab);
            addEqual(filterTabs, tab, 34);
        }
        ForestUi.add(pane.body, filterTabs, 12);
        ForestUi.add(pane.body, taskListContainer, 2);
        renderTasks.run();

        ForestUi.add(pane.body, StudySheet.section(ctx(), "近期考试与截止"), 16);
        List<StudySubject> deadlines = StudyWorkDialogs.deadlines(plan);
        if (deadlines.isEmpty()) ForestUi.add(pane.body, note("还没有设置近期日期"), 6);
        for (StudySubject subject : deadlines) {
            String detail = subject.due + (subject.assessment.isEmpty() ? "" : " · " + subject.assessment);
            ForestUi.add(pane.body, StudySheet.item(ctx(), subject.title, detail, 0, 0, "›", false,
                () -> work.taskForm(plan, subject, null, refresh, null)), 4);
        }
        sheet.push(pane);
    }

    private void chooseDeadline(StudyWorkDialogs work, StudySheet sheet, StudyPlan plan, Runnable changed) {
        StudySheet.Pane picker = work.pane("设置科目日期", plan.title);
        for (StudySubject subject : plan.books()) {
            ForestUi.add(picker.body, StudySheet.item(ctx(), subject.title, subject.due.isEmpty() ? "未设置" : subject.due,
                0, 0, "›", false, () -> {
                    StudySheet.Pane editor = work.pane(subject.title, subject.assessment);
                    StudyWorkDialogs.DateChoice day = work.chooseDate(editor.body, "考试或提交日", subject.due, true);
                    editor.primary("保存日期", () -> work.save(() -> {
                        store.update(n -> { n.setDeadline(plan.id, subject.id, day.day); return null; });
                        changed.run();
                    }));
                    sheet.push(editor);
                }), 4);
        }
        sheet.push(picker);
    }

    private void showSubjectSheet() {
        StudyNotebook notebook = store.notebook();
        if (notebook.currentHasSession()) return;
        StudyPlan plan = notebook.currentPlan();
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("今天学哪一门？", plan.title + " · " + plan.books().size() + " 门科目",
            R.drawable.ic_study_book);
        pane.body.setId(R.id.study_subject_picker);
        for (StudySubject subject : plan.books()) ForestUi.add(pane.body, subjectChoice(sheet, subject), 5);
        sheet.push(pane);
    }

    private View subjectChoice(StudySheet sheet, StudySubject subject) {
        StudyNotebook n = store.notebook();
        String details = subject.hint;
        if (!subject.due.isEmpty()) details += (details.isEmpty() ? "" : " · ") + subject.due;
        if (!subject.assessment.isEmpty()) details += (details.isEmpty() ? "" : " · ") + subject.assessment;
        boolean selected = subject.equals(n.selected());
        LinearLayout choice = StudySheet.item(ctx(), subject.title, details.isEmpty() ? null : details,
            0, selected ? StudySheet.MOSS : Color.TRANSPARENT, selected ? "✓" : null, selected,
            () -> perform(() -> {
                store.update(book -> {
                    book.select(subject);
                    return null;
                });
                sheet.dismiss();
                render();
                if (subjectSelector != null) ForestMotion.reveal(subjectSelector);
            }));
        choice.setContentDescription("选择" + subject.title + (selected ? "，当前科目" : ""));
        View dot = new View(ctx());
        dot.setBackground(rounded(ctx(), subject.color, 0, 5));
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(ctx(), 9), dp(ctx(), 9));
        params.setMarginEnd(dp(ctx(), 12));
        choice.addView(dot, 0, params);
        return choice;
    }

    private void updateTimer() {
        StudyNotebook notebook = store.notebook();
        long elapsed = notebook.currentHasSession() ? store.elapsedMillis() : 0;
        if (clock != null) clock.setText(StudyFormat.clock(elapsed));
        if (dial != null) dial.setProgress(
            (float) elapsed / (notebook.activeGoal() * 60_000L)
        );
        if (today != null) {
            StudyNotebook n = store.notebook();
            String planId = n.currentPlan().id;
            int count = 0;
            long now = System.currentTimeMillis();
            String day = StudyNotebook.dayKey(now);
            if (day.equals(renderedToday)) return;
            renderedToday = day;
            for (StudyNotebook.Record r : n.records())
                if (r.planId.equals(planId) && StudyNotebook.dayKey(r.completedAt).equals(day)) count++;
            today.setText(
                "今日已专注 " +
                    StudyFormat.duration(n.totalForDay(planId, now)) +
                    "\n每日目标 " +
                    n.dailyGoal() +
                    "分钟 · " +
                    count +
                    "次学习"
            );
        }
    }

    private void dailyGoalDialog() {
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("每日学习目标", "每天首次达标时获得一颗种子。",
            R.drawable.ic_study_sprout);
        EditText input = new EditText(ctx());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(store.notebook().dailyGoal()));
        input.setSelectAllOnFocus(true);
        input.setContentDescription("每日学习目标分钟");
        StudySheet.styleField(input);
        pane.body.addView(StudySheet.section(ctx(), "目标时长（分钟）"));
        ForestUi.add(pane.body, input, 7);
        LinearLayout presets = row(ctx());
        int[] presetValues = { 60, 120, 180 };
        for (int i = 0; i < presetValues.length; i++) {
            int minutes = presetValues[i];
            TextView option = StudySheet.link(ctx(), minutes + " 分钟", DEEP,
                () -> input.setText(String.valueOf(minutes)));
            option.setMinimumHeight(dp(ctx(), 38));
            option.setBackground(ripple(ctx(), rounded(ctx(), 0xfff5f2e6, 0xffd8d5c2, 10), false));
            LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) presetParams.setMarginStart(dp(ctx(), 6));
            presets.addView(option, presetParams);
        }
        ForestUi.add(pane.body, presets, 8);
        TextView errorMessage = text(ctx(), "", 12, 0xff8d4836);
        errorMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ForestUi.add(pane.body, errorMessage, 8);
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
        pane.primary("保存目标", () -> {
            try {
                int value = Integer.parseInt(input.getText().toString().trim());
                store.update(n -> { n.setDailyGoal(value); return null; });
                sheet.dismiss();
                render();
            } catch (IllegalArgumentException | IllegalStateException error) {
                errorMessage.setText(error instanceof NumberFormatException
                    ? "请输入10至720之间的分钟数" : error.getMessage());
                errorMessage.setVisibility(View.VISIBLE);
            }
        });
        sheet.push(pane);
    }

    private void finishDialog() {
        if (store.notebook().running()) store.pause();
        render();
        long elapsed = store.elapsedMillis();
        if (elapsed < 1000) {
            confirmDiscard(newSheet());
            return;
        }
        StudySheet sheet = newSheet();
        StudyWorkDialogs work = workDialogs(sheet);
        StudySheet.Pane pane = sheetPane("保存这段专注", null, R.drawable.ic_leaf);
        StudyNotebook notebook = store.notebook();
        String sessionId = notebook.sessionId();
        StudyPlan plan = notebook.plan(notebook.activePlanId());
        StudyWork.Task task = notebook.task(notebook.activeTaskId());
        StudySubject[] subject = { notebook.activeSubject() };
        long[] duration = { elapsed };

        LinearLayout receipt = row(ctx());
        receipt.setPadding(dp(ctx(), 16), dp(ctx(), 14), dp(ctx(), 16), dp(ctx(), 14));
        receipt.setBackground(rounded(ctx(), 0xffebf2e6, 0xffc8dac2, 16));
        LinearLayout receiptCopy = column(ctx());
        TextView subjectName = text(ctx(), "", 13, 0xff355336);
        subjectName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        subjectName.setText(ForestUi.badgeText(ctx(), subject[0].hint, subject[0].title, ""));
        TextView length = heading(ctx(), StudyFormat.duration(elapsed), 26);
        length.setTypeface(Typeface.create("serif", Typeface.BOLD));
        length.setTextColor(0xff243624);
        receiptCopy.addView(subjectName);
        ForestUi.add(receiptCopy, length, 6);
        receipt.addView(receiptCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView correctBtn = StudySheet.link(ctx(), "修正时长或科目", 0xff355336, () -> {
            StudySheet.Pane correction = work.pane("修正本次学习", "时长计入保存当天");
            StudyWorkDialogs.SubjectChoice selected = work.chooseSubject(correction.body, plan, subject[0]);
            EditText time = work.input(correction.body, "学习时长（时:分:秒）", "例如 00:35:00",
                StudyFormat.clock(duration[0]), 16);
            correction.primary("应用修正", () -> work.save(() -> {
                duration[0] = StudyWorkDialogs.duration(time, duration[0]);
                subject[0] = selected.selected;
                subjectName.setText(ForestUi.badgeText(ctx(), subject[0].hint, subject[0].title, ""));
                length.setText(StudyFormat.duration(duration[0]));
                sheet.pop();
            }));
            sheet.push(correction);
        });
        correctBtn.setTextSize(12);
        correctBtn.setMinimumHeight(dp(ctx(), 34));
        correctBtn.setPadding(dp(ctx(), 12), dp(ctx(), 6), dp(ctx(), 12), dp(ctx(), 6));
        correctBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xfffffcf3, 0xffc8d6c3, 999), false));
        LinearLayout.LayoutParams correctLp = new LinearLayout.LayoutParams(-2, -2);
        correctLp.setMarginStart(dp(ctx(), 10));
        receipt.addView(correctBtn, correctLp);
        ForestUi.add(pane.body, receipt, 0);

        LinearLayout noteHeader = row(ctx());
        noteHeader.addView(StudySheet.section(ctx(), "学习手记（选填）"),
            new LinearLayout.LayoutParams(0, -2, 1));
        TextView discardLink = StudySheet.link(ctx(), "放弃本次", 0xff8d4836,
            () -> confirmDiscard(sheet));
        discardLink.setTextSize(12);
        discardLink.setMinimumHeight(dp(ctx(), 30));
        discardLink.setPadding(dp(ctx(), 8), dp(ctx(), 2), dp(ctx(), 4), dp(ctx(), 2));
        noteHeader.addView(discardLink);
        ForestUi.add(pane.body, noteHeader, 12);

        EditText input = new EditText(ctx());
        input.setId(R.id.study_note_input);
        input.setHint("留一句学习手记（选填）");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(120) });
        input.setMinLines(1);
        StudySheet.styleField(input);
        ForestUi.add(pane.body, input, 5);

        String doneSub = task != null ? task.title : "保存后累计水滴，不满 25 分钟也会留到下一次";
        MaterialCheckBox done = ForestUi.checkWithSubtitle(ctx(), "本次目标已完成", doneSub);
        if (task == null) done.setChecked(true);
        ForestUi.add(pane.body, done, 10);

        EditText next = work.input(pane.body, "下次从哪继续（选填）", "例如 接着做第 5–8 题", "", 120);
        pane.primary("保存学习", () -> perform(() -> {
            if (store.notebook().running() || !sessionId.equals(store.notebook().sessionId())) {
                sheet.dismiss();
                render();
                toast("计时状态已变化，请重新结束这段学习");
                return;
            }
            StudyNotebook.Completion reward = store.finish(input.getText().toString(), subject[0].id,
                duration[0] == elapsed ? 0 : duration[0], done.isChecked(), StudyWorkDialogs.value(next));
            render();
            StudySheet.Pane result = sheetPane("学习已保存", null, R.drawable.ic_study_sprout);
            result.body.addView(rewardReceipt(reward));
            ForestUi.add(result.body, StudySheet.link(ctx(), "去看看我的小树", DEEP, () -> {
                sheet.dismiss();
                open(AppDestination.STUDY_GARDEN);
            }), 4);
            result.primary("继续学习", sheet::dismiss);
            result.secondary("切换科目", () -> {
                sheet.dismiss();
                showSubjectSheet();
            });
            sheet.replace(result);
        }));
        pane.secondary("稍后继续", sheet::dismiss);
        sheet.push(pane);
    }

    private void confirmDiscard(StudySheet sheet) {
        StudySheet.Pane pane = sheetPane("放弃这段学习？", "放弃后，本次计时将被清除。",
            R.drawable.ic_study_book);
        pane.primary("保留计时", sheet::pop);
        pane.secondary("确认放弃", () -> perform(() -> {
            store.update(n -> { n.discard(); return null; });
            sheet.dismiss();
            render();
        }));
        sheet.push(pane);
    }

    private View rewardReceipt(StudyNotebook.Completion reward) {
        LinearLayout receipt = column(ctx());
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
        StudyNotebook notebook = store.notebook();
        StudyPlan scope = statsPlan == null ? notebook.currentPlan() : notebook.plan(statsPlan);
        String scopeId = scope == null ? null : scope.id;
        StudyPlan initialForRecord = scope != null ? scope : notebook.currentPlan();

        LinearLayout headerRow = row(ctx());
        headerRow.addView(heading(ctx(), "学习足迹", 25), new LinearLayout.LayoutParams(0, -2, 1));
        if (initialForRecord != null) {
            TextView manualBtn = StudySheet.link(ctx(), "补记学习", 0xff355336, () -> {
                StudySheet sheet = newSheet();
                workDialogs(sheet).recordForm(initialForRecord, null, () -> {
                    sheet.dismiss();
                    render();
                    toast("已补记学习");
                });
            });
            manualBtn.setTextSize(12);
            manualBtn.setMinimumHeight(dp(ctx(), 34));
            manualBtn.setPadding(dp(ctx(), 12), dp(ctx(), 6), dp(ctx(), 12), dp(ctx(), 6));
            manualBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xfff3efe3, 0xffdcd8c8, 999), false));
            headerRow.addView(manualBtn);
        }
        ForestUi.add(content, headerRow, 14);

        TextView scopeButton = StudySheet.link(
            ctx(),
            (scope == null ? "所有计划" : scope.title + (scope.archived == 0 ? "" : " · 已归档")) + " ▾",
            0xff243624,
            this::filterPlans
        );
        scopeButton.setTextSize(12);
        scopeButton.setMinimumHeight(dp(ctx(), 32));
        scopeButton.setPadding(dp(ctx(), 12), dp(ctx(), 5), dp(ctx(), 12), dp(ctx(), 5));
        scopeButton.setBackground(ripple(ctx(), rounded(ctx(), 0xffe9efe3, 0xffbfd0b9, 999), false));
        LinearLayout.LayoutParams scopeLp = new LinearLayout.LayoutParams(-2, -2);
        scopeLp.topMargin = dp(ctx(), 8);
        content.addView(scopeButton, scopeLp);

        int[] periods = {1, 7, 30, 0};
        LinearLayout periodSelector = statsSegments(
            new String[] {"今天", "近7天", "近30天", "全部"},
            new int[] {R.id.study_date_today, R.id.study_date_week, R.id.study_date_month, View.NO_ID},
            days == 0 ? 3 : days == 30 ? 2 : days == 7 ? 1 : 0,
            index -> {
                if (days == periods[index]) return;
                days = periods[index];
                render();
            }
        );
        ForestUi.add(content, periodSelector, 12);
        List<StudyNotebook.Record> allScoped = notebook.forPlan(scopeId);
        Calendar end = Calendar.getInstance();
        if (days == 0 && !allScoped.isEmpty()) end.setTimeInMillis(allScoped.get(0).completedAt);
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        end.add(Calendar.DATE, 1);
        Calendar start = (Calendar) end.clone();
        if (days == 0 && !allScoped.isEmpty()) {
            start.setTimeInMillis(allScoped.get(allScoped.size() - 1).completedAt);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
        } else start.add(Calendar.DATE, -Math.max(days, 1));
        int chartDays = days > 0 ? days : (int) Math.max(1, Math.round(
            (end.getTimeInMillis() - start.getTimeInMillis()) / 86_400_000.0));
        List<StudyNotebook.Record> records = days == 0 ? allScoped
            : notebook.between(scopeId, start.getTimeInMillis(), end.getTimeInMillis());
        long total = 0;
        Set<String> dates = new HashSet<>();
        for (StudyNotebook.Record record : records) {
            total += record.durationMillis;
            dates.add(StudyNotebook.dayKey(record.completedAt));
        }
        String range = statsRange(start, end, records.isEmpty());
        ForestUi.add(content, durationSummary(range, total, records.size(), dates.size()), 14);
        LinearLayout card = statsPanel(LIGHT, LINE);
        card.addView(heading(ctx(), chart == StudyChartView.Kind.TREND && !records.isEmpty()
            ? "学习趋势" : "科目分布", 19));
        if (!records.isEmpty()) {
            ForestUi.add(card, statsSegments(
                new String[] {"环形时长", "科目时长", "每日趋势"},
                new int[] {R.id.study_chart_ring, R.id.study_chart_bars, R.id.study_chart_trend},
                chart.ordinal(),
                index -> {
                    StudyChartView.Kind type = StudyChartView.Kind.values()[index];
                    if (chart == type) return;
                    chart = type;
                    render();
                }
            ), 11);
        }
        if (records.isEmpty()) {
            LinearLayout empty = column(ctx());
            empty.setId(R.id.study_chart_view);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            View meadow = image(ctx(), R.drawable.crayon_study_meadow, null);
            meadow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            empty.addView(meadow, new LinearLayout.LayoutParams(-1, dp(ctx(), 86)));
            TextView message = text(ctx(), allScoped.isEmpty() ? "还没有学习记录" : "这段时间没有学习记录", 14, DEEP);
            message.setGravity(Gravity.CENTER);
            ForestUi.add(empty, message, 2);
            ForestUi.add(card, empty, 6);
        } else {
            StudyChartView graphic = new StudyChartView(ctx());
            graphic.setId(R.id.study_chart_view);
            graphic.setData(records, chart, start.getTimeInMillis(), chartDays);
            ForestUi.add(card, graphic, 8);
        }
        if (records.isEmpty() && !allScoped.isEmpty()) {
            ForestUi.add(card, StudySheet.link(ctx(), "查看全部时间", DEEP,
                () -> setStatsScope(statsPlan, 0)), 0);
        } else if (records.isEmpty() && scope != null && scope == notebook.currentPlan() && scope.archived == 0) {
            ForestUi.add(card, StudySheet.link(ctx(), "去专注", DEEP, () -> open(AppDestination.STUDY_FOCUS)), 0);
        }
        ForestUi.add(content, card, 16);
        weeklyReview(content, scopeId);
        if (allScoped.isEmpty()) return;
        LinearLayout history = row(ctx());
        history.addView(heading(ctx(), records.isEmpty() ? "学习记录" : "科目汇总", 18),
            new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton all = button(ctx(), "查看全部", false);
        all.setTextSize(12);
        all.setMinHeight(dp(ctx(), 48));
        all.setContentDescription("查看全部学习记录，不限时间");
        all.setOnClickListener(v -> showRecords(scopeId, () -> store.notebook().forPlan(scopeId), "全部时间", null));
        history.addView(all);
        ForestUi.add(content, history, 18);
        if (records.isEmpty()) return;
        LinearLayout list = statsPanel(LIGHT, LINE);
        list.setId(R.id.study_records_container);
        ForestUi.add(content, list, 7);
        subjectSummary(list, records, total, subject -> showRecords(scopeId, () -> days == 0
            ? store.notebook().forPlan(scopeId)
            : store.notebook().between(scopeId, start.getTimeInMillis(), end.getTimeInMillis()), range, subject));
    }

    private void weeklyReview(LinearLayout content, String scopeId) {
        Calendar end = StudyFormat.day(StudyNotebook.dayKey(System.currentTimeMillis()));
        end.add(Calendar.DATE, 1);
        Calendar start = (Calendar) end.clone();
        start.add(Calendar.DATE, -7);
        List<StudyWork.Task> completed = store.notebook().completedTasks(scopeId,
            start.getTimeInMillis(), end.getTimeInMillis());
        List<StudyWork.Review> pending = store.notebook().pendingReviews(scopeId);
        int reviewed = store.notebook().completedReviews(scopeId, start.getTimeInMillis(), end.getTimeInMillis()).size();
        if (completed.isEmpty() && pending.isEmpty() && reviewed == 0) return;
        String summary = "完成 " + completed.size() + " 项 · 待回顾 " + pending.size() + " 项";
        if (reviewed > 0) summary += " · 已回顾 " + reviewed + " 项";
        ForestUi.add(content, StudySheet.item(ctx(), "近7天回顾", summary, R.drawable.ic_study_trail,
            StudySheet.MOSS, "›", false, () -> showWeeklyReview(scopeId)), 12);
    }

    private void showWeeklyReview(String scopeId) {
        StudySheet sheet = newSheet();
        StudyWorkDialogs work = workDialogs(sheet);
        refreshWeeklyReview(work, sheet, scopeId, false);
    }

    private void refreshWeeklyReview(StudyWorkDialogs work, StudySheet sheet, String scopeId, boolean replace) {
        StudyNotebook notebook = store.notebook();
        String today = StudyNotebook.dayKey(System.currentTimeMillis());
        Calendar end = StudyFormat.day(today);
        end.add(Calendar.DATE, 1);
        Calendar start = (Calendar) end.clone();
        start.add(Calendar.DATE, -7);
        List<StudyWork.Task> completed = notebook.completedTasks(scopeId, start.getTimeInMillis(), end.getTimeInMillis());
        List<StudyWork.Review> pending = notebook.pendingReviews(scopeId);
        StudySheet.Pane pane = work.pane("近7天回顾", null);
        ForestUi.add(pane.body, StudySheet.section(ctx(), "待回顾"), 0);
        if (pending.isEmpty()) ForestUi.add(pane.body, note("回顾可以从一条学习手记发起"), 4);
        for (StudyWork.Review review : pending) {
            StudyNotebook.Record source = notebook.record(review.recordId);
            String status = review.day.compareTo(today) <= 0 ? "可回顾" : review.day;
            ForestUi.add(pane.body, StudySheet.item(ctx(), review.prompt, source.subject.title + " · " + status, 0, 0,
                "›", false, () -> openReview(work, sheet, review, source, () -> {
                    sheet.pop();
                    refreshWeeklyReview(work, sheet, scopeId, true);
                    render();
                })), 4);
        }
        ForestUi.add(pane.body, StudySheet.section(ctx(), "已完成的事项"), 14);
        if (completed.isEmpty()) ForestUi.add(pane.body, note("这周还没有勾选完成的事项"), 4);
        for (StudyWork.Task task : completed) {
            StudyPlan plan = notebook.plan(task.planId);
            StudySubject subject = plan.book(task.subjectId);
            ForestUi.add(pane.body, note(task.title + " · " + (subject == null ? plan.title : subject.title)), 7);
        }
        List<StudyWork.Review> reviewed = notebook.completedReviews(scopeId, start.getTimeInMillis(), end.getTimeInMillis());
        if (!reviewed.isEmpty()) {
            ForestUi.add(pane.body, StudySheet.section(ctx(), "已完成的回顾"), 14);
            for (StudyWork.Review review : reviewed) {
                StudyNotebook.Record source = notebook.record(review.recordId);
                ForestUi.add(pane.body, StudySheet.item(ctx(), review.prompt, source.subject.title, 0, 0, "›", false,
                    () -> recordDetail(sheet, source, () -> {}, false)), 4);
            }
        }
        if (replace) sheet.replace(pane);
        else sheet.push(pane);
    }

    private void openReview(StudyWorkDialogs work, StudySheet sheet, StudyWork.Review review,
                            StudyNotebook.Record source, Runnable changed) {
        StudySheet.Pane pane = work.pane(source.subject.title, "先试着回答，再打开原手记核对");
        ForestUi.add(pane.body, heading(ctx(), review.prompt, 19), 4);
        ForestUi.add(pane.body, StudySheet.link(ctx(), "查看原学习记录", DEEP,
            () -> recordDetail(sheet, store.notebook().record(review.recordId), () -> {}, false)), 6);
        pane.primary("已掌握，完成回顾", () -> work.save(() -> {
            store.update(n -> {
                n.changeReview(source.id, review.prompt, review.day, true, System.currentTimeMillis());
                return null;
            });
            changed.run();
        }));
        pane.secondary("再约一次", () -> work.reviewForm(source, review, () -> {
            sheet.pop();
            changed.run();
        }));
        sheet.push(pane);
    }

    private LinearLayout statsPanel(int color, int border) {
        LinearLayout panel = column(ctx());
        panel.setBackground(rounded(ctx(), color, border, 20));
        panel.setPadding(dp(ctx(), 15), dp(ctx(), 15), dp(ctx(), 15), dp(ctx(), 14));
        return panel;
    }

    private LinearLayout statsSegments(String[] labels, int[] ids, int selected, IntConsumer change) {
        LinearLayout panel = column(ctx());
        panel.setBackground(rounded(ctx(), 0xffebe7da, 0xffdcd8c8, 13));
        int gap = dp(ctx(), 4);
        panel.setPadding(gap, gap, gap, gap);
        android.content.res.Configuration config = ctx().getResources().getConfiguration();
        int columns = config.screenWidthDp >= 350 && config.fontScale <= 1.2f
            ? labels.length : labels.length == 4 ? 2 : 1;
        for (int start = 0; start < labels.length; start += columns) {
            LinearLayout options = row(ctx());
            for (int i = start; i < start + columns; i++) {
                int index = i;
                boolean isSelected = i == selected;
                TextView chip = text(ctx(), labels[i], 12, isSelected ? 0xff243624 : 0xff6b7568);
                chip.setId(ids[i]);
                chip.setGravity(Gravity.CENTER);
                chip.setMinHeight(dp(ctx(), 38));
                chip.setMinimumHeight(dp(ctx(), 38));
                chip.setSelected(isSelected);
                chip.setFocusable(true);
                chip.setTypeface(Typeface.create(
                    isSelected ? "sans-serif-medium" : "sans-serif",
                    isSelected ? Typeface.BOLD : Typeface.NORMAL
                ));
                chip.setBackground(ripple(ctx(), rounded(ctx(),
                    isSelected ? 0xfffffcf3 : Color.TRANSPARENT,
                    isSelected ? 0xffc6d4c0 : 0, 10), false));
                chip.setOnClickListener(v -> change.accept(index));
                ForestMotion.bind(chip);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                if (i != start) params.setMarginStart(gap);
                options.addView(chip, params);
            }
            ForestUi.add(panel, options, start == 0 ? 0 : 4);
        }
        return panel;
    }

    private String statsRange(Calendar start, Calendar end, boolean empty) {
        if (days == 0 && empty) return "全部时间";
        Calendar last = (Calendar) end.clone();
        last.add(Calendar.DATE, -1);
        boolean crossesYear = last.get(Calendar.YEAR) != start.get(Calendar.YEAR);
        SimpleDateFormat date = new SimpleDateFormat(crossesYear ? "yyyy年M月d日" : "M月d日", Locale.CHINA);
        String first = date.format(start.getTime());
        String until = date.format(last.getTime());
        if (days == 1) return "今天 · " + until;
        return first.equals(until) ? first : first + " 至 " + until;
    }

    private LinearLayout durationSummary(String range, long total, int sessions, int activeDays) {
        LinearLayout card = statsPanel(0xffe7edda, 0xffcdd8bb);
        LinearLayout top = row(ctx());
        top.addView(text(ctx(), "累计专注", 13, DEEP), new LinearLayout.LayoutParams(0, -2, 1));
        ImageView icon = new ImageView(ctx());
        icon.setImageResource(R.drawable.ic_study_sprout);
        icon.setImageTintList(ColorStateList.valueOf(DEEP));
        icon.setPadding(dp(ctx(), 7), dp(ctx(), 7), dp(ctx(), 7), dp(ctx(), 7));
        icon.setBackground(rounded(ctx(), 0xfff8faed, 0, 12));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(icon, new LinearLayout.LayoutParams(dp(ctx(), 36), dp(ctx(), 36)));
        card.addView(top);

        String formatted = StudyFormat.duration(total);
        SpannableString value = new SpannableString(formatted);
        Matcher units = DURATION_UNITS.matcher(formatted);
        while (units.find()) value.setSpan(new RelativeSizeSpan(.55f), units.start(), units.end(), 0);
        TextView number = heading(ctx(), "", 36);
        number.setText(value);
        number.setFontFeatureSettings("tnum");
        ForestUi.add(card, number, 1);
        ForestUi.add(card, text(ctx(), range, 12, MUTED), 5);
        View line = new View(ctx());
        line.setBackgroundColor(0xffcdd8bb);
        LinearLayout.LayoutParams divider = new LinearLayout.LayoutParams(-1, dp(ctx(), 1));
        divider.topMargin = dp(ctx(), 13);
        card.addView(line, divider);
        LinearLayout details = row(ctx());
        details.setGravity(Gravity.TOP);
        stat(details, "学习次数", sessions + " 次", 1);
        stat(details, "学习天数", activeDays + " 天", 1);
        ForestUi.add(card, details, 12);
        return card;
    }

    private void setStatsScope(@Nullable String plan, int period) {
        statsPlan = plan;
        days = period;
        render();
    }

    private void filterPlans() {
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("计划范围", null, R.drawable.ic_study_book);
        StudyNotebook notebook = store.notebook();
        StudyPlan current = notebook.currentPlan();
        if (current != null) {
            addPlanFilter(sheet, pane, "当前计划", current.title + (current.archived != 0 ? " · 已归档" : ""),
                null, days, statsPlan == null || current.id.equals(statsPlan));
        }
        addPlanFilter(sheet, pane, "所有计划", null, "", 0, "".equals(statsPlan) || current == null);
        for (StudyPlan plan : notebook.plans()) {
            if (plan == current) continue;
            addPlanFilter(sheet, pane, plan.title, plan.archived == 0 ? null : "已归档",
                plan.id, 0, plan.id.equals(statsPlan));
        }
        sheet.push(pane);
    }

    private void addPlanFilter(StudySheet sheet, StudySheet.Pane pane, String name, String detail,
                               @Nullable String plan, int period, boolean selected) {
        ForestUi.add(pane.body, StudySheet.item(ctx(), name, detail, 0, selected ? StudySheet.MOSS : Color.TRANSPARENT,
            selected ? "✓" : null, selected, () -> {
                sheet.dismiss();
                setStatsScope(plan, period);
            }), 4);
    }

    private void stat(LinearLayout summary, String title, String value, float weight) {
        LinearLayout column = column(ctx());
        column.addView(text(ctx(), title, 10, MUTED));
        TextView number = heading(ctx(), value, 18);
        number.setMaxLines(2);
        ForestUi.add(column, number, 8);
        summary.addView(column, new LinearLayout.LayoutParams(0, -2, weight));
    }

    private void subjectSummary(LinearLayout list, List<StudyNotebook.Record> records, long total,
                                Consumer<StudySubject> onSubject) {
        Map<StudySubject, Long> durations = new LinkedHashMap<>();
        for (StudyNotebook.Record record : records) {
            durations.merge(record.subject, record.durationMillis, Long::sum);
        }
        List<StudySubject> subjects = new ArrayList<>(durations.keySet());
        subjects.sort((a, b) -> Long.compare(durations.get(b), durations.get(a)));
        list.addView(summaryRow(null, "科目", "时长", "占比"));
        for (StudySubject subject : subjects) {
            View line = new View(ctx());
            line.setBackgroundColor(LINE);
            list.addView(line, new LinearLayout.LayoutParams(-1, 1));
            long duration = durations.get(subject);
            double share = (100.0 * duration) / total;
            String percent = share < .1 ? "<0.1%" : String.format(Locale.CHINA, "%.1f%%", share);
            String time = StudyFormat.duration(duration);
            LinearLayout row = summaryRow(subject, subject.title, time, percent);
            row.setMinimumHeight(dp(ctx(), 48));
            row.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 12), false));
            row.setContentDescription(subject.title + "，" + time + "，占比 " + percent + "，查看记录");
            row.setFocusable(true);
            row.setOnClickListener(v -> onSubject.accept(subject));
            ForestMotion.bind(row);
            list.addView(row);
        }
    }

    private LinearLayout summaryRow(
        @Nullable StudySubject subject,
        String label,
        String duration,
        String percent
    ) {
        LinearLayout cells = row(ctx());
        int verticalPadding = dp(ctx(), subject == null ? 8 : 12);
        cells.setPadding(0, verticalPadding, 0, verticalPadding);
        addSubjectDot(cells, subject == null ? 0 : subject.color);
        int ink = subject == null ? MUTED : DEEP;
        int size = subject == null ? 11 : 13;
        cells.addView(text(ctx(), label, size, ink), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView time = text(ctx(), duration, size, ink);
        time.setGravity(Gravity.END);
        cells.addView(time, new LinearLayout.LayoutParams(0, -2, .9f));
        TextView share = text(ctx(), percent, size, ink);
        share.setGravity(Gravity.END);
        cells.addView(share, new LinearLayout.LayoutParams(0, -2, .53f));
        TextView arrow = text(ctx(), subject == null ? "" : "›", 18, MUTED);
        arrow.setGravity(Gravity.END);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cells.addView(arrow, new LinearLayout.LayoutParams(dp(ctx(), 15), -2));
        return cells;
    }

    private static View addSubjectDot(LinearLayout row, int color) {
        Context context = row.getContext();
        View dot = new View(context);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dot.setBackground(rounded(context, color, 0, 5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            dp(context, 9),
            dp(context, 9)
        );
        params.setMarginEnd(dp(context, 11));
        row.addView(dot, params);
        return dot;
    }

    private void recordDetail(StudySheet sheet, StudyNotebook.Record record, Runnable changed, boolean editable) {
        sheet.push(recordPane(sheet, record, changed, editable));
    }

    private StudySheet.Pane recordPane(StudySheet sheet, StudyNotebook.Record record, Runnable changed,
                                       boolean editable) {
        StudySheet.Pane pane = sheetPane(record.subject.title, store.notebook().plan(record.planId).title,
            R.drawable.ic_study_book);
        LinearLayout summary = column(ctx());
        summary.setPadding(dp(ctx(), 16), dp(ctx(), 14), dp(ctx(), 16), dp(ctx(), 14));
        summary.setBackground(rounded(ctx(), StudySheet.MOSS, 0, 18));
        summary.addView(heading(ctx(), StudyFormat.duration(record.durationMillis), 28));
        ForestUi.add(summary, note(new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)
            .format(new java.util.Date(record.completedAt)) + recordOrigin(record)), 8);
        pane.body.addView(summary);
        StudyWork.Task task = store.notebook().task(record.taskId);
        if (task != null) ForestUi.add(pane.body, note("学习事项 · " + task.title), 12);
        if (!record.note.isEmpty()) {
            ForestUi.add(pane.body, StudySheet.section(ctx(), "学习手记"), 18);
            TextView entry = text(ctx(), record.note, 14, DEEP);
            entry.setLineSpacing(dp(ctx(), 5), 1);
            ForestUi.add(pane.body, entry, 7);
        }
        if (editable) {
            StudyWorkDialogs work = workDialogs(sheet);
            Runnable refresh = () -> {
                sheet.pop();
                sheet.replace(recordPane(sheet, store.notebook().record(record.id), changed, true));
                changed.run();
                render();
            };
            pane.secondary("编辑记录", () -> work.recordForm(store.notebook().plan(record.planId), record, refresh));
            if (!record.note.isEmpty()) {
                StudyWork.Review review = store.notebook().review(record.id);
                String label = review == null ? "加入待回顾" : review.completed == 0 ? "查看待回顾" : "再次安排回顾";
                if (review != null && review.completed == 0)
                    ForestUi.add(pane.body, note("回顾日期 · " + review.day), 12);
                pane.primary(label, () -> {
                    if (review != null && review.completed == 0) openReview(work, sheet, review, record, refresh);
                    else work.reviewForm(record, review, refresh);
                });
            }
        }
        return pane;
    }

    private static String recordOrigin(StudyNotebook.Record record) {
        if (record.manual) return " · 手动补记";
        return record.adjusted ? " · 时长已修正" : "";
    }

    private void showRecords(@Nullable String planId, Supplier<List<StudyNotebook.Record>> source, String range,
                             @Nullable StudySubject initialSubject) {
        List<StudyNotebook.Record> all = source.get();
        if (all.isEmpty()) {
            toast("还没有学习记录，从第一段专注开始吧");
            return;
        }
        StudySheet sheet = newSheet();
        String scope = planId == null ? "所有计划" : store.notebook().plan(planId).title;
        StudySheet.Pane pane = sheetPane(initialSubject == null ? "全部学习记录" : "学习记录",
            scope + " · " + range, R.drawable.ic_study_trail);
        List<StudyNotebook.Record> initialRecords = recordsForSubject(all, initialSubject);
        LinearLayout toolbar = row(ctx());
        MaterialButton filter = button(ctx(), (initialSubject == null ? "全部科目" : initialSubject.title) + " ▾", false);
        filter.setTextSize(12);
        toolbar.addView(filter, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = text(ctx(), initialRecords.size() + " 次", 11, MUTED);
        count.setGravity(Gravity.END);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, -2);
        countParams.setMarginStart(dp(ctx(), 12));
        toolbar.addView(count, countParams);
        pane.above.addView(toolbar);
        EditText search = new EditText(ctx());
        search.setSingleLine(true);
        search.setHint("搜索学习手记");
        search.setContentDescription("搜索学习手记");
        StudySheet.styleField(search);
        ForestUi.add(pane.above, search, 8);
        TextView empty = note("没有符合条件的学习记录");
        empty.setVisibility(View.GONE);
        ForestUi.add(pane.above, empty, 6);

        SimpleDateFormat date = new SimpleDateFormat("yyyy/MM/dd · HH:mm", Locale.CHINA);
        ArrayAdapter<StudyNotebook.Record> adapter = new ArrayAdapter<StudyNotebook.Record>(
            ctx(),
            0,
            initialRecords
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View recycled, @NonNull ViewGroup parent) {
                HistoryRow cell =
                    recycled == null ? new HistoryRow(parent.getContext()) : (HistoryRow) recycled;
                StudyNotebook.Record record = getItem(position);
                String timestamp = date.format(new java.util.Date(record.completedAt));
                if (planId == null) timestamp += " · " + store.notebook().plan(record.planId).title;
                cell.bind(record, timestamp + recordOrigin(record));
                return cell;
            }
        };
        ListView entries = new ListView(ctx());
        entries.setDivider(new ColorDrawable(LINE));
        entries.setDividerHeight(1);
        entries.setSelector(rounded(ctx(), 0x2291aa78, 0, 10));
        entries.setAdapter(adapter);
        int maxRowsHeight = dp(
            ctx(),
            ctx().getResources().getConfiguration().screenHeightDp * .52f
        );
        pane.listBody(entries, Math.min(maxRowsHeight, dp(ctx(), 68) * Math.min(all.size(), 10)));

        StudySubject[] selection = {initialSubject};
        Runnable updateList = () -> {
            String query = StudyWorkDialogs.value(search).toLowerCase(Locale.ROOT);
            List<StudyNotebook.Record> filtered = recordsForSubject(source.get(), selection[0]);
            if (!query.isEmpty()) filtered.removeIf(record -> !record.note.toLowerCase(Locale.ROOT).contains(query));
            adapter.setNotifyOnChange(false);
            adapter.clear();
            adapter.addAll(filtered);
            adapter.notifyDataSetChanged();
            count.setText(adapter.getCount() + " 次");
            empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                updateList.run();
                entries.setSelection(0);
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        entries.setOnItemClickListener((parent, view, position, id) ->
            recordDetail(sheet, adapter.getItem(position), updateList, true));
        filter.setOnClickListener(anchor -> {
            Map<StudySubject, String> recorded = new LinkedHashMap<>();
            for (StudyNotebook.Record record : source.get())
                recorded.putIfAbsent(record.subject, store.notebook().plan(record.planId).title);
            List<StudySubject> subjectOptions = new ArrayList<>();
            subjectOptions.add(null);
            subjectOptions.addAll(recorded.keySet());
            StudySheet.Pane options = sheetPane("按科目筛选", null, R.drawable.ic_study_book);
            for (StudySubject subject : subjectOptions) {
                String label = subject == null ? "全部科目" : subject.title;
                boolean selected = java.util.Objects.equals(selection[0], subject);
                String context = subject == null || planId != null ? null : recorded.get(subject);
                ForestUi.add(options.body, StudySheet.item(ctx(), label, context, 0,
                    selected ? StudySheet.MOSS : Color.TRANSPARENT, selected ? "✓" : null, selected, () -> {
                        selection[0] = subject;
                        updateList.run();
                        filter.setText(label + " ▾");
                        sheet.pop();
                        entries.setSelection(0);
                    }), 4);
            }
            sheet.push(options);
        });
        sheet.push(pane);
    }

    private static List<StudyNotebook.Record> recordsForSubject(List<StudyNotebook.Record> source,
                                                                @Nullable StudySubject subject) {
        List<StudyNotebook.Record> result = new ArrayList<>();
        for (StudyNotebook.Record record : source) {
            if (subject == null || record.subject.equals(subject)) result.add(record);
        }
        return result;
    }

    private static final class HistoryRow extends LinearLayout {

        private final View dot;
        private final TextView subject, date, duration;

        HistoryRow(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(context, 59));
            setPadding(dp(context, 2), dp(context, 9), dp(context, 2), dp(context, 9));
            dot = addSubjectDot(this, 0);
            LinearLayout copy = column(context);
            subject = text(context, "", 13, DEEP);
            date = text(context, "", 11, MUTED);
            copy.addView(subject);
            ForestUi.add(copy, date, 5);
            addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            duration = text(context, "", 12, DEEP);
            duration.setGravity(Gravity.END);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMarginStart(dp(context, 8));
            addView(duration, params);
        }

        void bind(StudyNotebook.Record record, String timestamp) {
            dot.setBackground(rounded(getContext(), record.subject.color, 0, 5));
            subject.setText(record.subject.title);
            date.setText(timestamp);
            duration.setText(StudyFormat.duration(record.durationMillis));
        }
    }

    private void garden() {
        StudyNotebook n = store.notebook();
        LinearLayout content = content();
        body.addView(content);
        ForestUi.add(content, heading(ctx(), "我的小林地", 27), 15);
        ForestUi.add(content, note("你在认真生活，森林就在悄悄长大。"), 9);
        wallet = text(ctx(), "水滴 " + n.water() + "     种子 " + n.seeds(), 13, WOOD);
        wallet.setId(R.id.study_wallet);
        ForestUi.add(content, wallet, 12);
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
        boolean canPlantNow = !n.planted() && n.seeds() > 0;
        String actionLabel = n.planted()
            ? "给小树浇水 · 1水滴"
            : (canPlantNow ? "种下一颗种子 · 1种子" : "去专注攒种子（满25分钟 +1）");
        waterButton = button(ctx(), actionLabel, n.planted() || canPlantNow);
        waterButton.setId(R.id.study_water_button);
        waterButton.setOnClickListener(v -> {
            if (!n.planted() && n.seeds() <= 0) {
                open(AppDestination.STUDY_FOCUS);
            } else {
                perform(this::care);
            }
        });
        ForestUi.add(content, waterButton, 14);
        String gentleCopy;
        if (n.planted()) {
            gentleCopy = "休息不会枯萎，小树会一直等你。";
        } else if (n.seeds() <= 0) {
            gentleCopy = "累计专注满 25 分钟，即可领到 1 颗新种子。";
        } else if (n.trees() == 0) {
            gentleCopy = "第一颗种子，送给愿意开始的你。";
        } else {
            gentleCopy = "种下新种子，继续扩大你的小森林。";
        }
        TextView gentle = note(gentleCopy);
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
        String goal = n.currentPlan() == null ? "达到任一计划的每日学习目标"
            : "每天达到" + n.dailyGoal() + "分钟目标";
        ForestUi.add(
            content,
            note(
                "每累计专注25分钟  →  +1水滴\n跨次累计，短短一段也不会浪费。\n\n" +
                    goal + "  →  +1种子\n每天最多一次，休息和暂停不扣奖励。\n\n浇满100次水  →  收藏小树\n小树长大后，可以再种下新的期待。"
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
            if (mature) {
                StudySheet sheet = newSheet();
                StudySheet.Pane pane = sheetPane("小树长成了", null, R.drawable.ic_study_sprout);
                ForestUi.add(pane.body, heading(ctx(), "第 " + store.notebook().trees() + " 棵小树", 28), 4);
                ForestUi.add(pane.body, note("已收进你的小林地。"), 8);
                pane.primary("把这份成长收好", sheet::dismiss);
                sheet.push(pane);
            }
        };
        if (wasPlanted) garden.water(from, done);
        else garden.plant(done);
    }

    @Override
    public void onResume() {
        super.onResume();
        StudyNotifications.refresh(ctx(), store);
        render();
        Bundle args = requireArguments();
        String action = args.getString("notificationAction");
        args.remove("notificationAction");
        if (StudyNotifications.FINISH_TIMER.equals(action)
            && store.notebook().sessionId().equals(args.getString(StudyNotifications.SESSION))) {
            perform(this::finishDialog);
        } else if (StudyNotifications.OPEN_AGENDA.equals(action)) {
            showAgenda();
        }
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
        state.putString("statsPlan", statsPlan);
    }

    @Override
    public void onDestroyView() {
        if (activeSheet != null) {
            activeSheet.setDismissWithAnimation(false);
            activeSheet.dismiss();
            activeSheet = null;
        }
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

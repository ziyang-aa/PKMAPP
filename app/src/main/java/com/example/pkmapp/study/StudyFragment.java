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
import com.example.pkmapp.forest.ForestCalendarView;
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
    private TextView topBarTitle;
    private LinearLayout body;
    private TextView clock, today, wallet, dialGoalCaption, dialRightCaption, activeStatusText, dailyProgressText;
    private TextView focusStatusLabel, focusGentleNote;
    private LinearLayout focusStatusPill;
    private View focusStatusDot;
    private MaterialButton startPauseButton;
    private android.animation.ObjectAnimator statusPulseAnimator;
    private android.widget.ProgressBar dailyProgressBar;
    private boolean countdownMode, keepScreenOn;
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
    private String todoFilterDay;
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
            if (page == AppDestination.STUDY_FOCUS && !notebook.sessionId().equals(renderedSession)) {
                render();
            } else if (page == AppDestination.STUDY_FOCUS && notebook.running() != renderedRunning) {
                if (focusStatusPill != null && startPauseButton != null && notebook.currentHasSession()) {
                    syncRunningStateInPlace(notebook.running());
                } else {
                    render();
                }
            } else {
                updateTimer();
            }
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
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(dp(requireContext(), 68));
        bar.setPadding(
            dp(requireContext(), 18),
            dp(requireContext(), 10),
            dp(requireContext(), 20),
            0
        );
        ImageButton back = new ImageButton(requireContext());
        back.setId(R.id.page_back_button);
        back.setImageResource(R.drawable.return_home_door);
        back.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        back.setPadding(0, 0, 0, 0);
        back.setContentDescription("返回森林入口");
        back.setOnClickListener(v -> open(AppDestination.HOME));
        ForestMotion.bind(back);
        back.setBackground(
            ForestUi.ripple(requireContext(), rounded(requireContext(), 0, 0, 14), false)
        );
        bar.addView(
            back,
            new LinearLayout.LayoutParams(dp(requireContext(), 56), dp(requireContext(), 56))
        );
        topBarTitle = heading(requireContext(), "", 24);
        topBarTitle.setTypeface(Typeface.create("serif", Typeface.BOLD));
        topBarTitle.setTextColor(DEEP);
        topBarTitle.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        topBarTitle.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMarginStart(dp(requireContext(), 12));
        bar.addView(topBarTitle, titleLp);

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
        scroll.setPadding(0, bars.top, 0, bars.bottom + dp(requireContext(), 112));
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
        StudySheet.Pane pane = sheetPane("恢复学习备份", "恢复将合并学习记录，请选择保留哪份计划设置和林地。",
            R.drawable.ic_study_trail);
        StudySheet.addHairline(pane.body, 0, 14);
        LinearLayout stats = row(ctx());
        stat(stats, "备份中的计划", backup.plans().size() + " 个", 1);
        stat(stats, "备份中的记录", backup.records().size() + " 条", 1);
        pane.body.addView(stats);
        StudySheet.addHairline(pane.body, 14, 0);
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
        if (statusPulseAnimator != null) {
            statusPulseAnimator.cancel();
            statusPulseAnimator = null;
        }
        clock = null;
        today = null;
        dialGoalCaption = null;
        dialRightCaption = null;
        activeStatusText = null;
        focusStatusPill = null;
        focusStatusDot = null;
        focusStatusLabel = null;
        focusGentleNote = null;
        startPauseButton = null;
        dailyProgressText = null;
        dailyProgressBar = null;
        renderedToday = null;
        android.content.SharedPreferences uiPrefs = ctx().getSharedPreferences("forest_study_ui", Context.MODE_PRIVATE);
        countdownMode = uiPrefs.getBoolean("countdown_mode", false);
        keepScreenOn = uiPrefs.getBoolean("keep_screen_on", false);
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

    static int partnerDrawable(StudyPlan.Partner partner) {
        if (partner == StudyPlan.Partner.BEAR) return R.drawable.crayon_partner_bear;
        if (partner == StudyPlan.Partner.BUNNY) return R.drawable.crayon_partner_bunny;
        return R.drawable.crayon_study_companion;
    }

    private void setBarTitle(String title) {
        if (topBarTitle != null) {
            topBarTitle.setText(title);
        }
    }

    private void focus() {
        StudyNotebook n = store.notebook();
        StudyPlan plan = n.currentPlan();
        StudyPlan.Partner partner = plan == null ? StudyPlan.Partner.SQUIRREL : plan.partner;
        boolean active = n.currentHasSession();

        // Top bar right side: title only, aligned parallel to the 56dp return door
        setBarTitle("森林自习室");

        LinearLayout content = content();
        body.addView(content);

        // Editorial Hairline hero strip below top bar: date/partner + borderless serif plan dropdown on left, companion on right
        LinearLayout heroRow = row(ctx());
        heroRow.setGravity(Gravity.BOTTOM);
        heroRow.setPadding(0, dp(ctx(), 2), 0, dp(ctx(), 10));
        heroRow.setBackground(bottomHairlineBg(0x1f5c4938));
        LinearLayout heroCopy = column(ctx());
        TextView datePartner = text(
            ctx(),
            new SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(new java.util.Date())
                + " · 伙伴·" + partner.title,
            13,
            0xff6b563f
        );
        datePartner.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        datePartner.setSingleLine(true);
        datePartner.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroCopy.addView(datePartner, new LinearLayout.LayoutParams(-1, -2));
        if (plan != null) {
            LinearLayout planWrap = row(ctx());
            planWrap.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            planWrap.setMinimumHeight(dp(ctx(), 40));
            planWrap.setPadding(0, dp(ctx(), 4), dp(ctx(), 6), dp(ctx(), 4));
            planWrap.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false));
            planWrap.setOnClickListener(v -> planDialogs().manager());
            ForestMotion.bind(planWrap);

            View planDot = new View(ctx());
            planDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            planDot.setBackground(rounded(ctx(), GREEN, 0, 99));
            LinearLayout.LayoutParams planDotLp = new LinearLayout.LayoutParams(dp(ctx(), 7.5f), dp(ctx(), 7.5f));
            planDotLp.setMarginEnd(dp(ctx(), 8));
            planWrap.addView(planDot, planDotLp);

            TextView plans = StudySheet.link(
                ctx(),
                "当前计划 · " + plan.title + " ▾",
                0xff243624,
                () -> planDialogs().manager()
            );
            plans.setTextSize(18);
            plans.setTypeface(Typeface.create("serif", Typeface.BOLD));
            plans.setSingleLine(true);
            plans.setEllipsize(android.text.TextUtils.TruncateAt.END);
            plans.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            plans.setPadding(0, 0, 0, 0);
            plans.setBackground(null);
            planWrap.addView(plans, new LinearLayout.LayoutParams(-2, -2));

            LinearLayout.LayoutParams planWrapLp = new LinearLayout.LayoutParams(-1, -2);
            planWrapLp.topMargin = dp(ctx(), 4);
            heroCopy.addView(planWrap, planWrapLp);
        }
        heroRow.addView(heroCopy, new LinearLayout.LayoutParams(0, -2, 1));
        int companion = partnerDrawable(partner);
        LinearLayout.LayoutParams mascotLp = new LinearLayout.LayoutParams(dp(ctx(), 70), dp(ctx(), 66));
        mascotLp.setMarginStart(dp(ctx(), 10));
        heroRow.addView(image(ctx(), companion, null), mascotLp);
        ForestUi.add(content, heroRow, 4);
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
        timer.setElevation(dp(ctx(), 2));
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(-1, -2);
        timerParams.topMargin = dp(ctx(), 8);
        content.addView(timer, timerParams);
        StudySubject currentSubject = active ? n.activeSubject() : n.selected();
        String subjectLabel =
            currentSubject.title + (active ? " · 本次科目" : "  切换 ›");
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
        LinearLayout timerTopRow = row(ctx());
        subjectSelector = button(ctx(), subjectLabel, false);
        subjectSelector.setText(selectorText);
        subjectSelector.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        subjectSelector.setIconPadding(dp(ctx(), 10));
        subjectSelector.setId(R.id.study_subject_selector);
        subjectSelector.setContentDescription("切换学习科目");
        subjectSelector.setIconResource(R.drawable.ic_study_book);
        subjectSelector.setIconTint(ColorStateList.valueOf(DEEP));
        subjectSelector.setIconSize(dp(ctx(), 19));
        subjectSelector.setBackgroundTintList(ColorStateList.valueOf(0));
        subjectSelector.setStrokeWidth(0);
        subjectSelector.setEnabled(!active);
        subjectSelector.setAlpha(active ? .88f : 1f);
        subjectSelector.setOnClickListener(v -> showSubjectSheet());
        timerTopRow.addView(subjectSelector, new LinearLayout.LayoutParams(0, dp(ctx(), 42), 1));
        TextView[] modeToggleRef = new TextView[1];
        TextView modeToggle = StudySheet.link(
            ctx(),
            countdownMode ? "倒计时 ⇄" : "正计时 ⇄",
            countdownMode ? 0xff2c5132 : WOOD,
            () -> {
                countdownMode = !countdownMode;
                ctx().getSharedPreferences("forest_study_ui", Context.MODE_PRIVATE)
                    .edit().putBoolean("countdown_mode", countdownMode).apply();
                TextView btn = modeToggleRef[0];
                if (btn != null) {
                    btn.setText(countdownMode ? "倒计时 ⇄" : "正计时 ⇄");
                    btn.setTextColor(countdownMode ? 0xff2c5132 : WOOD);
                    btn.setBackground(ripple(ctx(), rounded(ctx(),
                        countdownMode ? 0xffe6efe1 : 0xfff2eee3,
                        countdownMode ? 0xffa7c2a0 : 0x225c4938, 999), false));
                }
                if (focusGentleNote != null && !store.notebook().currentHasSession()) {
                    focusGentleNote.setText(countdownMode
                        ? "倒计时模式 · 归零后自动转为加时计数"
                        : "正向计时 · 随时暂停 · 到达目标仍可继续");
                }
                updateTimer();
            }
        );
        modeToggleRef[0] = modeToggle;
        modeToggle.setTextSize(11);
        modeToggle.setMinimumHeight(dp(ctx(), 30));
        modeToggle.setPadding(dp(ctx(), 10), dp(ctx(), 4), dp(ctx(), 10), dp(ctx(), 4));
        modeToggle.setBackground(ripple(ctx(), rounded(ctx(),
            countdownMode ? 0xffe6efe1 : 0xfff2eee3,
            countdownMode ? 0xffa7c2a0 : 0x225c4938, 999), false));
        timerTopRow.addView(modeToggle);
        timer.addView(timerTopRow, new LinearLayout.LayoutParams(-1, -2));
        StudyWork.Task activeTask = active ? n.task(n.activeTaskId()) : null;
        if (activeTask != null) ForestUi.add(timer, note("本次目标 · " + activeTask.title), 0);
        LinearLayout clockBlock = column(ctx());
        clockBlock.setGravity(Gravity.CENTER_HORIZONTAL);
        clockBlock.setPadding(dp(ctx(), 4), dp(ctx(), 20), dp(ctx(), 4), dp(ctx(), 14));

        // 1. Live Status Pill with breathing dot
        boolean isRunning = active && n.running();
        boolean isPaused = active && !n.running();
        focusStatusPill = row(ctx());
        focusStatusPill.setGravity(Gravity.CENTER_VERTICAL);
        focusStatusPill.setPadding(dp(ctx(), 12), dp(ctx(), 5), dp(ctx(), 12), dp(ctx(), 5));
        focusStatusPill.setBackground(rounded(ctx(),
            isRunning ? 0xffeef3e8 : isPaused ? 0xfff5efe3 : 0xfff2efe6, 0, 999));

        focusStatusDot = new View(ctx());
        focusStatusDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        focusStatusDot.setBackground(rounded(ctx(),
            isRunning ? 0xff456444 : isPaused ? 0xffb8894f : 0xffb5afa0, 0, 99));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx(), 7), dp(ctx(), 7));
        dotLp.setMarginEnd(dp(ctx(), 6));
        focusStatusPill.addView(focusStatusDot, dotLp);

        if (isRunning && (Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())) {
            statusPulseAnimator = android.animation.ObjectAnimator.ofFloat(focusStatusDot, View.ALPHA, 1f, 0.35f);
            statusPulseAnimator.setDuration(1100);
            statusPulseAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            statusPulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            statusPulseAnimator.start();
        }

        focusStatusLabel = text(
            ctx(),
            isRunning ? "正在专注" : isPaused ? "休息一下" : "准备好就开始",
            12,
            isRunning ? 0xff315e3a : isPaused ? 0xff7b5c3b : MUTED
        );
        focusStatusLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        focusStatusPill.addView(focusStatusLabel);
        clockBlock.addView(focusStatusPill, new LinearLayout.LayoutParams(-2, -2));

        // 2. Oversized Editorial Serif Clock
        clock = text(ctx(), StudyFormat.clock(active ? store.elapsedMillis() : 0), 48, 0xff243a28);
        clock.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        clock.setFontFeatureSettings("tnum");
        clock.setLetterSpacing(0.02f);
        clock.setMaxLines(1);
        clock.setId(R.id.study_timer);
        clock.setGravity(Gravity.CENTER);
        clock.setContentDescription("本次学习计时");
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            clock, 30, 50, 2, android.util.TypedValue.COMPLEX_UNIT_SP
        );
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(-1, dp(ctx(), 62));
        clockLp.topMargin = dp(ctx(), 12);
        clockBlock.addView(clock, clockLp);

        // 3. Precision Horizontal Gradient Progress Rail
        dial = new StudyClockFace();
        View progressRail = new View(ctx());
        progressRail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        progressRail.setBackground(dial);
        LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(-1, dp(ctx(), 7));
        railLp.setMargins(dp(ctx(), 6), dp(ctx(), 20), dp(ctx(), 6), 0);
        clockBlock.addView(progressRail, railLp);

        // 4. Two-Sided Progress Metadata Row
        int focusGoal = active ? n.activeGoal() : n.sessionGoal();
        LinearLayout progressMeta = row(ctx());
        dialGoalCaption = text(ctx(), (countdownMode ? "倒计时目标 " : "目标 ") + focusGoal + " 分钟", 12, MUTED);
        dialGoalCaption.setFontFeatureSettings("tnum");
        progressMeta.addView(dialGoalCaption, new LinearLayout.LayoutParams(0, -2, 1));

        dialRightCaption = text(ctx(), "0%", 12, 0xff315e3a);
        dialRightCaption.setFontFeatureSettings("tnum");
        dialRightCaption.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        progressMeta.addView(dialRightCaption);

        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
        metaLp.setMargins(dp(ctx(), 6), dp(ctx(), 8), dp(ctx(), 6), 0);
        clockBlock.addView(progressMeta, metaLp);

        timer.addView(clockBlock, new LinearLayout.LayoutParams(-1, -2));
        if (!active) {
            LinearLayout goals = row(ctx());
            goals.setBackground(rounded(ctx(), 0xffeae5d9, 0, 13));
            goals.setPadding(dp(ctx(), 4), dp(ctx(), 4), dp(ctx(), 4), dp(ctx(), 4));
            int[] presetGoals = new int[] { 25, 45, 60 };
            boolean isCustomGoal = focusGoal != 25 && focusGoal != 45 && focusGoal != 60;
            for (int minutes : presetGoals) {
                boolean selectedGoal = focusGoal == minutes;
                TextView chip = text(ctx(), minutes + "分钟", 12, selectedGoal ? 0xff243624 : 0xff6b7568);
                chip.setGravity(Gravity.CENTER);
                chip.setTypeface(Typeface.create(selectedGoal ? "sans-serif-medium" : "sans-serif",
                    selectedGoal ? Typeface.BOLD : Typeface.NORMAL));
                chip.setBackground(ripple(ctx(), rounded(ctx(),
                    selectedGoal ? 0xfffffefa : Color.TRANSPARENT,
                    selectedGoal ? 0x185c4938 : 0, 10), false));
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
            customChip.setBackground(ripple(ctx(), rounded(ctx(),
                isCustomGoal ? 0xfffffefa : Color.TRANSPARENT,
                isCustomGoal ? 0x185c4938 : 0, 10), false));
            customChip.setOnClickListener(v -> customSessionGoalDialog());
            addEqual(goals, customChip, 36);
            ForestUi.add(timer, goals, 10);
        } else {
            StudySheet.addHairline(timer, 6, 8);
            LinearLayout activeBar = row(ctx());
            activeBar.setPadding(dp(ctx(), 4), dp(ctx(), 2), dp(ctx(), 2), dp(ctx(), 2));
            activeStatusText = text(
                ctx(),
                n.running() ? "静心计时中 · 随时可暂停" : "已暂停 · 暂停时长不计入",
                12,
                MUTED
            );
            activeBar.addView(activeStatusText, new LinearLayout.LayoutParams(0, -2, 1));
            TextView[] wakeBtnRef = new TextView[1];
            TextView wakeBtn = StudySheet.link(
                ctx(),
                keepScreenOn ? "常亮已开" : "屏幕常亮",
                keepScreenOn ? 0xff2a4e2f : MUTED,
                () -> {
                    keepScreenOn = !keepScreenOn;
                    ctx().getSharedPreferences("forest_study_ui", Context.MODE_PRIVATE)
                        .edit().putBoolean("keep_screen_on", keepScreenOn).apply();
                    syncKeepScreenOn();
                    TextView wb = wakeBtnRef[0];
                    if (wb != null) {
                        wb.setText(keepScreenOn ? "常亮已开" : "屏幕常亮");
                        wb.setTextColor(keepScreenOn ? 0xff2a4e2f : MUTED);
                        wb.setBackground(ripple(ctx(), rounded(ctx(),
                            keepScreenOn ? 0xffeef4ea : 0xfff2eee3, 0, 999), false));
                    }
                }
            );
            wakeBtnRef[0] = wakeBtn;
            wakeBtn.setTextSize(11);
            wakeBtn.setMinimumHeight(dp(ctx(), 28));
            wakeBtn.setPadding(dp(ctx(), 11), dp(ctx(), 4), dp(ctx(), 11), dp(ctx(), 4));
            wakeBtn.setBackground(ripple(ctx(), rounded(ctx(),
                keepScreenOn ? 0xffeef4ea : 0xfff2eee3, 0, 999), false));
            activeBar.addView(wakeBtn);
            timer.addView(activeBar, new LinearLayout.LayoutParams(-1, -2));
            syncKeepScreenOn();
        }
        LinearLayout controls = row(ctx());
        startPauseButton = button(
            ctx(),
            active ? (n.running() ? "暂停一下" : "继续学习")
                : n.hasSession() ? "返回原计划保存计时" : "开始专注，种下今天",
            true
        );
        startPauseButton.setId(R.id.study_start_pause_button);
        startPauseButton.setCornerRadius(dp(ctx(), 12));
        startPauseButton.setOnClickListener(v ->
            perform(() -> {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastControlClick < 280) return;
                lastControlClick = now;
                if (store.notebook().hasSession() && !store.notebook().currentHasSession()) {
                    returnToTimer(store.notebook().activePlanId());
                    return;
                }
                if (!store.notebook().hasSession()) {
                    store.start();
                    render();
                } else {
                    if (store.notebook().running()) store.pause();
                    else store.resume();
                    syncRunningStateInPlace(store.notebook().running());
                }
            })
        );
        controls.addView(startPauseButton, new LinearLayout.LayoutParams(0, dp(ctx(), 48), 1));
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
        focusGentleNote = text(
            ctx(),
            active
                ? "完成后可自由切换科目 · 满 25 分钟累计 1 水滴"
                : (countdownMode ? "倒计时模式 · 归零后自动转为加时计数" : "正向计时 · 随时暂停 · 到达目标仍可继续"),
            11,
            MUTED
        );
        focusGentleNote.setGravity(Gravity.CENTER);
        ForestUi.add(timer, focusGentleNote, 8);
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
        StudySheet.Pane pane = sheetPane("自定义专注时长", "单次目标 5 至 360 分钟，满 25 分钟累计水滴。", 0);
        StudySheet.addHairline(pane.body, 0, 12);

        EditText input = new EditText(ctx());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(store.notebook().sessionGoal()));
        input.setSelectAllOnFocus(true);
        input.setContentDescription("自定义专注目标分钟");
        StudySheet.styleUnderlineField(input);
        input.setTextSize(22f);
        input.setTypeface(Typeface.create("serif", Typeface.BOLD));
        input.setFontFeatureSettings("tnum");
        pane.body.addView(StudySheet.section(ctx(), "单次目标时长（分钟）"));
        ForestUi.add(pane.body, input, 4);

        LinearLayout presets = row(ctx());
        int[] presetValues = { 15, 30, 90, 120 };
        List<TextView> presetChips = new ArrayList<>();
        Runnable syncPresets = () -> {
            String cur = input.getText().toString().trim();
            for (int i = 0; i < presetValues.length; i++) {
                StudySheet.styleChoiceChip(presetChips.get(i), cur.equals(String.valueOf(presetValues[i])));
            }
        };
        for (int i = 0; i < presetValues.length; i++) {
            int minutes = presetValues[i];
            TextView option = StudySheet.link(ctx(), minutes + " 分钟", DEEP, () -> {
                input.setText(String.valueOf(minutes));
                input.setSelection(input.getText().length());
            });
            option.setTextSize(12);
            option.setFontFeatureSettings("tnum");
            option.setMinimumHeight(dp(ctx(), 36));
            LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) presetParams.setMarginStart(dp(ctx(), 8));
            presets.addView(option, presetParams);
            presetChips.add(option);
        }
        ForestUi.add(pane.body, presets, 14);

        TextView errorMessage = text(ctx(), "", 12, 0xff8d4836);
        errorMessage.setVisibility(View.GONE);
        ForestUi.add(pane.body, errorMessage, 8);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                errorMessage.setVisibility(View.GONE);
                syncPresets.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        syncPresets.run();

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
        dailyProgressBar = new android.widget.ProgressBar(ctx(), null, android.R.attr.progressBarStyleHorizontal);
        dailyProgressBar.setMax(100);
        dailyProgressBar.setProgressDrawable(roundedProgressDrawable());
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(-1, dp(ctx(), 7));
        pbLp.topMargin = dp(ctx(), 10);
        preview.addView(dailyProgressBar, pbLp);
        dailyProgressText = text(ctx(), "", 11, MUTED);
        ForestUi.add(preview, dailyProgressText, 6);

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

    private android.graphics.drawable.Drawable tintedIcon(int resId, int sizeDp, int tintColor) {
        android.graphics.drawable.Drawable raw = androidx.core.content.ContextCompat.getDrawable(ctx(), resId);
        if (raw == null) return null;
        android.graphics.drawable.Drawable icon = androidx.core.graphics.drawable.DrawableCompat.wrap(raw.mutate());
        androidx.core.graphics.drawable.DrawableCompat.setTint(icon, tintColor);
        int px = dp(ctx(), sizeDp);
        icon.setBounds(0, 0, px, px);
        return icon;
    }

    private android.graphics.drawable.Drawable bottomHairlineBg(int color) {
        android.graphics.drawable.GradientDrawable line = new android.graphics.drawable.GradientDrawable();
        line.setColor(color);
        android.graphics.drawable.LayerDrawable bg = new android.graphics.drawable.LayerDrawable(
            new android.graphics.drawable.Drawable[] { line }
        );
        bg.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        bg.setLayerHeight(0, Math.max(1, dp(ctx(), 0.75f)));
        return bg;
    }

    private void todoPage() {
        LinearLayout content = content();
        body.addView(content);
        TodoStore todos = TodoStore.get(ctx());

        // 1. Top bar right side: title only, aligned parallel to the 56dp return door
        setBarTitle("待办");

        // Editorial Hairline Toolbar (Proposal 1): crisp date + larger borderless icon links + airy porcelain outline CTA
        LinearLayout toolbar = row(ctx());
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, dp(ctx(), 2), 0, dp(ctx(), 10));
        toolbar.setBackground(bottomHairlineBg(0x1f5c4938));

        LinearLayout leftMeta = column(ctx());
        TextView todayLabel = text(
            ctx(),
            new SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(new java.util.Date()),
            14,
            0xff5c4938
        );
        todayLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        leftMeta.addView(todayLabel);

        LinearLayout subLinks = row(ctx());
        subLinks.setGravity(Gravity.CENTER_VERTICAL);

        boolean remindOn = todos.remindEnabled();
        int remindColor = remindOn ? DEEP : MUTED;
        LinearLayout remindRow = row(ctx());
        remindRow.setGravity(Gravity.CENTER_VERTICAL);
        remindRow.setMinimumHeight(dp(ctx(), 40));
        remindRow.setPadding(0, dp(ctx(), 4), dp(ctx(), 4), dp(ctx(), 4));
        remindRow.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 8), false));
        remindRow.setOnClickListener(v -> todoReminderSettingsDialog());
        ForestMotion.bind(remindRow);

        TextView remindText = text(
            ctx(),
            remindOn ? ("提醒 " + todos.defaultRemindTime()) : "提醒已关",
            13,
            remindColor
        );
        remindText.setTextSize(13.5f);
        remindText.setSingleLine(true);
        remindText.setTypeface(Typeface.create(remindOn ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        remindText.setFontFeatureSettings("tnum");
        remindText.setCompoundDrawablesRelative(tintedIcon(R.drawable.ic_editorial_bell, 15, remindColor), null, null, null);
        remindText.setCompoundDrawablePadding(dp(ctx(), 4));
        remindRow.addView(remindText);
        if (remindOn) {
            View dot = new View(ctx());
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            dot.setBackground(rounded(ctx(), GREEN, 0, 99));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx(), 6), dp(ctx(), 6));
            dotLp.setMarginStart(dp(ctx(), 5));
            remindRow.addView(dot, dotLp);
        }
        subLinks.addView(remindRow);

        View sep = new View(ctx());
        sep.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        sep.setBackgroundColor(0x285c4938);
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(Math.max(1, dp(ctx(), 1)), dp(ctx(), 13));
        sepLp.setMarginStart(dp(ctx(), 8));
        sepLp.setMarginEnd(dp(ctx(), 8));
        subLinks.addView(sep, sepLp);

        TextView dateFilterPill = StudySheet.link(ctx(), "按日期筛选 ▾", 0xff6b5c49, () -> {});
        dateFilterPill.setTextSize(13.5f);
        dateFilterPill.setSingleLine(true);
        dateFilterPill.setFontFeatureSettings("tnum");
        dateFilterPill.setGravity(Gravity.CENTER_VERTICAL);
        dateFilterPill.setMinimumHeight(dp(ctx(), 40));
        dateFilterPill.setCompoundDrawablePadding(dp(ctx(), 4));
        subLinks.addView(dateFilterPill);
        ForestUi.add(leftMeta, subLinks, 4);
        toolbar.addView(leftMeta, new LinearLayout.LayoutParams(0, -2, 1));

        TextView addTodoBtn = StudySheet.link(ctx(), "新建", DEEP, () -> todoEditorSheet(null));
        addTodoBtn.setContentDescription("新建待办");
        addTodoBtn.setTextSize(14);
        addTodoBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        addTodoBtn.setGravity(Gravity.CENTER);
        addTodoBtn.setMinimumHeight(dp(ctx(), 40));
        addTodoBtn.setPadding(dp(ctx(), 14), dp(ctx(), 7), dp(ctx(), 15), dp(ctx(), 7));
        addTodoBtn.setCompoundDrawablesRelative(tintedIcon(R.drawable.ic_editorial_plus, 14, DEEP), null, null, null);
        addTodoBtn.setCompoundDrawablePadding(dp(ctx(), 5));
        addTodoBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xfffffefa, 0x4c315e3a, 999), false));
        addTodoBtn.setElevation(dp(ctx(), 1));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-2, -2);
        addLp.setMarginStart(dp(ctx(), 8));
        toolbar.addView(addTodoBtn, addLp);
        ForestUi.add(content, toolbar, 4);

        // 2. Motion-Primitives 3-Tab Mutually Exclusive Filter Bar + Optional Single-Day Bar + Notebook Pad
        if (todoFilter < 0 || todoFilter > 2) todoFilter = 0;
        ForestNotebookPad notebookPad = new ForestNotebookPad(ctx());
        notebookPad.setMinimumHeight(dp(ctx(), 350));

        // Sleek borderless single-day strip (visible ONLY while filtering by a specific date)
        LinearLayout dateFilterStrip = row(ctx());
        dateFilterStrip.setPadding(dp(ctx(), 2), dp(ctx(), 2), dp(ctx(), 2), dp(ctx(), 2));
        TextView prevDayBtn = StudySheet.link(ctx(), "‹", 0xff4c594a, () -> {});
        prevDayBtn.setTextSize(18);
        prevDayBtn.setContentDescription("前一天");
        prevDayBtn.setMinimumHeight(dp(ctx(), 32));
        prevDayBtn.setPadding(dp(ctx(), 8), dp(ctx(), 2), dp(ctx(), 8), dp(ctx(), 2));
        prevDayBtn.setBackground(ripple(ctx(), rounded(ctx(), 0x125c4938, 0, 999), false));
        dateFilterStrip.addView(prevDayBtn, new LinearLayout.LayoutParams(dp(ctx(), 32), dp(ctx(), 32)));

        TextView filterDayLabel = StudySheet.link(ctx(), "", 0xff243624, () -> {});
        filterDayLabel.setTextSize(13);
        filterDayLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        filterDayLabel.setFontFeatureSettings("tnum");
        filterDayLabel.setMinimumHeight(dp(ctx(), 32));
        filterDayLabel.setPadding(dp(ctx(), 10), dp(ctx(), 4), dp(ctx(), 10), dp(ctx(), 4));
        dateFilterStrip.addView(filterDayLabel, new LinearLayout.LayoutParams(-2, -2));

        TextView nextDayBtn = StudySheet.link(ctx(), "›", 0xff4c594a, () -> {});
        nextDayBtn.setTextSize(18);
        nextDayBtn.setContentDescription("后一天");
        nextDayBtn.setMinimumHeight(dp(ctx(), 32));
        nextDayBtn.setPadding(dp(ctx(), 8), dp(ctx(), 2), dp(ctx(), 8), dp(ctx(), 2));
        nextDayBtn.setBackground(ripple(ctx(), rounded(ctx(), 0x125c4938, 0, 999), false));
        dateFilterStrip.addView(nextDayBtn, new LinearLayout.LayoutParams(dp(ctx(), 32), dp(ctx(), 32)));

        dateFilterStrip.addView(new View(ctx()), new LinearLayout.LayoutParams(0, 1, 1));

        TextView clearFilterBtn = StudySheet.link(ctx(), "退出筛选", 0xff7b5c3b, () -> {});
        clearFilterBtn.setTextSize(12);
        clearFilterBtn.setMinimumHeight(dp(ctx(), 30));
        clearFilterBtn.setPadding(dp(ctx(), 10), dp(ctx(), 4), dp(ctx(), 10), dp(ctx(), 4));
        clearFilterBtn.setBackground(ripple(ctx(), rounded(ctx(), 0x125c4938, 0, 999), false));
        dateFilterStrip.addView(clearFilterBtn);

        ForestSegmentedControl[] filterBarRef = new ForestSegmentedControl[1];
        Runnable[] refreshTodoView = new Runnable[1];
        refreshTodoView[0] = () -> {
            boolean filtering = todoFilterDay != null && !todoFilterDay.isEmpty();
            if (filtering) {
                dateFilterPill.setText("已选 " + shortMonthDay(todoFilterDay) + " ▾");
                dateFilterPill.setTextColor(DEEP);
                dateFilterPill.setCompoundDrawablesRelative(
                    tintedIcon(R.drawable.ic_editorial_calendar, 15, DEEP), null, null, null);
                dateFilterPill.setPadding(dp(ctx(), 10), dp(ctx(), 4), dp(ctx(), 10), dp(ctx(), 4));
                dateFilterPill.setBackground(ripple(ctx(), rounded(ctx(), 0xffe5eee0, 0x55315e3a, 999), false));
                dateFilterStrip.setVisibility(View.VISIBLE);
                String todayKey = TodoStore.dayKey(System.currentTimeMillis());
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, 1);
                String tomorrowKey = TodoStore.dayKey(cal.getTimeInMillis());
                Calendar yCal = Calendar.getInstance();
                yCal.add(Calendar.DATE, -1);
                String yesterdayKey = TodoStore.dayKey(yCal.getTimeInMillis());
                filterDayLabel.setText(formatTodoDayHeader(todoFilterDay, todayKey, tomorrowKey, yesterdayKey, true));
            } else {
                dateFilterPill.setText("按日期筛选 ▾");
                dateFilterPill.setTextColor(0xff6b5c49);
                dateFilterPill.setCompoundDrawablesRelative(
                    tintedIcon(R.drawable.ic_editorial_calendar, 15, 0xff6b5c49), null, null, null);
                dateFilterPill.setPadding(dp(ctx(), 2), dp(ctx(), 4), dp(ctx(), 4), dp(ctx(), 4));
                dateFilterPill.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 8), false));
                dateFilterStrip.setVisibility(View.GONE);
            }
            populateStandaloneTodos(notebookPad, filterBarRef[0], todos);
        };

        dateFilterPill.setOnClickListener(v -> todoDateFilterSheet(todos, refreshTodoView[0]));
        filterDayLabel.setOnClickListener(v -> todoDateFilterSheet(todos, refreshTodoView[0]));
        prevDayBtn.setOnClickListener(v -> {
            shiftTodoFilterDay(-1);
            refreshTodoView[0].run();
        });
        nextDayBtn.setOnClickListener(v -> {
            shiftTodoFilterDay(1);
            refreshTodoView[0].run();
        });
        clearFilterBtn.setOnClickListener(v -> {
            todoFilterDay = null;
            refreshTodoView[0].run();
        });

        ForestSegmentedControl filterBar = new ForestSegmentedControl(
            ctx(),
            new String[] {"今天", "计划", "已完成"},
            null,
            todoFilter,
            idx -> {
                todoFilterDay = null;
                todoFilter = idx;
                refreshTodoView[0].run();
            }
        );
        filterBarRef[0] = filterBar;
        ForestUi.add(content, filterBar, 12);
        ForestUi.add(content, dateFilterStrip, 8);
        ForestUi.add(content, notebookPad, 10);
        refreshTodoView[0].run();
    }

    private void shiftTodoFilterDay(int deltaDays) {
        String base = (todoFilterDay == null || todoFilterDay.isEmpty())
            ? TodoStore.dayKey(System.currentTimeMillis()) : todoFilterDay;
        Calendar c = TodoStore.parseDay(base);
        c.add(Calendar.DATE, deltaDays);
        todoFilterDay = TodoStore.dayKey(c.getTimeInMillis());
    }

    private void todoDateFilterSheet(TodoStore todos, Runnable onFilterChanged) {
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("按日期筛选", null, 0);
        String todayKey = TodoStore.dayKey(System.currentTimeMillis());

        List<TodoStore.TodoItem> pendingAll = todos.pending();
        List<TodoStore.TodoItem> doneAll = todos.completed();
        Map<String, Integer> markers = new LinkedHashMap<>();
        Map<String, Integer> pendingCounts = new LinkedHashMap<>();
        Map<String, Integer> doneCounts = new LinkedHashMap<>();
        for (TodoStore.TodoItem item : pendingAll) {
            if (item.day.isEmpty()) continue;
            int flag = item.day.compareTo(todayKey) < 0
                ? ForestCalendarView.MARKER_OVERDUE : ForestCalendarView.MARKER_PENDING;
            markers.merge(item.day, flag, (a, b) -> a | b);
            pendingCounts.merge(item.day, 1, Integer::sum);
        }
        for (TodoStore.TodoItem item : doneAll) {
            String doneDay = item.completed > 0 ? TodoStore.dayKey(item.completed) : item.day;
            if (doneDay.isEmpty()) continue;
            markers.merge(doneDay, ForestCalendarView.MARKER_DONE, (a, b) -> a | b);
            doneCounts.merge(doneDay, 1, Integer::sum);
        }

        StudySheet.addHairline(pane.body, 0, 8);

        TextView previewDate = text(ctx(), "", 13, 0xff243624);
        previewDate.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        previewDate.setFontFeatureSettings("tnum");
        TextView previewStats = text(ctx(), "", 12, MUTED);
        previewStats.setFontFeatureSettings("tnum");

        Runnable[] syncPreview = new Runnable[1];
        String[] activeKey = { (todoFilterDay != null && !todoFilterDay.isEmpty()) ? todoFilterDay : todayKey };
        syncPreview[0] = () -> {
            String key = activeKey[0];
            previewDate.setText(ForestCalendarView.formatChineseFull(key));
            int pCount = pendingCounts.getOrDefault(key, 0);
            int dCount = doneCounts.getOrDefault(key, 0);
            if (pCount == 0 && dCount == 0) {
                previewStats.setText("暂无待办或完成记录");
                previewStats.setTextColor(MUTED);
            } else {
                StringBuilder sb = new StringBuilder();
                if (pCount > 0) sb.append(pCount).append(" 项待办");
                if (dCount > 0) {
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append(dCount).append(" 项已完成");
                }
                previewStats.setText(sb.toString());
                previewStats.setTextColor(pCount > 0 && key.compareTo(todayKey) < 0 ? 0xff9e4c37 : DEEP);
            }
        };

        ForestCalendarView calendar = new ForestCalendarView(ctx(), activeKey[0], markers, dayKey -> {
            activeKey[0] = dayKey;
            syncPreview[0].run();
        });
        pane.body.addView(calendar, new LinearLayout.LayoutParams(-1, -2));

        // Compact botanical legend row directly on sheet
        LinearLayout legend = row(ctx());
        legend.setGravity(Gravity.CENTER_VERTICAL);
        addLegendDot(legend, 0xff456444, "待办", false);
        addLegendDot(legend, 0xffb2553f, "逾期", true);
        addLegendDot(legend, 0xff9bb595, "已完成", true);
        ForestUi.add(pane.body, legend, 6);

        StudySheet.addHairline(pane.body, 10, 12);
        LinearLayout previewRow = row(ctx());
        previewRow.addView(previewDate, new LinearLayout.LayoutParams(0, -2, 1));
        previewRow.addView(previewStats);
        pane.body.addView(previewRow, new LinearLayout.LayoutParams(-1, -2));
        syncPreview[0].run();

        if (todoFilterDay != null && !todoFilterDay.isEmpty()) {
            pane.secondary("查看全部", () -> {
                todoFilterDay = null;
                sheet.dismiss();
                onFilterChanged.run();
            });
        }
        pane.primary("查看这一天", () -> {
            todoFilterDay = calendar.selectedDayKey();
            sheet.dismiss();
            onFilterChanged.run();
        });
        sheet.push(pane);
    }

    private void addLegendDot(LinearLayout row, int color, String label, boolean spaced) {
        View dot = new View(ctx());
        dot.setBackground(rounded(ctx(), color, 0, 99));
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(ctx(), 6), dp(ctx(), 6));
        if (spaced) dlp.setMarginStart(dp(ctx(), 14));
        dlp.setMarginEnd(dp(ctx(), 5));
        row.addView(dot, dlp);
        row.addView(text(ctx(), label, 11, MUTED));
    }

    private CharSequence formatTodoTabLabel(String title, int count) {
        if (count <= 0) return title;
        String raw = title + " " + count;
        SpannableString span = new SpannableString(raw);
        span.setSpan(new RelativeSizeSpan(0.86f), title.length() + 1, raw.length(), 0);
        return span;
    }

    private static String shortMonthDay(String dayKey) {
        if (dayKey == null || dayKey.length() != 10) return dayKey == null ? "" : dayKey;
        Calendar c = TodoStore.parseDay(dayKey);
        return (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";
    }

    private static String formatTodoDayHeader(
        String dayKey,
        String todayKey,
        String tomorrowKey,
        String yesterdayKey,
        boolean doneGroup
    ) {
        if (dayKey == null || dayKey.isEmpty()) return "随时可做 · 未限日期";
        Calendar c = TodoStore.parseDay(dayKey);
        String md = (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";
        if (dayKey.equals(todayKey)) return "今天 · " + md;
        if (dayKey.equals(tomorrowKey)) return "明天 · " + md;
        if (doneGroup && dayKey.equals(yesterdayKey)) return "昨天 · " + md;
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        if (c.get(Calendar.YEAR) != currentYear) {
            return c.get(Calendar.YEAR) + "年" + md;
        }
        String[] weeks = {"", "周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return md + " · " + weeks[c.get(Calendar.DAY_OF_WEEK)];
    }

    private void addTodoGroupHeader(LinearLayout host, String title, int count, boolean overdue, boolean first) {
        LinearLayout dateHeader = row(ctx());
        dateHeader.setPadding(dp(ctx(), 50), dp(ctx(), first ? 6 : 14), dp(ctx(), 4), dp(ctx(), 6));
        TextView dateTitle = text(ctx(), title, 12, overdue ? 0xff9e4c37 : 0xff3b543c);
        dateTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        dateHeader.addView(dateTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView countTv = text(ctx(), count + " 项", 11, MUTED);
        countTv.setFontFeatureSettings("tnum");
        dateHeader.addView(countTv);
        host.addView(dateHeader, new LinearLayout.LayoutParams(-1, -2));
    }

    private void populateStandaloneTodos(
        LinearLayout host,
        @Nullable ForestSegmentedControl filterBar,
        TodoStore todos
    ) {
        host.removeAllViews();
        long now = System.currentTimeMillis();
        String todayKey = TodoStore.dayKey(now);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        String tomorrowKey = TodoStore.dayKey(cal.getTimeInMillis());
        Calendar yCal = Calendar.getInstance();
        yCal.add(Calendar.DATE, -1);
        String yesterdayKey = TodoStore.dayKey(yCal.getTimeInMillis());

        List<TodoStore.TodoItem> pendingList = todos.pending();
        List<TodoStore.TodoItem> overdueItems = new ArrayList<>();
        List<TodoStore.TodoItem> todayItems = new ArrayList<>();
        List<TodoStore.TodoItem> plannedDated = new ArrayList<>();
        List<TodoStore.TodoItem> plannedAnytime = new ArrayList<>();
        for (TodoStore.TodoItem t : pendingList) {
            if (!t.day.isEmpty() && t.day.compareTo(todayKey) < 0) {
                overdueItems.add(t);
            } else if (t.day.equals(todayKey)) {
                todayItems.add(t);
            } else if (!t.day.isEmpty()) {
                plannedDated.add(t);
            } else {
                plannedAnytime.add(t);
            }
        }
        List<TodoStore.TodoItem> doneList = todos.completed();
        int todayCount = overdueItems.size() + todayItems.size();
        int plannedCount = plannedDated.size() + plannedAnytime.size();
        if (filterBar != null) {
            filterBar.setTabLabels(new CharSequence[] {
                formatTodoTabLabel("今天", todayCount),
                formatTodoTabLabel("计划", plannedCount),
                formatTodoTabLabel("已完成", doneList.size())
            });
            filterBar.setSelectionCleared(todoFilterDay != null && !todoFilterDay.isEmpty());
        }

        // Single-Day Date Filter Mode: shows ALL pending + completed items for todoFilterDay
        if (todoFilterDay != null && !todoFilterDay.isEmpty()) {
            String targetDay = todoFilterDay;
            boolean targetOverdue = targetDay.compareTo(todayKey) < 0;
            List<TodoStore.TodoItem> dayPending = new ArrayList<>();
            for (TodoStore.TodoItem t : pendingList) {
                if (targetDay.equals(t.day)) dayPending.add(t);
            }
            List<TodoStore.TodoItem> dayDone = new ArrayList<>();
            for (TodoStore.TodoItem t : doneList) {
                String doneDay = t.completed > 0 ? TodoStore.dayKey(t.completed) : t.day;
                if (targetDay.equals(doneDay)) dayDone.add(t);
            }
            String quickHint = "＋ 快速记一条 " + shortMonthDay(targetDay) + " 待办...";
            if (dayPending.isEmpty() && dayDone.isEmpty()) {
                LinearLayout emptyBox = column(ctx());
                emptyBox.setPadding(dp(ctx(), 50), dp(ctx(), 18), dp(ctx(), 8), dp(ctx(), 12));
                emptyBox.addView(heading(ctx(), shortMonthDay(targetDay) + " 暂无待办", 17));
                ForestUi.add(emptyBox, note("可直接在下方记一条这一天的待办，或点上方 ‹ › 切换相邻日期。"), 6);
                host.addView(emptyBox);
                appendQuickAddRow(host, filterBar, todos, targetDay, quickHint);
                return;
            }
            int gIdx = 0;
            if (!dayPending.isEmpty()) {
                String header = (targetOverdue ? "已逾期 · " : "待办 · ")
                    + formatTodoDayHeader(targetDay, todayKey, tomorrowKey, yesterdayKey, false);
                addTodoGroupHeader(host, header, dayPending.size(), targetOverdue, gIdx++ == 0);
                for (TodoStore.TodoItem item : dayPending) {
                    addPendingTodoRow(host, filterBar, todos, item, todayKey, targetOverdue);
                }
            }
            if (!dayDone.isEmpty()) {
                addTodoGroupHeader(host, "当天已完成", dayDone.size(), false, gIdx++ == 0);
                SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.CHINA);
                for (TodoStore.TodoItem item : dayDone) {
                    StringBuilder meta = new StringBuilder();
                    if (item.completed > 0) {
                        meta.append(hm.format(new java.util.Date(item.completed))).append(" 完成");
                    } else {
                        meta.append("已完成");
                    }
                    if (!item.day.isEmpty() && !item.day.equals(targetDay)) {
                        meta.append(" · 原计划 ").append(shortMonthDay(item.day));
                    }
                    if (!item.note.isEmpty()) {
                        meta.append(" · ").append(item.note);
                    }
                    ForestTodoRow doneRow = new ForestTodoRow(
                        ctx(),
                        item.title,
                        0,
                        null,
                        meta.toString(),
                        false,
                        true,
                        () -> {
                            todos.toggleDone(item.id, false);
                            populateStandaloneTodos(host, filterBar, todos);
                        },
                        () -> todoEditorSheet(item)
                    );
                    host.addView(doneRow);
                }
            }
            appendQuickAddRow(host, filterBar, todos, targetDay, quickHint);
            return;
        }

        // Tab 2: 已完成 · 按实际完成日期倒序分组日志
        if (todoFilter == 2) {
            if (doneList.isEmpty()) {
                LinearLayout emptyBox = column(ctx());
                emptyBox.setPadding(dp(ctx(), 50), dp(ctx(), 20), dp(ctx(), 8), dp(ctx(), 20));
                emptyBox.addView(heading(ctx(), "还没有已完成的待办", 17));
                ForestUi.add(emptyBox, note("打勾划掉后，会按实际完成日期与时刻自动记入这里。"), 6);
                host.addView(emptyBox);
                return;
            }
            Map<String, List<TodoStore.TodoItem>> doneByDay = new LinkedHashMap<>();
            for (TodoStore.TodoItem item : doneList) {
                String doneDay = item.completed > 0
                    ? TodoStore.dayKey(item.completed)
                    : (item.day.isEmpty() ? todayKey : item.day);
                doneByDay.computeIfAbsent(doneDay, k -> new ArrayList<>()).add(item);
            }
            SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.CHINA);
            int groupIdx = 0;
            for (Map.Entry<String, List<TodoStore.TodoItem>> entry : doneByDay.entrySet()) {
                String doneDay = entry.getKey();
                addTodoGroupHeader(
                    host,
                    formatTodoDayHeader(doneDay, todayKey, tomorrowKey, yesterdayKey, true),
                    entry.getValue().size(),
                    false,
                    groupIdx++ == 0
                );
                for (TodoStore.TodoItem item : entry.getValue()) {
                    StringBuilder meta = new StringBuilder();
                    if (item.completed > 0) {
                        meta.append(hm.format(new java.util.Date(item.completed))).append(" 完成");
                    } else {
                        meta.append("已完成");
                    }
                    if (!item.day.isEmpty() && !item.day.equals(doneDay)) {
                        meta.append(" · 原计划 ").append(shortMonthDay(item.day));
                    }
                    if (!item.note.isEmpty()) {
                        meta.append(" · ").append(item.note);
                    }
                    ForestTodoRow row = new ForestTodoRow(
                        ctx(),
                        item.title,
                        0,
                        null,
                        meta.toString(),
                        false,
                        true,
                        () -> {
                            todos.toggleDone(item.id, false);
                            populateStandaloneTodos(host, filterBar, todos);
                        },
                        () -> todoEditorSheet(item)
                    );
                    host.addView(row);
                }
            }
            return;
        }

        // Tab 0: 今天 = 已逾期（暖陶红置顶） + 今日待办
        if (todoFilter == 0) {
            if (todayCount == 0) {
                LinearLayout emptyBox = column(ctx());
                emptyBox.setPadding(dp(ctx(), 50), dp(ctx(), 18), dp(ctx(), 8), dp(ctx(), 12));
                emptyBox.addView(heading(ctx(), "今天的事项都清完了", 17));
                String sub = plannedCount > 0
                    ? "计划里还有 " + plannedCount + " 项后续安排，随时可切过去查看。"
                    : "在下方直接记下今日待办，或点右上角「+ 新建」设置截止提醒。";
                ForestUi.add(emptyBox, note(sub), 6);
                host.addView(emptyBox);
                appendQuickAddRow(host, filterBar, todos, todayKey, "＋ 快速记一条今日待办...");
                return;
            }
            int groupIdx = 0;
            if (!overdueItems.isEmpty()) {
                addTodoGroupHeader(host, "已逾期 · 优先清掉", overdueItems.size(), true, groupIdx++ == 0);
                for (TodoStore.TodoItem item : overdueItems) {
                    addPendingTodoRow(host, filterBar, todos, item, todayKey, true);
                }
            }
            if (!todayItems.isEmpty()) {
                addTodoGroupHeader(
                    host,
                    formatTodoDayHeader(todayKey, todayKey, tomorrowKey, yesterdayKey, false),
                    todayItems.size(),
                    false,
                    groupIdx++ == 0
                );
                for (TodoStore.TodoItem item : todayItems) {
                    addPendingTodoRow(host, filterBar, todos, item, todayKey, false);
                }
            }
            appendQuickAddRow(host, filterBar, todos, todayKey, "＋ 快速记一条今日待办...");
            return;
        }

        // Tab 1: 计划 = 未来日程（按日分组） + 随时可做（未限日期收集箱）
        if (plannedCount == 0) {
            LinearLayout emptyBox = column(ctx());
            emptyBox.setPadding(dp(ctx(), 50), dp(ctx(), 18), dp(ctx(), 8), dp(ctx(), 12));
            emptyBox.addView(heading(ctx(), "暂无后续安排", 17));
            ForestUi.add(emptyBox, note("未来日期的日程与未限日期的收集箱都会收在这里，和「今天」互不重复。"), 6);
            host.addView(emptyBox);
            appendQuickAddRow(host, filterBar, todos, "", "＋ 随手记一条待安排事项（不限日期）...");
            return;
        }
        Map<String, List<TodoStore.TodoItem>> futureByDay = new LinkedHashMap<>();
        for (TodoStore.TodoItem item : plannedDated) {
            futureByDay.computeIfAbsent(item.day, k -> new ArrayList<>()).add(item);
        }
        int groupIdx = 0;
        for (Map.Entry<String, List<TodoStore.TodoItem>> entry : futureByDay.entrySet()) {
            addTodoGroupHeader(
                host,
                formatTodoDayHeader(entry.getKey(), todayKey, tomorrowKey, yesterdayKey, false),
                entry.getValue().size(),
                false,
                groupIdx++ == 0
            );
            for (TodoStore.TodoItem item : entry.getValue()) {
                addPendingTodoRow(host, filterBar, todos, item, todayKey, false);
            }
        }
        if (!plannedAnytime.isEmpty()) {
            addTodoGroupHeader(host, "随时可做 · 未限日期", plannedAnytime.size(), false, groupIdx++ == 0);
            for (TodoStore.TodoItem item : plannedAnytime) {
                addPendingTodoRow(host, filterBar, todos, item, todayKey, false);
            }
        }
        appendQuickAddRow(host, filterBar, todos, "", "＋ 随手记一条待安排事项（不限日期）...");
    }

    private void addPendingTodoRow(
        LinearLayout host,
        @Nullable ForestSegmentedControl filterBar,
        TodoStore todos,
        TodoStore.TodoItem item,
        String todayKey,
        boolean isOverdue
    ) {
        StringBuilder meta = new StringBuilder();
        if (isOverdue) {
            meta.append("原定 ").append(shortMonthDay(item.day));
        }
        if (!item.dueTime.isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(item.dueTime).append(" 截止");
            if (item.remind && !item.day.isEmpty()) {
                meta.append(" · ").append(item.leadLabel()).append("提醒");
            }
        } else if (item.remind && !item.day.isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append("当日 ").append(todos.defaultRemindTime()).append(" 提醒");
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
                toast("已归档到「已完成」");
                populateStandaloneTodos(host, filterBar, todos);
            },
            () -> todoEditorSheet(item)
        );
        host.addView(todoRow);
    }

    private void appendQuickAddRow(
        LinearLayout host,
        @Nullable ForestSegmentedControl filterBar,
        TodoStore todos,
        String defaultDay,
        String hint
    ) {
        LinearLayout quick = row(ctx());
        quick.setPadding(dp(ctx(), 50), dp(ctx(), 10), dp(ctx(), 6), dp(ctx(), 6));
        EditText input = new EditText(ctx());
        input.setHint(hint);
        input.setHintTextColor(0xff9aa094);
        input.setTextColor(0xff243624);
        input.setTextSize(14f);
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setBackground(null);
        input.setPadding(0, dp(ctx(), 6), dp(ctx(), 6), dp(ctx(), 6));
        Runnable commit = () -> {
            String textVal = input.getText().toString().trim();
            if (textVal.isEmpty()) return;
            todos.add(textVal, "", defaultDay, "", false, 30);
            input.setText("");
            populateStandaloneTodos(host, filterBar, todos);
            String msg = defaultDay.isEmpty() ? "已记入计划收集箱"
                : defaultDay.equals(TodoStore.dayKey(System.currentTimeMillis())) ? "已记入今日待办"
                : "已记入 " + shortMonthDay(defaultDay) + " 待办";
            toast(msg);
        };
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                commit.run();
                return true;
            }
            return false;
        });
        quick.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        TextView addBtn = StudySheet.link(ctx(), "记下", DEEP, commit);
        addBtn.setTextSize(12);
        addBtn.setMinimumHeight(dp(ctx(), 32));
        addBtn.setPadding(dp(ctx(), 12), dp(ctx(), 4), dp(ctx(), 12), dp(ctx(), 4));
        addBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xffeef4ea, 0, 999), false));
        quick.addView(addBtn);
        host.addView(quick, new LinearLayout.LayoutParams(-1, -2));
    }

    private void todoEditorSheet(@Nullable TodoStore.TodoItem existing) {
        TodoStore todos = TodoStore.get(ctx());
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane(existing == null ? "新建待办" : "编辑待办", null, 0);

        // 1. Borderless editorial writing area directly on porcelain sheet
        EditText title = new EditText(ctx());
        title.setSingleLine(false);
        title.setMaxLines(3);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setHint("准备做哪件事？");
        title.setFilters(new InputFilter[] { new InputFilter.LengthFilter(120) });
        title.setText(existing == null ? "" : existing.title);
        title.setTextSize(17.5f);
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
        noteInput.setHint("添加备注（选填）");
        noteInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(200) });
        noteInput.setText(existing == null ? "" : existing.note);
        noteInput.setTextSize(13.5f);
        noteInput.setTextColor(MUTED);
        noteInput.setHintTextColor(0xffa7ad9f);
        noteInput.setBackground(null);
        noteInput.setPadding(dp(ctx(), 2), dp(ctx(), 2), dp(ctx(), 2), dp(ctx(), 8));
        pane.body.addView(noteInput, new LinearLayout.LayoutParams(-1, -2));

        StudySheet.addHairline(pane.body, 4, 10);

        String todayKey = TodoStore.dayKey(System.currentTimeMillis());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        String tomorrowKey = TodoStore.dayKey(cal.getTimeInMillis());

        String defaultNewDay = (todoFilterDay != null && !todoFilterDay.isEmpty())
            ? todoFilterDay
            : (todoFilter == 1 ? "" : todayKey);
        String[] chosenDay = { existing == null ? defaultNewDay : existing.day };
        int[] leadMinutes = { existing == null ? 30 : existing.remindLeadMinutes };
        boolean initialRemind = existing != null && existing.remind;

        // 2. Direct Date Row (borderless flat chips directly on sheet — zero nested card)
        LinearLayout dateRow = row(ctx());
        dateRow.setMinimumHeight(dp(ctx(), 44));
        TextView dateLabel = text(ctx(), "日期", 13, 0xff5b5447);
        dateLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dateRow.addView(dateLabel, new LinearLayout.LayoutParams(0, -2, 1));

        TextView todayChip = StudySheet.link(ctx(), "今天", DEEP, () -> {});
        TextView tomorrowChip = StudySheet.link(ctx(), "明天", DEEP, () -> {});
        TextView pickDayChip = StudySheet.link(ctx(), "选日期", DEEP, () -> {});
        TextView anytimeChip = StudySheet.link(ctx(), "不限", DEEP, () -> {});
        TextView[] dateChips = { todayChip, tomorrowChip, pickDayChip, anytimeChip };
        for (int i = 0; i < dateChips.length; i++) {
            TextView chip = dateChips[i];
            chip.setTextSize(12);
            chip.setMinimumHeight(dp(ctx(), 32));
            chip.setPadding(dp(ctx(), 11), dp(ctx(), 5), dp(ctx(), 11), dp(ctx(), 5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.setMarginStart(dp(ctx(), 6));
            dateRow.addView(chip, lp);
        }
        pane.body.addView(dateRow, new LinearLayout.LayoutParams(-1, -2));

        Runnable syncDateChips = () -> {
            String cur = chosenDay[0];
            boolean isToday = cur.equals(todayKey);
            boolean isTomorrow = cur.equals(tomorrowKey);
            boolean isAnytime = cur.isEmpty();
            boolean isCustom = !isToday && !isTomorrow && !isAnytime;
            pickDayChip.setText(isCustom ? shortMonthDay(cur) : "选日期");
            StudySheet.styleChoiceChip(todayChip, isToday);
            StudySheet.styleChoiceChip(tomorrowChip, isTomorrow);
            StudySheet.styleChoiceChip(pickDayChip, isCustom);
            StudySheet.styleChoiceChip(anytimeChip, isAnytime);
        };
        todayChip.setOnClickListener(v -> { chosenDay[0] = todayKey; syncDateChips.run(); });
        tomorrowChip.setOnClickListener(v -> { chosenDay[0] = tomorrowKey; syncDateChips.run(); });
        anytimeChip.setOnClickListener(v -> { chosenDay[0] = ""; syncDateChips.run(); });
        pickDayChip.setOnClickListener(v -> {
            sheet.hideKeyboard();
            sheet.pushDatePicker("选择待办日期", chosenDay[0], picked -> {
                chosenDay[0] = picked;
                syncDateChips.run();
            });
        });
        syncDateChips.run();

        StudySheet.addHairline(pane.body, 10, 4);

        // 3. Direct Deadline Time Row + collapsible flat preset strip
        LinearLayout timeHeader = row(ctx());
        timeHeader.setMinimumHeight(dp(ctx(), 48));
        timeHeader.setPadding(dp(ctx(), 2), dp(ctx(), 8), dp(ctx(), 2), dp(ctx(), 8));
        timeHeader.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false));
        TextView timeRowLabel = text(ctx(), "截止时刻", 13, 0xff5b5447);
        timeRowLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        timeHeader.addView(timeRowLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView timeValue = text(ctx(), "未设置 ›", 13, MUTED);
        timeValue.setFontFeatureSettings("tnum");
        timeHeader.addView(timeValue);
        pane.body.addView(timeHeader, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout timePanel = column(ctx());
        timePanel.setVisibility(View.GONE);
        LinearLayout timePresetsRow = row(ctx());
        EditText dueTimeInput = new EditText(ctx());
        dueTimeInput.setSingleLine(true);
        dueTimeInput.setInputType(InputType.TYPE_CLASS_TEXT);
        dueTimeInput.setHint("或输入具体时刻，如 19:30");
        dueTimeInput.setText(existing == null ? "" : existing.dueTime);
        dueTimeInput.setFontFeatureSettings("tnum");
        StudySheet.styleQuietInlineField(dueTimeInput);

        String[] timePresets = {"12:00", "18:00", "21:00", "22:30", "不限"};
        List<TextView> timePresetChips = new ArrayList<>();
        Runnable syncTimeUi = () -> {
            String tVal = dueTimeInput.getText().toString().trim();
            boolean hasTime = !tVal.isEmpty();
            timeValue.setText(hasTime ? tVal + " 截止 ›" : "未设置 ›");
            timeValue.setTextColor(hasTime ? 0xff9e4c37 : MUTED);
            for (int i = 0; i < timePresets.length; i++) {
                String preset = timePresets[i];
                boolean active = "不限".equals(preset) ? !hasTime : preset.equals(tVal);
                StudySheet.styleChoiceChip(timePresetChips.get(i), active);
            }
        };
        for (int i = 0; i < timePresets.length; i++) {
            String preset = timePresets[i];
            TextView chip = StudySheet.link(ctx(), preset, DEEP, () -> {
                dueTimeInput.setText("不限".equals(preset) ? "" : preset);
                syncTimeUi.run();
                timePanel.setVisibility(View.GONE);
            });
            chip.setTextSize(12);
            chip.setFontFeatureSettings("tnum");
            chip.setMinimumHeight(dp(ctx(), 34));
            chip.setPadding(dp(ctx(), 6), dp(ctx(), 5), dp(ctx(), 6), dp(ctx(), 5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(ctx(), 6));
            timePresetsRow.addView(chip, lp);
            timePresetChips.add(chip);
        }
        timePanel.addView(timePresetsRow);
        ForestUi.add(timePanel, dueTimeInput, 6);
        ForestUi.add(pane.body, timePanel, 4);

        timeHeader.setOnClickListener(v -> {
            boolean show = timePanel.getVisibility() != View.VISIBLE;
            timePanel.setVisibility(show ? View.VISIBLE : View.GONE);
        });
        dueTimeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { syncTimeUi.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        syncTimeUi.run();

        StudySheet.addHairline(pane.body, 4, 2);

        // 4. Borderless Morphing Leaf Switch Row directly on sheet
        MaterialCheckBox remindSwitch = ForestUi.flatSwitchRow(
            ctx(),
            "截止提醒",
            todoLeadSubtitle(initialRemind, leadMinutes[0], todos.defaultRemindTime())
        );
        remindSwitch.setChecked(initialRemind);
        pane.body.addView(remindSwitch, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout leadRow = row(ctx());
        int[] leadValues = {15, 30, 60, 1440};
        String[] leadLabels = {"提前15分", "提前30分", "提前1小时", "前一天"};
        List<TextView> leadButtons = new ArrayList<>();
        Runnable updateLeadUi = () -> {
            boolean on = remindSwitch.isChecked();
            leadRow.setVisibility(on ? View.VISIBLE : View.GONE);
            ForestUi.updateFlatSwitchSubtitle(
                remindSwitch,
                todoLeadSubtitle(on, leadMinutes[0], todos.defaultRemindTime())
            );
            for (int i = 0; i < leadButtons.size(); i++) {
                StudySheet.styleChoiceChip(leadButtons.get(i), leadMinutes[0] == leadValues[i]);
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
            btn.setPadding(dp(ctx(), 6), dp(ctx(), 5), dp(ctx(), 6), dp(ctx(), 5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(ctx(), 6));
            leadRow.addView(btn, lp);
            leadButtons.add(btn);
        }
        remindSwitch.setOnCheckedChangeListener((b, checked) -> updateLeadUi.run());
        updateLeadUi.run();
        ForestUi.add(pane.body, leadRow, 4);

        if (existing != null && existing.completed == 0L && store.notebook().currentPlan() != null && !store.notebook().hasSession()) {
            StudySheet.addHairline(pane.body, 12, 4);
            LinearLayout focusRow = row(ctx());
            focusRow.setMinimumHeight(dp(ctx(), 48));
            focusRow.setPadding(dp(ctx(), 2), dp(ctx(), 8), dp(ctx(), 2), dp(ctx(), 8));
            focusRow.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false));
            TextView focusTitle = text(ctx(), "以此为目标去专注", 13, 0xff315e3a);
            focusTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            focusRow.addView(focusTitle, new LinearLayout.LayoutParams(0, -2, 1));
            focusRow.addView(text(ctx(), "前往自习室 ›", 12, MUTED));
            focusRow.setOnClickListener(v -> {
                String taskName = title.getText().toString().trim();
                if (taskName.isEmpty()) taskName = existing.title;
                store.start("", taskName);
                sheet.dismiss();
                open(AppDestination.STUDY_FOCUS);
            });
            ForestMotion.bind(focusRow);
            pane.body.addView(focusRow, new LinearLayout.LayoutParams(-1, -2));
        }

        if (existing != null) {
            pane.secondary("删除待办", () -> {
                todos.delete(existing.id);
                sheet.dismiss();
                render();
            });
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
        if (!enabled) return "已关闭，开启后在截止前自动提醒";
        if (leadMinutes >= 1440) return "截止前一天 " + defaultTime + " 轻提醒";
        if (leadMinutes >= 60 && leadMinutes % 60 == 0)
            return "截止前 " + (leadMinutes / 60) + " 小时轻提醒";
        return "截止前 " + leadMinutes + " 分钟轻提醒";
    }

    private void todoReminderSettingsDialog() {
        TodoStore todos = TodoStore.get(ctx());
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("待办提醒", null, 0);

        String initialTime = todos.defaultRemindTime();
        String[] presets = {"09:00", "12:00", "18:00", "20:30"};
        boolean initialIsPreset = false;
        for (String p : presets) if (p.equals(initialTime)) initialIsPreset = true;
        boolean[] customMode = { !initialIsPreset };
        String[] selectedTime = { initialTime };

        StudySheet.addHairline(pane.body, 0, 2);

        // 1. Direct Borderless Master Leaf Switch
        MaterialCheckBox masterSwitch = ForestUi.flatSwitchRow(
            ctx(),
            "截止前提醒",
            todos.remindEnabled()
                ? "未设具体时刻时，每日 " + initialTime + " 提醒"
                : "已关闭，开启后在截止前自动提醒"
        );
        masterSwitch.setChecked(todos.remindEnabled());
        pane.body.addView(masterSwitch, new LinearLayout.LayoutParams(-1, -2));

        // 2. Progressive Disclosure Container (collapses cleanly when master switch is OFF)
        LinearLayout detailsSection = column(ctx());
        StudySheet.addHairline(detailsSection, 2, 14);

        LinearLayout timeHeader = row(ctx());
        TextView timeTitle = text(ctx(), "默认提醒时间", 14, 0xff243624);
        timeTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        timeHeader.addView(timeTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView timeDisplay = text(ctx(), initialTime, 14, 0xff315e3a);
        timeDisplay.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        timeDisplay.setFontFeatureSettings("tnum");
        timeHeader.addView(timeDisplay);
        detailsSection.addView(timeHeader, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout presetRow = row(ctx());
        List<TextView> timeChips = new ArrayList<>();
        String[] chipLabels = {"09:00", "12:00", "18:00", "20:30", "自定"};

        EditText customInput = new EditText(ctx());
        customInput.setSingleLine(true);
        customInput.setText(initialTime);
        customInput.setHint("输入时刻，如 19:30");
        customInput.setFontFeatureSettings("tnum");
        StudySheet.styleQuietInlineField(customInput);

        Runnable syncUi = () -> {
            boolean on = masterSwitch.isChecked();
            detailsSection.setVisibility(on ? View.VISIBLE : View.GONE);
            String cur = selectedTime[0].isEmpty() ? initialTime : selectedTime[0];
            timeDisplay.setText(cur);
            ForestUi.updateFlatSwitchSubtitle(
                masterSwitch,
                on ? "未设具体时刻时，每日 " + cur + " 提醒" : "已关闭，开启后在截止前自动提醒"
            );
            customInput.setVisibility(customMode[0] ? View.VISIBLE : View.GONE);
            for (int i = 0; i < chipLabels.length; i++) {
                boolean active = customMode[0] ? (i == chipLabels.length - 1) : chipLabels[i].equals(cur);
                StudySheet.styleChoiceChip(timeChips.get(i), active);
            }
        };

        for (int i = 0; i < chipLabels.length; i++) {
            String lbl = chipLabels[i];
            boolean isCustom = "自定".equals(lbl);
            TextView chip = StudySheet.link(ctx(), lbl, DEEP, () -> {
                if (isCustom) {
                    customMode[0] = true;
                    String typed = customInput.getText().toString().trim();
                    if (!typed.isEmpty()) selectedTime[0] = typed;
                    syncUi.run();
                    customInput.requestFocus();
                } else {
                    customMode[0] = false;
                    selectedTime[0] = lbl;
                    customInput.setText(lbl);
                    syncUi.run();
                }
            });
            chip.setTextSize(12);
            chip.setFontFeatureSettings("tnum");
            chip.setMinimumHeight(dp(ctx(), 36));
            chip.setPadding(dp(ctx(), 4), dp(ctx(), 5), dp(ctx(), 4), dp(ctx(), 5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(ctx(), 6));
            presetRow.addView(chip, lp);
            timeChips.add(chip);
        }
        ForestUi.add(detailsSection, presetRow, 12);
        ForestUi.add(detailsSection, customInput, 6);

        customInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (customMode[0]) {
                    selectedTime[0] = s.toString().trim();
                    String cur = selectedTime[0].isEmpty() ? initialTime : selectedTime[0];
                    timeDisplay.setText(cur);
                    if (masterSwitch.isChecked()) {
                        ForestUi.updateFlatSwitchSubtitle(masterSwitch, "未设具体时刻时，每日 " + cur + " 提醒");
                    }
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        StudySheet.addHairline(detailsSection, 14, 2);

        // 3. Quiet Inline Test Notification Row directly on sheet
        LinearLayout testRow = row(ctx());
        testRow.setMinimumHeight(dp(ctx(), 48));
        testRow.setPadding(dp(ctx(), 2), dp(ctx(), 8), dp(ctx(), 2), dp(ctx(), 8));
        testRow.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false));
        TextView testTitle = text(ctx(), "发送一条测试通知", 13, 0xff456444);
        testTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        testRow.addView(testTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView testStatus = text(ctx(), "验证提醒通道 ›", 12, 0xff8f8675);
        testRow.addView(testStatus);
        testRow.setOnClickListener(v -> {
            allowStudyNotifications();
            boolean sent = TodoNotifications.sendImmediate(
                ctx(),
                "待办即将截止提醒",
                "提醒功能已就绪！将在待办截止时间前自动通知你。"
            );
            testStatus.setText(sent ? "已发送测试通知 ✓" : "请先开启系统通知");
            testStatus.setTextColor(sent ? 0xff315e3a : 0xff9e4c37);
            toast(sent ? "已发送测试提醒通知，请查看顶部通知栏" : "请先允许应用发送系统通知");
        });
        ForestMotion.bind(testRow);
        detailsSection.addView(testRow, new LinearLayout.LayoutParams(-1, -2));

        pane.body.addView(detailsSection, new LinearLayout.LayoutParams(-1, -2));
        masterSwitch.setOnCheckedChangeListener((b, checked) -> syncUi.run());
        syncUi.run();

        pane.primary("保存设置", () -> perform(() -> {
            String timeToSave = customMode[0] ? customInput.getText().toString() : selectedTime[0];
            todos.setReminderConfig(masterSwitch.isChecked(), timeToSave);
            if (masterSwitch.isChecked()) allowStudyNotifications();
            sheet.dismiss();
            render();
        }));
        sheet.push(pane);
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
        StudySheet.Pane pane = sheetPane("提醒设置", null, 0);

        String initialTime = plan.remindTime;
        String[] presets = {"09:00", "12:00", "18:00", "20:30"};
        boolean initialIsPreset = false;
        for (String p : presets) if (p.equals(initialTime)) initialIsPreset = true;
        boolean[] customMode = { !initialIsPreset };
        String[] selectedTime = { initialTime };

        StudySheet.addHairline(pane.body, 0, 2);

        MaterialCheckBox masterSwitch = ForestUi.flatSwitchRow(
            ctx(),
            "考试与截止前提醒",
            planRemindSubtitle(plan)
        );
        masterSwitch.setChecked(plan.remindDeadlines);
        pane.body.addView(masterSwitch, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout detailsSection = column(ctx());
        StudySheet.addHairline(detailsSection, 2, 14);

        LinearLayout timeHeader = row(ctx());
        TextView timeTitle = text(ctx(), "默认提醒时间", 14, 0xff243624);
        timeTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        timeHeader.addView(timeTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView timeDisplay = text(ctx(), initialTime, 14, 0xff315e3a);
        timeDisplay.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        timeDisplay.setFontFeatureSettings("tnum");
        timeHeader.addView(timeDisplay);
        detailsSection.addView(timeHeader, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout presetRow = row(ctx());
        List<TextView> timeChips = new ArrayList<>();
        String[] chipLabels = {"09:00", "12:00", "18:00", "20:30", "自定"};

        EditText customInput = new EditText(ctx());
        customInput.setSingleLine(true);
        customInput.setText(initialTime);
        customInput.setHint("输入时刻，如 18:00");
        customInput.setContentDescription("每日提醒时间");
        customInput.setFontFeatureSettings("tnum");
        StudySheet.styleQuietInlineField(customInput);

        Runnable syncUi = () -> {
            boolean on = masterSwitch.isChecked();
            detailsSection.setVisibility(on ? View.VISIBLE : View.GONE);
            String cur = selectedTime[0].isEmpty() ? initialTime : selectedTime[0];
            timeDisplay.setText(cur);
            ForestUi.updateFlatSwitchSubtitle(
                masterSwitch,
                on ? "截止前一天 " + cur + " 发送森林自习提醒" : "已关闭，开启后在截止前自动提醒"
            );
            customInput.setVisibility(customMode[0] ? View.VISIBLE : View.GONE);
            for (int i = 0; i < chipLabels.length; i++) {
                boolean active = customMode[0] ? (i == chipLabels.length - 1) : chipLabels[i].equals(cur);
                StudySheet.styleChoiceChip(timeChips.get(i), active);
            }
        };

        for (int i = 0; i < chipLabels.length; i++) {
            String lbl = chipLabels[i];
            boolean isCustom = "自定".equals(lbl);
            TextView chip = StudySheet.link(ctx(), lbl, DEEP, () -> {
                if (isCustom) {
                    customMode[0] = true;
                    String typed = customInput.getText().toString().trim();
                    if (!typed.isEmpty()) selectedTime[0] = typed;
                    syncUi.run();
                    customInput.requestFocus();
                } else {
                    customMode[0] = false;
                    selectedTime[0] = lbl;
                    customInput.setText(lbl);
                    syncUi.run();
                }
            });
            chip.setTextSize(12);
            chip.setFontFeatureSettings("tnum");
            chip.setMinimumHeight(dp(ctx(), 36));
            chip.setPadding(dp(ctx(), 4), dp(ctx(), 5), dp(ctx(), 4), dp(ctx(), 5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(ctx(), 6));
            presetRow.addView(chip, lp);
            timeChips.add(chip);
        }
        ForestUi.add(detailsSection, presetRow, 12);
        ForestUi.add(detailsSection, customInput, 6);

        customInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (customMode[0]) {
                    selectedTime[0] = s.toString().trim();
                    String cur = selectedTime[0].isEmpty() ? initialTime : selectedTime[0];
                    timeDisplay.setText(cur);
                    if (masterSwitch.isChecked()) {
                        ForestUi.updateFlatSwitchSubtitle(masterSwitch, "截止前一天 " + cur + " 发送森林自习提醒");
                    }
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        StudySheet.addHairline(detailsSection, 14, 2);

        LinearLayout testRow = row(ctx());
        testRow.setMinimumHeight(dp(ctx(), 48));
        testRow.setPadding(dp(ctx(), 2), dp(ctx(), 8), dp(ctx(), 2), dp(ctx(), 8));
        testRow.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false));
        TextView testTitle = text(ctx(), "发送一条测试通知", 13, 0xff456444);
        testTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        testRow.addView(testTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView testStatus = text(ctx(), "验证提醒通道 ›", 12, 0xff8f8675);
        testRow.addView(testStatus);
        testRow.setOnClickListener(v -> {
            allowStudyNotifications();
            boolean sent = StudyNotifications.sendImmediateReminder(
                ctx(),
                plan.id,
                "森林自习室 · 待办与截止提醒",
                "提醒功能已就绪！将在待办截止时间前自动通知你。"
            );
            testStatus.setText(sent ? "已发送测试通知 ✓" : "请先开启系统通知");
            testStatus.setTextColor(sent ? 0xff315e3a : 0xff9e4c37);
            toast(sent ? "已发送测试提醒通知，请查看顶部通知栏" : "请先允许应用发送系统通知");
        });
        ForestMotion.bind(testRow);
        detailsSection.addView(testRow, new LinearLayout.LayoutParams(-1, -2));

        pane.body.addView(detailsSection, new LinearLayout.LayoutParams(-1, -2));
        masterSwitch.setOnCheckedChangeListener((b, checked) -> syncUi.run());
        syncUi.run();

        pane.primary("保存设置", () -> perform(() -> {
            String rawTime = customMode[0] ? customInput.getText().toString() : selectedTime[0];
            String clean = StudyWork.Task.validateTime(rawTime);
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

        StudySheet.addHairline(pane.body, 0, 2);
        MaterialCheckBox remind = ForestUi.flatSwitchRow(
            ctx(),
            "考试与截止前提醒",
            planRemindSubtitle(plan)
        );
        remind.setChecked(plan.remindDeadlines);
        remind.setOnClickListener(v -> perform(() -> {
            store.update(n -> { n.remindDeadlines(plan.id, remind.isChecked()); return null; });
            ForestUi.updateFlatSwitchSubtitle(remind, planRemindSubtitle(plan));
            if (remind.isChecked()) allowStudyNotifications();
        }));
        pane.body.addView(remind, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout timeRow = row(ctx());
        timeRow.setMinimumHeight(dp(ctx(), 44));
        timeRow.setPadding(dp(ctx(), 2), dp(ctx(), 6), dp(ctx(), 2), dp(ctx(), 6));
        timeRow.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false));
        timeRow.addView(text(ctx(), "提醒时间设置", 13, 0xff5b5447), new LinearLayout.LayoutParams(0, -2, 1));
        TextView timeVal = text(ctx(), plan.remindTime + " ›", 13, 0xff315e3a);
        timeVal.setFontFeatureSettings("tnum");
        timeRow.addView(timeVal);
        timeRow.setOnClickListener(v -> reminderSettingsDialog(plan, refresh));
        ForestMotion.bind(timeRow);
        pane.body.addView(timeRow, new LinearLayout.LayoutParams(-1, -2));

        if (plan.remindDeadlines && !StudyNotifications.permitted(ctx())) {
            ForestUi.add(pane.body, StudySheet.link(ctx(), "开启系统通知", DEEP,
                this::allowStudyNotifications), 2);
        }

        List<StudyWork.Task> tasks = store.notebook().pendingTasks(plan.id);
        String todayKey = StudyNotebook.dayKey(System.currentTimeMillis());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        String tomorrowKey = StudyNotebook.dayKey(cal.getTimeInMillis());
        Calendar yCal = Calendar.getInstance();
        yCal.add(Calendar.DATE, -1);
        String yesterdayKey = StudyNotebook.dayKey(yCal.getTimeInMillis());

        int[] filterMode = {0}; // 0: 今天（含逾期）, 1: 计划（未来与不限）, 2: 已完成
        LinearLayout filterHost = column(ctx());
        LinearLayout taskListContainer = column(ctx());

        Runnable[] renderTasksRef = new Runnable[1];
        renderTasksRef[0] = () -> {
            filterHost.removeAllViews();
            filterHost.addView(editorialChartTabs(
                new String[] {"今天", "计划", "已完成"},
                new int[] {View.NO_ID, View.NO_ID, View.NO_ID},
                filterMode[0],
                idx -> {
                    if (filterMode[0] == idx) return;
                    filterMode[0] = idx;
                    renderTasksRef[0].run();
                }
            ));

            taskListContainer.removeAllViews();
            if (filterMode[0] == 2) {
                List<StudyWork.Task> doneTasks = store.notebook().completedTasks(plan.id, 0, Long.MAX_VALUE);
                if (doneTasks.isEmpty()) {
                    ForestUi.add(taskListContainer, note("还没有已完成的事项"), 10);
                } else {
                    Map<String, List<StudyWork.Task>> doneByDay = new LinkedHashMap<>();
                    for (StudyWork.Task dt : doneTasks) {
                        String dKey = dt.completed > 0 ? StudyNotebook.dayKey(dt.completed) : todayKey;
                        doneByDay.computeIfAbsent(dKey, k -> new ArrayList<>()).add(dt);
                    }
                    SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.CHINA);
                    for (Map.Entry<String, List<StudyWork.Task>> entry : doneByDay.entrySet()) {
                        TextView dayHd = text(ctx(),
                            formatTodoDayHeader(entry.getKey(), todayKey, tomorrowKey, yesterdayKey, true),
                            12, WOOD);
                        dayHd.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                        ForestUi.add(taskListContainer, dayHd, 12);
                        for (StudyWork.Task doneTask : entry.getValue()) {
                            StudySubject subj = plan.book(doneTask.subjectId);
                            String sub = (doneTask.completed > 0 ? hm.format(new java.util.Date(doneTask.completed)) + " 完成" : "已完成")
                                + (subj == null ? "" : " · " + subj.title);
                            MaterialCheckBox cb = ForestUi.flatCheckWithSubtitle(ctx(), doneTask.title, sub);
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
                            taskListContainer.addView(cb, new LinearLayout.LayoutParams(-1, -2));
                        }
                    }
                }
                return;
            }

            List<StudyWork.Task> filtered = new ArrayList<>();
            for (StudyWork.Task t : tasks) {
                boolean isTodayOrOverdue = !t.day.isEmpty() && t.day.compareTo(todayKey) <= 0;
                if (filterMode[0] == 0 && !isTodayOrOverdue) continue;
                if (filterMode[0] == 1 && isTodayOrOverdue) continue;
                filtered.add(t);
            }
            if (filtered.isEmpty()) {
                ForestUi.add(taskListContainer, note(filterMode[0] == 0 ? "今天暂时没有待做事项" : "暂无后续安排"), 10);
                return;
            }
            Map<String, List<StudyWork.Task>> grouped = groupTasksByDate(filtered, todayKey, tomorrowKey);
            for (Map.Entry<String, List<StudyWork.Task>> entry : grouped.entrySet()) {
                TextView dateHeader = text(
                    ctx(),
                    entry.getKey() + " · " + entry.getValue().size() + " 项",
                    12,
                    entry.getKey().startsWith("已逾期") ? 0xff9e4c37 : WOOD
                );
                dateHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                ForestUi.add(taskListContainer, dateHeader, 12);
                int rowIdx = 0;
                for (StudyWork.Task task : entry.getValue()) {
                    if (rowIdx++ > 0) StudySheet.addHairline(taskListContainer, 0, 0);
                    StudySubject subject = plan.book(task.subjectId);
                    String detail = formatTaskMeta(subject, task, todayKey, tomorrowKey);
                    Runnable start = store.notebook().hasSession() ? null : () -> perform(() -> startTask(task.id));
                    taskListContainer.addView(
                        StudySheet.item(
                            ctx(),
                            task.title,
                            detail,
                            0,
                            Color.TRANSPARENT,
                            "›",
                            false,
                            () -> work.taskForm(plan, subject, task, refresh, start)
                        ),
                        new LinearLayout.LayoutParams(-1, -2)
                    );
                }
            }
        };

        ForestUi.add(pane.body, filterHost, 8);
        ForestUi.add(pane.body, taskListContainer, 2);
        renderTasksRef[0].run();

        StudySheet.addHairline(pane.body, 14, 14);
        pane.body.addView(StudySheet.section(ctx(), "近期考试与截止"));
        List<StudySubject> deadlines = StudyWorkDialogs.deadlines(plan);
        if (deadlines.isEmpty()) ForestUi.add(pane.body, note("还没有设置近期日期"), 6);
        int dlIdx = 0;
        for (StudySubject subject : deadlines) {
            if (dlIdx++ > 0) StudySheet.addHairline(pane.body, 0, 0);
            String detail = subject.due + (subject.assessment.isEmpty() ? "" : " · " + subject.assessment);
            pane.body.addView(StudySheet.item(ctx(), subject.title, detail, 0, Color.TRANSPARENT, "›", false,
                () -> work.taskForm(plan, subject, null, refresh, null)), new LinearLayout.LayoutParams(-1, -2));
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
        boolean active = notebook.currentHasSession();
        long elapsed = active ? store.elapsedMillis() : 0L;
        int goalMin = active ? notebook.activeGoal() : notebook.sessionGoal();
        long goalMillis = Math.max(1L, goalMin * 60_000L);
        if (clock != null) {
            if (countdownMode) {
                if (!active) {
                    clock.setText(StudyFormat.clock(goalMillis));
                } else if (elapsed <= goalMillis) {
                    clock.setText(StudyFormat.clock(goalMillis - elapsed));
                } else {
                    clock.setText("+" + StudyFormat.clock(elapsed - goalMillis));
                }
            } else {
                clock.setText(StudyFormat.clock(elapsed));
            }
        }
        if (dialGoalCaption != null) {
            dialGoalCaption.setText((countdownMode ? "倒计时目标 " : "目标 ") + goalMin + " 分钟");
            dialGoalCaption.setTextColor(MUTED);
        }
        if (dialRightCaption != null) {
            if (!active) {
                dialRightCaption.setText("0%");
                dialRightCaption.setTextColor(0xff8f8675);
            } else if (elapsed > goalMillis) {
                long overMin = Math.max(1L, (elapsed - goalMillis) / 60_000L);
                dialRightCaption.setText("已超额 +" + overMin + " 分钟");
                dialRightCaption.setTextColor(0xff315e3a);
            } else {
                int pct = (int) Math.min(100L, (elapsed * 100L) / goalMillis);
                dialRightCaption.setText("已完成 " + pct + "%");
                dialRightCaption.setTextColor(0xff315e3a);
            }
        }
        if (activeStatusText != null) {
            activeStatusText.setText(
                notebook.running() ? "静心计时中 · 随时可暂停" : "已暂停 · 暂停时长不计入"
            );
        }
        if (dial != null) dial.setProgress((float) elapsed / goalMillis);
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
            long todayMillis = n.totalForDay(planId, now);
            int dailyGoalMin = Math.max(1, n.dailyGoal());
            long todayMin = todayMillis / 60_000L;
            int dailyPct = (int) Math.min(100L, (todayMin * 100L) / dailyGoalMin);
            today.setText(
                "今日已专注 " +
                    StudyFormat.duration(todayMillis) +
                    "\n每日目标 " +
                    dailyGoalMin +
                    "分钟 · " +
                    count +
                    "次学习"
            );
            if (dailyProgressBar != null) dailyProgressBar.setProgress(dailyPct);
            if (dailyProgressText != null) {
                if (todayMin >= dailyGoalMin) {
                    dailyProgressText.setText("今日目标已达成 ✓（100%）");
                    dailyProgressText.setTextColor(0xff34603a);
                } else {
                    dailyProgressText.setText("今日进度 " + dailyPct + "% · 还差 " + (dailyGoalMin - todayMin) + " 分钟达标");
                    dailyProgressText.setTextColor(MUTED);
                }
            }
        }
    }

    private void syncRunningStateInPlace(boolean running) {
        renderedRunning = running;
        if (focusStatusPill != null) {
            focusStatusPill.setBackground(rounded(ctx(), running ? 0xffeef3e8 : 0xfff5efe3, 0, 999));
        }
        if (statusPulseAnimator != null) {
            statusPulseAnimator.cancel();
            statusPulseAnimator = null;
        }
        if (focusStatusDot != null) {
            focusStatusDot.setAlpha(1f);
            focusStatusDot.setBackground(rounded(ctx(), running ? 0xff456444 : 0xffb8894f, 0, 99));
            if (running && (Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())) {
                statusPulseAnimator = android.animation.ObjectAnimator.ofFloat(focusStatusDot, View.ALPHA, 1f, 0.35f);
                statusPulseAnimator.setDuration(1100);
                statusPulseAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                statusPulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                statusPulseAnimator.start();
            }
        }
        if (focusStatusLabel != null) {
            focusStatusLabel.setText(running ? "正在专注" : "休息一下");
            focusStatusLabel.setTextColor(running ? 0xff315e3a : 0xff7b5c3b);
        }
        if (activeStatusText != null) {
            activeStatusText.setText(running ? "静心计时中 · 随时可暂停" : "已暂停 · 暂停时长不计入");
        }
        if (startPauseButton != null) {
            startPauseButton.setText(running ? "暂停一下" : "继续学习");
        }
        syncKeepScreenOn();
        updateTimer();
    }

    private void syncKeepScreenOn() {
        if (!isAdded() || getActivity() == null) return;
        boolean keep = keepScreenOn && page == AppDestination.STUDY_FOCUS && store != null && store.notebook().currentHasSession();
        if (keep) {
            getActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private android.graphics.drawable.Drawable roundedProgressDrawable() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xffe7e3d6);
        bg.setCornerRadius(dp(ctx(), 99));
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setColor(0xff456444);
        fill.setCornerRadius(dp(ctx(), 99));
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(
            fill, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
            new android.graphics.drawable.Drawable[] { bg, clip });
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        return layers;
    }

    private void dailyGoalDialog() {
        StudySheet sheet = newSheet();
        StudySheet.Pane pane = sheetPane("每日学习目标", "每天首次达标时获得一颗种子。", 0);
        StudySheet.addHairline(pane.body, 0, 12);

        EditText input = new EditText(ctx());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(store.notebook().dailyGoal()));
        input.setSelectAllOnFocus(true);
        input.setContentDescription("每日学习目标分钟");
        StudySheet.styleUnderlineField(input);
        input.setTextSize(22f);
        input.setTypeface(Typeface.create("serif", Typeface.BOLD));
        input.setFontFeatureSettings("tnum");
        pane.body.addView(StudySheet.section(ctx(), "目标时长（分钟）"));
        ForestUi.add(pane.body, input, 4);

        LinearLayout presets = row(ctx());
        int[] presetValues = { 60, 120, 180 };
        List<TextView> presetChips = new ArrayList<>();
        Runnable syncPresets = () -> {
            String cur = input.getText().toString().trim();
            for (int i = 0; i < presetValues.length; i++) {
                StudySheet.styleChoiceChip(presetChips.get(i), cur.equals(String.valueOf(presetValues[i])));
            }
        };
        for (int i = 0; i < presetValues.length; i++) {
            int minutes = presetValues[i];
            TextView option = StudySheet.link(ctx(), minutes + " 分钟", DEEP, () -> {
                input.setText(String.valueOf(minutes));
                input.setSelection(input.getText().length());
            });
            option.setTextSize(12);
            option.setFontFeatureSettings("tnum");
            option.setMinimumHeight(dp(ctx(), 38));
            LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) presetParams.setMarginStart(dp(ctx(), 8));
            presets.addView(option, presetParams);
            presetChips.add(option);
        }
        ForestUi.add(pane.body, presets, 14);

        TextView errorMessage = text(ctx(), "", 12, 0xff8d4836);
        errorMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ForestUi.add(pane.body, errorMessage, 8);
        errorMessage.setVisibility(View.GONE);
        input.addTextChangedListener(
            new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                    errorMessage.setVisibility(View.GONE);
                    syncPresets.run();
                }
                @Override public void afterTextChanged(Editable value) {}
            }
        );
        syncPresets.run();

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
        if (store.notebook().running()) {
            store.pause();
            if (focusStatusPill != null && startPauseButton != null && store.notebook().currentHasSession()) {
                syncRunningStateInPlace(false);
            } else {
                render();
            }
        }
        long elapsed = store.elapsedMillis();
        if (elapsed < 1000) {
            confirmDiscard(newSheet());
            return;
        }
        StudySheet sheet = newSheet();
        StudyWorkDialogs work = workDialogs(sheet);
        StudySheet.Pane pane = sheetPane("保存这段专注", null, 0);
        StudyNotebook notebook = store.notebook();
        String sessionId = notebook.sessionId();
        StudyPlan plan = notebook.plan(notebook.activePlanId());
        StudyWork.Task task = notebook.task(notebook.activeTaskId());
        StudySubject[] subject = { notebook.activeSubject() };
        long[] duration = { elapsed };

        // 1. Direct Editorial Receipt Summary (ZERO nested card box inside sheet!)
        StudySheet.addHairline(pane.body, 0, 14);
        LinearLayout receipt = row(ctx());
        LinearLayout receiptCopy = column(ctx());
        TextView subjectName = text(ctx(), "", 13, 0xff355336);
        subjectName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        subjectName.setText(ForestUi.badgeText(ctx(), subject[0].hint, subject[0].title, ""));
        TextView length = heading(ctx(), StudyFormat.duration(elapsed), 30);
        length.setTypeface(Typeface.create("serif", Typeface.BOLD));
        length.setFontFeatureSettings("tnum");
        length.setTextColor(0xff243624);
        receiptCopy.addView(subjectName);
        ForestUi.add(receiptCopy, length, 5);
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
        correctBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xfff2eee3, 0, 999), false));
        LinearLayout.LayoutParams correctLp = new LinearLayout.LayoutParams(-2, -2);
        correctLp.setMarginStart(dp(ctx(), 10));
        receipt.addView(correctBtn, correctLp);
        pane.body.addView(receipt, new LinearLayout.LayoutParams(-1, -2));

        StudySheet.addHairline(pane.body, 14, 4);

        MaterialCheckBox done = null;
        if (task != null) {
            done = ForestUi.flatCheckWithSubtitle(ctx(), "本次目标已完成", task.title);
            pane.body.addView(done, new LinearLayout.LayoutParams(-1, -2));
            StudySheet.addHairline(pane.body, 2, 10);
        }

        // 2. Borderless Editorial Underline Inputs directly on porcelain sheet
        ForestUi.add(pane.body, StudySheet.section(ctx(), "学习手记（选填）"), task != null ? 0 : 8);
        EditText input = new EditText(ctx());
        input.setId(R.id.study_note_input);
        input.setHint("留一句今天的收获或卡点");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(120) });
        input.setMinLines(1);
        StudySheet.styleUnderlineField(input);
        ForestUi.add(pane.body, input, 2);

        ForestUi.add(pane.body, StudySheet.section(ctx(), "下次从哪继续（选填）"), 14);
        EditText next = new EditText(ctx());
        next.setSingleLine(true);
        next.setInputType(InputType.TYPE_CLASS_TEXT);
        next.setHint("例如 接着做第 5–8 题");
        next.setContentDescription("下次从哪继续（选填）");
        next.setFilters(new InputFilter[] { new InputFilter.LengthFilter(120) });
        StudySheet.styleUnderlineField(next);
        ForestUi.add(pane.body, next, 2);

        LinearLayout metaBar = row(ctx());
        TextView waterHint = text(ctx(), "满 25 分钟自动累计水滴，未满也会留存", 12, MUTED);
        metaBar.addView(waterHint, new LinearLayout.LayoutParams(0, -2, 1));
        TextView discardLink = StudySheet.link(ctx(), "放弃本次", 0xff9b4c38,
            () -> confirmDiscard(sheet));
        discardLink.setTextSize(12);
        discardLink.setMinimumHeight(dp(ctx(), 32));
        discardLink.setPadding(dp(ctx(), 8), dp(ctx(), 2), dp(ctx(), 2), dp(ctx(), 2));
        metaBar.addView(discardLink);
        ForestUi.add(pane.body, metaBar, 12);

        final MaterialCheckBox doneBox = done;
        pane.primary("保存学习", () -> perform(() -> {
            if (store.notebook().running() || !sessionId.equals(store.notebook().sessionId())) {
                sheet.dismiss();
                render();
                toast("计时状态已变化，请重新结束这段学习");
                return;
            }
            boolean taskDone = doneBox != null && doneBox.isChecked();
            StudyNotebook.Completion reward = store.finish(input.getText().toString(), subject[0].id,
                duration[0] == elapsed ? 0 : duration[0], taskDone, StudyWorkDialogs.value(next));
            render();
            StudySheet.Pane result = sheetPane("学习已保存", null, 0);
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
        StudySheet.Pane pane = sheetPane("放弃这段学习？", "放弃后，本次计时将被清除。", 0);
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
        StudySheet.addHairline(receipt, 0, 14);
        LinearLayout summary = row(ctx());
        TextView subject = text(ctx(), reward.record.subject.title, 13, WOOD);
        summary.addView(subject, new LinearLayout.LayoutParams(0, -2, 1));
        TextView stamp = text(ctx(), "✓ 已收录", 11, GREEN);
        stamp.setPadding(dp(ctx(), 10), dp(ctx(), 4), dp(ctx(), 10), dp(ctx(), 4));
        stamp.setBackground(rounded(ctx(), 0xffeef4ea, 0, 999));
        summary.addView(stamp);
        ForestMotion.stamp(stamp);
        receipt.addView(summary);
        TextView dur = heading(ctx(), StudyFormat.duration(reward.record.durationMillis), 30);
        dur.setFontFeatureSettings("tnum");
        ForestUi.add(receipt, dur, 10);
        StudySheet.addHairline(receipt, 14, 14);
        LinearLayout rewards = row(ctx());
        rewards.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView drop = new android.widget.ImageView(ctx());
        drop.setImageResource(R.drawable.ic_study_drop);
        rewards.addView(drop, new LinearLayout.LayoutParams(dp(ctx(), 22), dp(ctx(), 22)));
        TextView earned = text(
            ctx(),
            "  +" + reward.water + " 水滴" + (reward.seeds > 0 ? "    +1 种子" : ""),
            16,
            DEEP
        );
        earned.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        earned.setFontFeatureSettings("tnum");
        rewards.addView(earned);
        receipt.addView(rewards);
        ForestUi.add(
            receipt,
            note(
                reward.water == 0
                    ? "时间已积攒，累计满 25 分钟就能收到水滴。"
                    : "把水滴带去林地，给小树一点滋养吧。"
            ),
            10
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

        // Top bar right side: title only, aligned parallel to the 56dp return door
        setBarTitle("学习足迹");

        // Editorial Hairline Plan Header (Proposal 1): clean serif title-dropdown + porcelain outline action
        LinearLayout statsActions = row(ctx());
        statsActions.setGravity(Gravity.CENTER_VERTICAL);
        statsActions.setPadding(0, dp(ctx(), 2), 0, dp(ctx(), 10));
        statsActions.setBackground(bottomHairlineBg(0x1f5c4938));

        LinearLayout scopeWrap = row(ctx());
        scopeWrap.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        scopeWrap.setMinimumHeight(dp(ctx(), 40));
        scopeWrap.setPadding(0, dp(ctx(), 4), dp(ctx(), 8), dp(ctx(), 4));
        scopeWrap.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false));
        scopeWrap.setOnClickListener(v -> filterPlans());
        ForestMotion.bind(scopeWrap);

        View scopeDot = new View(ctx());
        scopeDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scopeDot.setBackground(rounded(ctx(), GREEN, 0, 99));
        LinearLayout.LayoutParams scopeDotLp = new LinearLayout.LayoutParams(dp(ctx(), 7.5f), dp(ctx(), 7.5f));
        scopeDotLp.setMarginEnd(dp(ctx(), 8));
        scopeWrap.addView(scopeDot, scopeDotLp);

        TextView scopeButton = StudySheet.link(
            ctx(),
            (scope == null ? "所有计划" : scope.title + (scope.archived == 0 ? "" : " · 已归档")) + " ▾",
            0xff243624,
            this::filterPlans
        );
        scopeButton.setTextSize(19);
        scopeButton.setTypeface(Typeface.create("serif", Typeface.BOLD));
        scopeButton.setSingleLine(true);
        scopeButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
        scopeButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        scopeButton.setPadding(0, 0, 0, 0);
        scopeButton.setBackground(null);
        scopeWrap.addView(scopeButton, new LinearLayout.LayoutParams(-2, -2));
        statsActions.addView(scopeWrap, new LinearLayout.LayoutParams(0, -2, 1));

        if (initialForRecord != null) {
            TextView manualBtn = StudySheet.link(ctx(), "补记学习", DEEP, () -> {
                StudySheet sheet = newSheet();
                workDialogs(sheet).recordForm(initialForRecord, null, () -> {
                    sheet.dismiss();
                    render();
                    toast("已补记学习");
                });
            });
            manualBtn.setTextSize(12.5f);
            manualBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            manualBtn.setGravity(Gravity.CENTER);
            manualBtn.setMinimumHeight(dp(ctx(), 36));
            manualBtn.setPadding(dp(ctx(), 13), dp(ctx(), 6), dp(ctx(), 13), dp(ctx(), 6));
            manualBtn.setCompoundDrawablesRelative(tintedIcon(R.drawable.ic_editorial_pen, 14, DEEP), null, null, null);
            manualBtn.setCompoundDrawablePadding(dp(ctx(), 5));
            manualBtn.setBackground(ripple(ctx(), rounded(ctx(), 0xfffffefa, 0x38315e3a, 999), false));
            LinearLayout.LayoutParams manualLp = new LinearLayout.LayoutParams(-2, -2);
            manualLp.setMarginStart(dp(ctx(), 10));
            statsActions.addView(manualBtn, manualLp);
        }
        ForestUi.add(content, statsActions, 4);

        int[] periods = {1, 7, 30, 0};
        LinearLayout periodSelector = editorialPeriodTabs(
            new String[] {"今天", "近7天", "近30天", "全部"},
            new int[] {R.id.study_date_today, R.id.study_date_week, R.id.study_date_month, View.NO_ID},
            days == 0 ? 3 : days == 30 ? 2 : days == 7 ? 1 : 0,
            index -> {
                if (days == periods[index]) return;
                days = periods[index];
                render();
            }
        );
        ForestUi.add(content, periodSelector, 2);
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
        int streak = computeStudyStreak(allScoped);
        long avgDaily = dates.isEmpty() ? 0L : total / dates.size();
        ForestUi.add(content, durationSummary(range, total, records.size(), dates.size(), avgDaily, streak), 14);
        LinearLayout card = statsPanel(LIGHT, LINE);
        Set<StudySubject> activeSubjects = new HashSet<>();
        for (StudyNotebook.Record r : records) activeSubjects.add(r.subject);
        String periodTag = days == 1 ? "今天" : days == 7 ? "近7天" : days == 30 ? "近30天" : "全部";
        LinearLayout chartHeader = row(ctx());
        chartHeader.addView(
            heading(ctx(), chart == StudyChartView.Kind.TREND && !records.isEmpty()
                ? "学习趋势" : "科目分布", 19),
            new LinearLayout.LayoutParams(0, -2, 1)
        );
        TextView chartMeta = text(
            ctx(),
            records.isEmpty() ? periodTag : (periodTag + " · " + activeSubjects.size() + "门科目"),
            12,
            MUTED
        );
        chartMeta.setFontFeatureSettings("tnum");
        chartHeader.addView(chartMeta);
        card.addView(chartHeader);
        if (!records.isEmpty()) {
            ForestUi.add(card, editorialChartTabs(
                new String[] {"环形占比", "科目时长", "每日趋势"},
                new int[] {R.id.study_chart_ring, R.id.study_chart_bars, R.id.study_chart_trend},
                chart.ordinal(),
                index -> {
                    StudyChartView.Kind type = StudyChartView.Kind.values()[index];
                    if (chart == type) return;
                    chart = type;
                    render();
                }
            ), 8);
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
        ForestUi.add(content, card, 14);
        ForestUi.add(content, heatmapCard(allScoped, streak), 14);
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
        int resolvedFill = color == LIGHT ? 0xfffffefa : color;
        int resolvedStroke = border == LINE ? 0x1c5c4938 : border;
        panel.setBackground(rounded(ctx(), resolvedFill, resolvedStroke, 20));
        panel.setElevation(dp(ctx(), 1));
        panel.setPadding(dp(ctx(), 16), dp(ctx(), 15), dp(ctx(), 16), dp(ctx(), 14));
        return panel;
    }

    private LinearLayout editorialChartTabs(String[] labels, int[] ids, int selected, IntConsumer change) {
        LinearLayout strip = row(ctx());
        strip.setGravity(Gravity.BOTTOM);
        android.graphics.drawable.GradientDrawable hairline = new android.graphics.drawable.GradientDrawable();
        hairline.setColor(0x1f5c4938);
        android.graphics.drawable.LayerDrawable stripBg = new android.graphics.drawable.LayerDrawable(
            new android.graphics.drawable.Drawable[] { hairline }
        );
        stripBg.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        stripBg.setLayerHeight(0, Math.max(1, dp(ctx(), 1)));
        strip.setBackground(stripBg);

        float density = ctx().getResources().getDisplayMetrics().density;
        int indicatorHeight = Math.max(3, Math.round(2.5f * density));
        int tabGap = dp(ctx(), 22);

        for (int i = 0; i < labels.length; i++) {
            int index = i;
            boolean isSelected = i == selected;
            LinearLayout tab = column(ctx());
            tab.setId(ids[i]);
            tab.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            tab.setMinimumHeight(dp(ctx(), 42));
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setSelected(isSelected);
            tab.setContentDescription(labels[i]);
            tab.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 8), false));

            TextView label = text(ctx(), labels[i], 13, isSelected ? 0xff283f2c : 0xff736c5d);
            label.setTypeface(Typeface.create(
                isSelected ? "sans-serif-medium" : "sans-serif",
                isSelected ? Typeface.BOLD : Typeface.NORMAL
            ));
            label.setPadding(dp(ctx(), 2), dp(ctx(), 8), dp(ctx(), 2), dp(ctx(), 9));
            tab.addView(label, new LinearLayout.LayoutParams(-2, -2));

            View indicator = new View(ctx());
            indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            indicator.setBackground(rounded(ctx(), isSelected ? 0xff315e3a : Color.TRANSPARENT, 0, 99));
            tab.addView(indicator, new LinearLayout.LayoutParams(-1, indicatorHeight));

            tab.setOnClickListener(v -> change.accept(index));
            ForestMotion.bind(tab);

            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) tabLp.setMarginStart(tabGap);
            strip.addView(tab, tabLp);
        }
        return strip;
    }

    private LinearLayout editorialPeriodTabs(String[] labels, int[] ids, int selected, IntConsumer change) {
        LinearLayout strip = row(ctx());
        strip.setGravity(Gravity.BOTTOM);
        float density = ctx().getResources().getDisplayMetrics().density;
        int indicatorHeight = Math.max(3, Math.round(2.5f * density));

        for (int i = 0; i < labels.length; i++) {
            int index = i;
            boolean isSelected = i == selected;
            LinearLayout tab = column(ctx());
            tab.setId(ids[i]);
            tab.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            tab.setMinimumHeight(dp(ctx(), 42));
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setSelected(isSelected);
            tab.setContentDescription(labels[i]);
            tab.setBackground(ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 8), false));

            TextView label = text(ctx(), labels[i], 13, isSelected ? 0xff274c2f : 0xff7a7364);
            label.setTypeface(Typeface.create(
                isSelected ? "sans-serif-medium" : "sans-serif",
                isSelected ? Typeface.BOLD : Typeface.NORMAL
            ));
            label.setPadding(dp(ctx(), 4), dp(ctx(), 9), dp(ctx(), 4), dp(ctx(), 8));
            tab.addView(label, new LinearLayout.LayoutParams(-2, -2));

            View indicator = new View(ctx());
            indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            indicator.setBackground(rounded(ctx(), isSelected ? 0xff3b613f : Color.TRANSPARENT, 0, 99));
            tab.addView(indicator, new LinearLayout.LayoutParams(dp(ctx(), 26), indicatorHeight));

            tab.setOnClickListener(v -> change.accept(index));
            ForestMotion.bind(tab);
            strip.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return strip;
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

    private int computeStudyStreak(List<StudyNotebook.Record> records) {
        if (records == null || records.isEmpty()) return 0;
        Set<String> activeDays = new HashSet<>();
        for (StudyNotebook.Record r : records) activeDays.add(StudyNotebook.dayKey(r.completedAt));
        Calendar cursor = StudyFormat.day(StudyNotebook.dayKey(System.currentTimeMillis()));
        if (!activeDays.contains(StudyNotebook.dayKey(cursor.getTimeInMillis()))) {
            cursor.add(Calendar.DATE, -1);
            if (!activeDays.contains(StudyNotebook.dayKey(cursor.getTimeInMillis()))) return 0;
        }
        int streak = 0;
        while (activeDays.contains(StudyNotebook.dayKey(cursor.getTimeInMillis()))) {
            streak++;
            cursor.add(Calendar.DATE, -1);
        }
        return streak;
    }

    private LinearLayout heatmapCard(List<StudyNotebook.Record> allScoped, int streak) {
        LinearLayout card = statsPanel(LIGHT, LINE);
        LinearLayout top = row(ctx());
        top.addView(heading(ctx(), "专注热力图", 18), new LinearLayout.LayoutParams(0, -2, 1));
        TextView detailLabel = text(ctx(), streak > 0 ? "已连续打卡 " + streak + " 天" : "点按格子查看当天时长", 11, DEEP);
        top.addView(detailLabel);
        card.addView(top);
        StudyHeatmapView heatmap = new StudyHeatmapView(ctx());
        heatmap.setData(allScoped, detailLabel::setText);
        ForestUi.add(card, heatmap, 10);
        return card;
    }

    private LinearLayout durationSummary(String range, long total, int sessions, int activeDays, long avgDaily, int streak) {
        LinearLayout card = statsPanel(0xfff1f6e9, 0x24456444);
        card.setElevation(dp(ctx(), 2));
        LinearLayout top = row(ctx());
        top.addView(text(ctx(), "累计专注", 13, DEEP), new LinearLayout.LayoutParams(0, -2, 1));
        ImageView icon = new ImageView(ctx());
        icon.setImageResource(R.drawable.ic_study_sprout);
        icon.setImageTintList(ColorStateList.valueOf(DEEP));
        icon.setPadding(dp(ctx(), 7), dp(ctx(), 7), dp(ctx(), 7), dp(ctx(), 7));
        icon.setBackground(rounded(ctx(), 0xfffffefa, 0x18456444, 12));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(icon, new LinearLayout.LayoutParams(dp(ctx(), 34), dp(ctx(), 34)));
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
        line.setBackgroundColor(0x22456444);
        LinearLayout.LayoutParams divider = new LinearLayout.LayoutParams(-1, dp(ctx(), 1));
        divider.topMargin = dp(ctx(), 12);
        card.addView(line, divider);
        LinearLayout details = row(ctx());
        details.setGravity(Gravity.TOP);
        stat(details, "学习次数", sessions + " 次", 1);
        stat(details, "学习天数", activeDays + " 天", 1);
        stat(details, "日均专注", avgDaily > 0 ? StudyFormat.duration(avgDaily) : "0分钟", 1.25f);
        stat(details, "连续打卡", streak + " 天", 1);
        ForestUi.add(card, details, 11);
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
        TextView number = heading(ctx(), value, 15);
        number.setMaxLines(2);
        ForestUi.add(column, number, 5);
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
        StudySheet.Pane pane = sheetPane(record.subject.title, store.notebook().plan(record.planId).title, 0);
        StudySheet.addHairline(pane.body, 0, 14);
        TextView durHeading = heading(ctx(), StudyFormat.duration(record.durationMillis), 30);
        durHeading.setFontFeatureSettings("tnum");
        pane.body.addView(durHeading);
        ForestUi.add(pane.body, note(new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)
            .format(new java.util.Date(record.completedAt)) + recordOrigin(record)), 6);
        StudySheet.addHairline(pane.body, 14, 0);
        StudyWork.Task task = store.notebook().task(record.taskId);
        if (task != null) ForestUi.add(pane.body, note("学习事项 · " + task.title), 12);
        if (!record.note.isEmpty()) {
            ForestUi.add(pane.body, StudySheet.section(ctx(), "学习手记"), 16);
            TextView entry = text(ctx(), record.note, 14, DEEP);
            entry.setLineSpacing(dp(ctx(), 5), 1);
            ForestUi.add(pane.body, entry, 6);
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

        // Top bar right side: title only, aligned parallel to the 56dp return door
        setBarTitle("我的小林地");

        LinearLayout content = content();
        body.addView(content);
        LinearLayout introRow = row(ctx());
        introRow.setGravity(Gravity.CENTER_VERTICAL);
        introRow.addView(note("你在认真生活，森林就在悄悄长大。"), new LinearLayout.LayoutParams(0, -2, 1));
        wallet = text(ctx(), "水滴 " + n.water() + " · 种子 " + n.seeds(), 13, 0xff2e4f2f);
        wallet.setId(R.id.study_wallet);
        wallet.setFontFeatureSettings("tnum");
        wallet.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        wallet.setPadding(dp(ctx(), 12), dp(ctx(), 6), dp(ctx(), 12), dp(ctx(), 6));
        wallet.setBackground(rounded(ctx(), 0xffe9efe3, 0xffbfd0b9, 999));
        LinearLayout.LayoutParams walletLp = new LinearLayout.LayoutParams(-2, -2);
        walletLp.setMarginStart(dp(ctx(), 10));
        introRow.addView(wallet, walletLp);
        ForestUi.add(content, introRow, 8);
        garden = new StudyGardenView(ctx());
        garden.setId(R.id.study_garden_view);
        StudyPlan currentGardenPlan = n.currentPlan();
        garden.setCompanion(partnerDrawable(currentGardenPlan == null ? StudyPlan.Partner.SQUIRREL : currentGardenPlan.partner));
        garden.setState(n.planted(), n.growth());
        garden.setOnClickListener(v -> {
            if (garden.busy()) return;
            if (store.notebook().planted()) {
                garden.greet();
                toast("叶子轻轻晃了晃：今天也一起加油。摸摸不消耗水滴。");
            } else toast("这片小小的空地，正等你种下一颗种子。");
        });
        LinearLayout.LayoutParams sceneParams = new LinearLayout.LayoutParams(-1, dp(ctx(), 248));
        sceneParams.topMargin = dp(ctx(), 12);
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
        boolean canExchangeSeed = !n.planted() && n.seeds() <= 0 && n.water() >= StudyNotebook.SEED_WATER_COST;
        String actionLabel = n.planted()
            ? "给小树浇水 · 1水滴"
            : canPlantNow ? "种下一颗种子 · 1种子"
            : canExchangeSeed ? ("用 " + StudyNotebook.SEED_WATER_COST + " 水滴兑换种子并种下")
            : ("去专注达标领种子（每日目标 " + n.dailyGoal() + " 分钟）");
        waterButton = button(ctx(), actionLabel, n.planted() || canPlantNow || canExchangeSeed);
        waterButton.setId(R.id.study_water_button);
        waterButton.setOnClickListener(v -> {
            if (!n.planted() && n.seeds() <= 0) {
                if (store.notebook().water() >= StudyNotebook.SEED_WATER_COST) {
                    perform(() -> {
                        store.update(book -> {
                            book.exchangeWaterForSeed();
                            return null;
                        });
                        care();
                    });
                } else {
                    open(AppDestination.STUDY_FOCUS);
                }
            } else {
                perform(this::care);
            }
        });
        ForestUi.add(content, waterButton, 14);
        String gentleCopy;
        if (n.planted()) {
            gentleCopy = "休息不会枯萎，小树会一直等你。";
        } else if (canExchangeSeed) {
            gentleCopy = "当前已有 " + n.water() + " 滴水，可直接兑换 1 颗种子开始种树。";
        } else if (n.seeds() <= 0) {
            gentleCopy = "达成每日 " + n.dailyGoal() + " 分钟目标，或累计满 " + StudyNotebook.SEED_WATER_COST + " 滴水即可获得新种子。";
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
        collection.setPadding(dp(ctx(), 14), dp(ctx(), 12), dp(ctx(), 14), dp(ctx(), 12));
        collection.setBackground(rounded(ctx(), 0xfffffdfa, 0x1e5c4938, 16));
        collection.setElevation(dp(ctx(), 1));
        for (int i = 0; i < Math.min(3, n.trees()); i++) {
            LinearLayout.LayoutParams treeLp = new LinearLayout.LayoutParams(dp(ctx(), 42), dp(ctx(), 48));
            treeLp.setMarginEnd(dp(ctx(), 6));
            collection.addView(image(ctx(), R.drawable.savings_tree_stage_3, "已长成的小树"), treeLp);
        }
        TextView collected = text(
            ctx(),
            n.trees() == 0 ? "森林的故事，等你写下。" : "每一棵，都有你的时间。",
            12,
            DEEP
        );
        collection.addView(collected, new LinearLayout.LayoutParams(0, -2, 1));
        ForestUi.add(content, collection, 12);
        section(content, "森林的小小约定", "约 1~2 周养成一棵");
        String goal = n.currentPlan() == null ? "达到任一计划的每日学习目标"
            : "每天达到 " + n.dailyGoal() + " 分钟目标";
        ForestUi.add(
            content,
            note(
                "每累计专注 25 分钟  →  +1 水滴\n跨次累计，短短一段也不会浪费。\n\n" +
                    goal + "  →  +1 种子\n空地无种子时也可用 " + StudyNotebook.SEED_WATER_COST + " 滴水兑换 1 颗。\n\n浇满 " +
                    StudyGrowth.REQUIRED_WATER + " 次水（约 42 小时，每天 5 小时约 8~9 天）  →  收藏小树\n小树长大后，可以再种下新的期待。"
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
            "水滴 " + store.notebook().water() + " · 种子 " + store.notebook().seeds()
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
        if (statusPulseAnimator != null) {
            statusPulseAnimator.cancel();
            statusPulseAnimator = null;
        }
        if (getActivity() != null) {
            getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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
        if (statusPulseAnimator != null) {
            statusPulseAnimator.cancel();
            statusPulseAnimator = null;
        }
        if (garden != null) garden.setActive(false);
        topBarTitle = null;
        body = null;
        scroll = null;
        clock = null;
        today = null;
        dialGoalCaption = null;
        dialRightCaption = null;
        activeStatusText = null;
        focusStatusPill = null;
        focusStatusDot = null;
        focusStatusLabel = null;
        focusGentleNote = null;
        startPauseButton = null;
        garden = null;
        growthProgress = null;
        dial = null;
        wallet = null;
        waterButton = null;
        super.onDestroyView();
    }
}

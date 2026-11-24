package com.example.pkmapp.home;

import static com.example.pkmapp.forest.ForestUi.*;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.core.datastore.AppPreferences;
import com.example.pkmapp.forest.ForestDialogBuilder;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.ForestSceneView;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.life.LifeSharedViewModel;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.MoneyParser;
import com.example.pkmapp.study.StudyFormat;
import com.example.pkmapp.study.StudyNotebook;
import com.example.pkmapp.study.StudySharedViewModel;
import com.example.pkmapp.study.StudyStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import java.util.ArrayList;
import java.util.List;

public final class HomeFragment extends Fragment {

    private static final String COTTAGE_STUDY = "STUDY";
    private static final String COTTAGE_LIFE = "LIFE";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ObjectAnimator> breathing = new ArrayList<>();
    private StudyStore store;
    private LifeSharedViewModel lifeViewModel;
    private StudySharedViewModel studyViewModel;
    private TextView resume;
    private TextView summaryFocusValue;
    private TextView summaryTodoValue;
    private TextView summaryExpenseValue;
    private MaterialCheckBox rememberSwitch;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateResume();
            handler.postDelayed(this, 1000);
        }
    };

    public static boolean isRememberLastCottage(@NonNull Context context) {
        return AppPreferences.getInstance(context).getCurrent().getRememberLastCottage();
    }

    public static void setRememberLastCottage(@NonNull Context context, boolean enabled) {
        AppPreferences.getInstance(context).setRememberLastCottage(enabled);
    }

    public static void recordLastCottage(@NonNull Context context, @NonNull AppDestination destination) {
        if (destination == AppDestination.HOME) return;
        String value = destination.isStudy() ? COTTAGE_STUDY : COTTAGE_LIFE;
        AppPreferences.getInstance(context).setLastCottage(value);
    }

    @NonNull
    public static AppDestination resolveStartupDestination(@NonNull Context context) {
        if (!isRememberLastCottage(context)) {
            return AppDestination.HOME;
        }
        String last = AppPreferences.getInstance(context).getCurrent().getLastCottage();
        if (COTTAGE_STUDY.equals(last)) {
            return AppDestination.STUDY_FOCUS;
        }
        if (COTTAGE_LIFE.equals(last)) {
            return AppDestination.DETAILS;
        }
        return AppDestination.HOME;
    }

    @NonNull
    public static String lastCottageLabel(@NonNull Context context) {
        String last = AppPreferences.getInstance(context).getCurrent().getLastCottage();
        if (COTTAGE_STUDY.equals(last)) return "学习小屋";
        if (COTTAGE_LIFE.equals(last)) return "生活小屋";
        return "上次进入的小屋";
    }

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        store = StudyStore.get(requireContext());
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(0, bars.top, 0, bars.bottom + dp(requireContext(), 12));
            return insets;
        });
        androidx.core.view.WindowInsetsCompat initialInsets =
            androidx.core.view.ViewCompat.getRootWindowInsets(
                requireActivity().getWindow().getDecorView()
            );
        if (initialInsets != null) {
            androidx.core.graphics.Insets bars = initialInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            );
            scroll.setPadding(0, bars.top, 0, bars.bottom + dp(requireContext(), 12));
        }
        LinearLayout content = column(requireContext());
        scroll.addView(content);

        LinearLayout header = row(requireContext());
        header.setPadding(
            dp(requireContext(), 20),
            dp(requireContext(), 10),
            dp(requireContext(), 20),
            dp(requireContext(), 8)
        );
        ImageView leaf = new ImageView(requireContext());
        leaf.setImageResource(R.drawable.ic_leaf);
        leaf.setImageTintList(ColorStateList.valueOf(DEEP));
        header.addView(
            leaf,
            new LinearLayout.LayoutParams(dp(requireContext(), 21), dp(requireContext(), 21))
        );
        TextView brand = heading(requireContext(), "芽叶记", 20);
        brand.setPadding(dp(requireContext(), 7), 0, 0, 0);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(requireContext(), "学习 · 记账", 11, MUTED);
        header.addView(badge);
        content.addView(header);

        FrameLayout scene = new FrameLayout(requireContext());
        scene.addView(
            new ForestSceneView(requireContext(), R.drawable.crayon_cottages, .52f, false),
            new FrameLayout.LayoutParams(-1, -1)
        );
        LinearLayout greeting = column(requireContext());
        greeting.addView(text(requireContext(), "今天，想和谁一起出发？", 11, WOOD));
        TextView title = heading(requireContext(), "欢迎回到\n你的森林小窝。", 28);
        title.setLineSpacing(dp(requireContext(), 4), 1);
        ForestUi.add(greeting, title, 8);
        FrameLayout.LayoutParams copy = new FrameLayout.LayoutParams(-1, -2);
        copy.setMargins(
            dp(requireContext(), 22),
            dp(requireContext(), 10),
            dp(requireContext(), 14),
            0
        );
        scene.addView(greeting, copy);
        LinearLayout doors = row(requireContext());
        doors.setPadding(dp(requireContext(), 16), 0, dp(requireContext(), 16), 0);
        doors.addView(
            door(R.id.home_door_study, "进入学习小屋", AppDestination.STUDY_FOCUS),
            new LinearLayout.LayoutParams(0, -1, 1)
        );
        doors.addView(
            new View(requireContext()),
            new LinearLayout.LayoutParams(dp(requireContext(), 24), 1)
        );
        doors.addView(
            door(R.id.home_door_life, "进入生活小屋", AppDestination.DETAILS),
            new LinearLayout.LayoutParams(0, -1, 1)
        );
        FrameLayout.LayoutParams doorParams = new FrameLayout.LayoutParams(
            -1,
            dp(requireContext(), 148),
            Gravity.BOTTOM
        );
        doorParams.bottomMargin = dp(requireContext(), 8);
        scene.addView(doors, doorParams);
        content.addView(scene, new LinearLayout.LayoutParams(-1, dp(requireContext(), 356)));

        LinearLayout partners = row(requireContext());
        partners.setGravity(Gravity.TOP);
        partners.setPadding(
            dp(requireContext(), 12),
            dp(requireContext(), 16),
            dp(requireContext(), 12),
            0
        );
        partners.addView(companion(true), new LinearLayout.LayoutParams(0, -2, 1));
        partners.addView(
            new View(requireContext()),
            new LinearLayout.LayoutParams(dp(requireContext(), 10), 1)
        );
        partners.addView(companion(false), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(partners);

        resume = text(requireContext(), "", 12, DEEP);
        resume.setId(R.id.home_resume_study);
        resume.setGravity(Gravity.CENTER);
        resume.setPadding(
            dp(requireContext(), 12),
            dp(requireContext(), 12),
            dp(requireContext(), 12),
            dp(requireContext(), 12)
        );
        resume.setBackground(rounded(requireContext(), 0xffe9eedc, 0xffcdd5b8, 12));
        resume.setOnClickListener(v -> open(AppDestination.STUDY_FOCUS));
        ForestMotion.bind(resume);
        LinearLayout.LayoutParams resumeParams = new LinearLayout.LayoutParams(-1, -2);
        resumeParams.setMargins(
            dp(requireContext(), 18),
            dp(requireContext(), 14),
            dp(requireContext(), 18),
            0
        );
        content.addView(resume, resumeParams);

        addTodayOverview(content);
        addRememberCottageToggle(content);
        ForestUi.spacer(content, 20);
        return scroll;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        lifeViewModel = LifeSharedViewModel.get(requireActivity());
        studyViewModel = StudySharedViewModel.get(requireActivity());
        studyViewModel.syncFromStores(requireContext());
        lifeViewModel.observe(getViewLifecycleOwner(), snapshot -> refreshOverview());
        studyViewModel.observeOverview(getViewLifecycleOwner(), this::refreshOverview);
    }

    private void addTodayOverview(LinearLayout parent) {
        LinearLayout card = column(requireContext());
        card.setPadding(
            dp(requireContext(), 14),
            dp(requireContext(), 12),
            dp(requireContext(), 14),
            dp(requireContext(), 12)
        );
        card.setBackground(rounded(requireContext(), 0xfffaf6ec, 0xffdfd8c4, 16));

        LinearLayout head = row(requireContext());
        TextView label = text(requireContext(), "今日速览", 12, DEEP);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(text(requireContext(), "点击直达对应页面", 10, MUTED));
        card.addView(head);

        LinearLayout metrics = row(requireContext());
        metrics.setPadding(0, dp(requireContext(), 10), 0, 0);

        summaryFocusValue = addMetricColumn(metrics, "今日专注", "0分钟", DEEP, AppDestination.STUDY_FOCUS);
        addMetricDivider(metrics);
        summaryTodoValue = addMetricColumn(metrics, "待办事项", "0 项", DEEP, AppDestination.STUDY_TODO);
        addMetricDivider(metrics);
        summaryExpenseValue = addMetricColumn(metrics, "本月支出", "¥0.00", WOOD, AppDestination.DETAILS);

        card.addView(metrics);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(
            dp(requireContext(), 18),
            dp(requireContext(), 14),
            dp(requireContext(), 18),
            0
        );
        parent.addView(card, cardLp);
    }

    private TextView addMetricColumn(
        LinearLayout parent,
        String label,
        String initialValue,
        int valueColor,
        AppDestination destination
    ) {
        LinearLayout col = column(requireContext());
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(requireContext(), 4), dp(requireContext(), 6), dp(requireContext(), 4), dp(requireContext(), 6));
        col.setBackground(ripple(requireContext(), rounded(requireContext(), 0, 0, 10), false));
        col.setOnClickListener(v -> open(destination));
        ForestMotion.bind(col);

        TextView value = heading(requireContext(), initialValue, 15);
        value.setTextColor(valueColor);
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        col.addView(value);

        TextView caption = text(requireContext(), label, 10, MUTED);
        caption.setGravity(Gravity.CENTER);
        ForestUi.add(col, caption, 4);

        parent.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        return value;
    }

    private void addMetricDivider(LinearLayout parent) {
        View divider = new View(requireContext());
        divider.setBackgroundColor(0x265c4938);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(requireContext(), 1), dp(requireContext(), 28));
        lp.gravity = Gravity.CENTER_VERTICAL;
        parent.addView(divider, lp);
    }

    private void addRememberCottageToggle(LinearLayout parent) {
        boolean enabled = isRememberLastCottage(requireContext());
        rememberSwitch = ForestUi.switchWithSubtitle(
            requireContext(),
            "记住上次进入的小屋",
            rememberSubtitle(enabled)
        );
        rememberSwitch.setChecked(enabled);
        rememberSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setRememberLastCottage(requireContext(), isChecked);
            ForestUi.updateSwitchSubtitle(
                rememberSwitch,
                rememberSubtitle(isChecked)
            );
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(
            dp(requireContext(), 18),
            dp(requireContext(), 10),
            dp(requireContext(), 18),
            0
        );
        parent.addView(rememberSwitch, lp);
    }

    private String rememberSubtitle(boolean enabled) {
        if (enabled) {
            return "已开启 · 下次启动直达「" + lastCottageLabel(requireContext()) + "」";
        }
        return "已关闭 · 每次打开先经过森林门厅";
    }

    private void refreshOverview() {
        if (summaryFocusValue == null || summaryTodoValue == null || summaryExpenseValue == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String todayKey = StudyNotebook.dayKey(now);
        StudyNotebook notebook = store.notebook();
        long focusMillis = 0L;
        for (StudyNotebook.Record record : notebook.records()) {
            if (todayKey.equals(StudyNotebook.dayKey(record.completedAt))) {
                focusMillis += record.durationMillis;
            }
        }
        if (notebook.hasSession()) {
            focusMillis += store.elapsedMillis();
        }
        summaryFocusValue.setText(focusMillis > 0L ? StudyFormat.duration(focusMillis) : "0分钟");

        int pendingCount = studyViewModel == null
            ? 0 : studyViewModel.getTodoState().getValue().getPendingCount();
        summaryTodoValue.setText(pendingCount + " 项");

        long monthExpense = lifeViewModel == null
            ? 0L : lifeViewModel.getCurrentSnapshot().getCurrentMonthTotals().getExpenseInCents();
        summaryExpenseValue.setText(MoneyParser.formatCents(monthExpense));

        if (rememberSwitch != null) {
            boolean enabled = isRememberLastCottage(requireContext());
            if (rememberSwitch.isChecked() != enabled) {
                rememberSwitch.setChecked(enabled);
            }
            ForestUi.updateSwitchSubtitle(
                rememberSwitch,
                rememberSubtitle(enabled)
            );
        }
    }

    private View door(int id, String description, AppDestination destination) {
        MaterialButton button = ForestUi.button(requireContext(), "", false);
        button.setId(id);
        button.setContentDescription(description);
        button.setBackgroundTintList(ColorStateList.valueOf(0));
        button.setStrokeWidth(0);
        button.setOnClickListener(v -> open(destination));
        return button;
    }

    private View companion(boolean study) {
        LinearLayout choice = column(requireContext());
        choice.setGravity(Gravity.CENTER_HORIZONTAL);
        choice.setId(study ? R.id.home_enter_study : R.id.home_enter_life);
        choice.setContentDescription(
            study ? "和学习伙伴松栗进入学习小屋" : "和生活伙伴小芽进入生活小屋"
        );
        choice.setFocusable(true);
        choice.setClickable(true);
        choice.setOnClickListener(v ->
            open(study ? AppDestination.STUDY_FOCUS : AppDestination.DETAILS)
        );
        ForestMotion.bind(choice);
        ImageView art = image(
            requireContext(),
            study ? R.drawable.crayon_study_companion : R.drawable.turtwig_carrying_acorn_jar,
            null
        );
        choice.addView(art, new LinearLayout.LayoutParams(-1, dp(requireContext(), 148)));
        ObjectAnimator animation = ObjectAnimator.ofFloat(
            art,
            View.TRANSLATION_Y,
            0,
            -dp(requireContext(), 2),
            0
        );
        animation.setDuration(study ? 4000 : 4300);
        animation.setRepeatCount(ValueAnimator.INFINITE);
        breathing.add(animation);
        TextView role = text(
            requireContext(),
            study ? "学习伙伴 · 松栗" : "生活伙伴 · 小芽",
            10,
            study ? 0xff839069 : 0xffaa9169
        );
        role.setGravity(Gravity.CENTER);
        ForestUi.add(choice, role, 6);
        TextView caption = heading(
            requireContext(),
            study ? "把努力，慢慢种下" : "把日子，好好收着",
            14
        );
        if (!study) caption.setTextColor(WOOD);
        caption.setGravity(Gravity.CENTER);
        ForestUi.add(choice, caption, 6);
        TextView details = text(
            requireContext(),
            study ? "专注计时 · 待办 · 学习足迹" : "日常账本 · 攒钱 · 生活工具",
            9,
            MUTED
        );
        details.setGravity(Gravity.CENTER);
        ForestUi.add(choice, details, 5);
        TextView enter = text(
            requireContext(),
            study ? "进入学习小屋  →" : "进入生活小屋  →",
            12,
            study ? DEEP : WOOD
        );
        enter.setGravity(Gravity.CENTER);
        ForestUi.add(choice, enter, 8);
        return choice;
    }

    private void open(AppDestination destination) {
        ((MainActivity) requireActivity()).showDestination(destination);
    }

    private void updateResume() {
        if (resume == null) return;
        StudyNotebook n = store.notebook();
        resume.setVisibility(n.hasSession() ? View.VISIBLE : View.GONE);
        if (n.hasSession()) resume.setText(
            n.activeSubject().title +
                " · " +
                (n.running() ? "学习计时仍在继续" : "专注已暂停") +
                "  " +
                StudyFormat.clock(store.elapsedMillis()) +
                "  →"
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        if (studyViewModel != null) {
            studyViewModel.syncFromStores(requireContext());
        }
        if (lifeViewModel != null) {
            lifeViewModel.refresh();
        }
        refreshOverview();
        handler.removeCallbacks(tick);
        handler.post(tick);
        if (
            Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        ) for (ObjectAnimator animation : breathing) animation.start();
        if (store.takeRecoveryWarning()) new ForestDialogBuilder(requireContext())
            .setTitle("学习存档读取异常")
            .setMessage("原始存档已在本机保留副本，当前打开空白学习本。账本数据不受影响。")
            .setPositiveButton("知道了", null)
            .show();
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(tick);
        for (ObjectAnimator animation : breathing) animation.cancel();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(tick);
        for (ObjectAnimator animation : breathing) animation.cancel();
        breathing.clear();
        resume = null;
        summaryFocusValue = null;
        summaryTodoValue = null;
        summaryExpenseValue = null;
        rememberSwitch = null;
        super.onDestroyView();
    }
}

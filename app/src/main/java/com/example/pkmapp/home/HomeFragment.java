package com.example.pkmapp.home;

import static com.example.pkmapp.forest.ForestUi.*;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import com.example.pkmapp.forest.ForestDialogBuilder;
import com.example.pkmapp.forest.ForestSceneView;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.study.StudyFormat;
import com.example.pkmapp.study.StudyNotebook;
import com.example.pkmapp.study.StudyStore;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public final class HomeFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ObjectAnimator> breathing = new ArrayList<>();
    private StudyStore store;
    private TextView resume;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateResume();
            handler.postDelayed(this, 1000);
        }
    };

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
        ForestUi.paper(scroll);
        LinearLayout content = column(requireContext());
        scroll.addView(content);
        LinearLayout header = row(requireContext());
        header.setPadding(
            dp(requireContext(), 20),
            dp(requireContext(), 12),
            dp(requireContext(), 20),
            dp(requireContext(), 12)
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
        TextView badge = text(requireContext(), "你的生活集合", 10, DEEP);
        badge.setPadding(
            dp(requireContext(), 10),
            dp(requireContext(), 5),
            dp(requireContext(), 10),
            dp(requireContext(), 5)
        );
        badge.setBackground(rounded(requireContext(), 0xffe8eddb, 0xffccd5b9, 20));
        header.addView(badge);
        content.addView(header);
        FrameLayout scene = new FrameLayout(requireContext());
        scene.addView(
            new ForestSceneView(requireContext(), R.drawable.crayon_cottages, .32f, false),
            new FrameLayout.LayoutParams(-1, -1)
        );
        LinearLayout greeting = column(requireContext());
        greeting.addView(text(requireContext(), "今天，想和谁一起出发？", 11, WOOD));
        TextView title = heading(requireContext(), "欢迎回到\n你的森林小窝。", 30);
        title.setLineSpacing(dp(requireContext(), 5), 1);
        ForestUi.add(greeting, title, 10);
        FrameLayout.LayoutParams copy = new FrameLayout.LayoutParams(-1, -2);
        copy.setMargins(
            dp(requireContext(), 22),
            dp(requireContext(), 22),
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
            dp(requireContext(), 128),
            Gravity.BOTTOM
        );
        doorParams.bottomMargin = dp(requireContext(), 17);
        scene.addView(doors, doorParams);
        content.addView(scene, new LinearLayout.LayoutParams(-1, dp(requireContext(), 330)));
        LinearLayout partners = row(requireContext());
        partners.setGravity(Gravity.TOP);
        partners.setPadding(dp(requireContext(), 12), 0, dp(requireContext(), 12), 0);
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
            dp(requireContext(), 14),
            dp(requireContext(), 12),
            dp(requireContext(), 14)
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
        TextView letter = heading(
            requireContext(),
            "认真学习，也好好生活。\n每一次回来，都有伙伴在等你。",
            12
        );
        letter.setTextColor(WOOD);
        letter.setGravity(Gravity.CENTER);
        letter.setLineSpacing(dp(requireContext(), 5), 1);
        ForestUi.add(content, letter, 17);
        TextView footer = text(requireContext(), "属于你的生活集合 · 更多小屋，慢慢生长", 9, MUTED);
        footer.setGravity(Gravity.CENTER);
        ForestUi.add(content, footer, 15);
        ForestUi.spacer(content, 18);
        return scroll;
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
            study ? R.drawable.crayon_study_companion : R.drawable.balance_bulbasaur_coin,
            null
        );
        choice.addView(art, new LinearLayout.LayoutParams(-1, dp(requireContext(), 190)));
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
        choice.addView(
            text(
                requireContext(),
                study ? "学习伙伴 · 松栗" : "生活伙伴 · 小芽",
                10,
                study ? 0xff839069 : 0xffaa9169
            )
        );
        TextView caption = heading(
            requireContext(),
            study ? "把努力，慢慢种下" : "把日子，好好收着",
            14
        );
        if (!study) caption.setTextColor(WOOD);
        caption.setGravity(Gravity.CENTER);
        ForestUi.add(choice, caption, 9);
        TextView details = text(
            requireContext(),
            study ? "专注计时 · 学习足迹 · 林地" : "日常账本 · 攒钱 · 生活工具",
            9,
            MUTED
        );
        details.setGravity(Gravity.CENTER);
        ForestUi.add(choice, details, 7);
        TextView enter = text(
            requireContext(),
            study ? "进入学习小屋  →" : "进入生活小屋  →",
            12,
            study ? DEEP : WOOD
        );
        enter.setGravity(Gravity.CENTER);
        ForestUi.add(choice, enter, 12);
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
        breathing.clear();
        resume = null;
        super.onDestroyView();
    }
}

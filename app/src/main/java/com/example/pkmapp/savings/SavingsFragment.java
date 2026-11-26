package com.example.pkmapp.savings;

import static com.example.pkmapp.forest.ForestUi.dp;
import static com.example.pkmapp.forest.ForestUi.row;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.databinding.FragmentSavingsBinding;
import com.example.pkmapp.forest.ForestDialogBuilder;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.ForestSheetHelper;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.life.LifeSharedViewModel;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.MoneyParser;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.math.BigDecimal;
import java.util.List;

public final class SavingsFragment extends Fragment {
    private FragmentSavingsBinding binding;
    private LifeSharedViewModel viewModel;
    private List<SavingsGoal> renderedGoals;
    private BottomSheetDialog activeSheet;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSavingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = LifeSharedViewModel.get(requireActivity());
        binding.pageBackButton.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.savingsAddGoalButton.setOnClickListener(v -> showGoalEditorSheet(null));
        viewModel.observe(getViewLifecycleOwner(), snapshot -> renderGoalsIfChanged(snapshot.getSavingsGoals()));
    }

    private void renderGoalsIfChanged(List<SavingsGoal> goals) {
        if (binding == null) {
            return;
        }
        if (renderedGoals != null && sameGoals(renderedGoals, goals)) {
            return;
        }
        renderedGoals = goals;
        binding.savingsGoalCount.setText(goals.size() + " 个目标");
        binding.savingsGoalList.removeAllViews();
        if (goals.isEmpty()) {
            binding.savingsGoalList.addView(createEmptyGoalHint());
        } else {
            for (SavingsGoal goal : goals) {
                binding.savingsGoalList.addView(createGoalCard(goal));
            }
        }
        binding.savingsAddGoalButton.setVisibility(View.VISIBLE);
    }

    private View createEmptyGoalHint() {
        Context ctx = requireContext();
        LinearLayout empty = ForestUi.column(ctx);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(dp(ctx, 16), dp(ctx, 28), dp(ctx, 16), dp(ctx, 20));
        ImageView sprout = new ImageView(ctx);
        sprout.setImageResource(R.drawable.savings_seedling_stage_1);
        sprout.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        empty.addView(sprout, new LinearLayout.LayoutParams(dp(ctx, 84), dp(ctx, 84)));
        TextView title = ForestUi.heading(ctx, "种下第一颗攒钱心愿", 17);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setTextColor(ForestSheetHelper.INK);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.topMargin = dp(ctx, 10);
        empty.addView(title, titleLp);
        TextView hint = ForestUi.text(ctx, "点下方「＋ 新建目标」，设定心愿名称和目标金额。", 12, ForestUi.MUTED);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-2, -2);
        hintLp.topMargin = dp(ctx, 4);
        empty.addView(hint, hintLp);
        return empty;
    }

    private static boolean sameGoals(List<SavingsGoal> a, List<SavingsGoal> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            SavingsGoal left = a.get(i);
            SavingsGoal right = b.get(i);
            if (!left.getId().equals(right.getId())
                    || !left.getName().equals(right.getName())
                    || left.getTargetCents() != right.getTargetCents()
                    || left.getSavedCents() != right.getSavedCents()) {
                return false;
            }
        }
        return true;
    }

    private View createGoalCard(SavingsGoal goal) {
        View card = LayoutInflater.from(requireContext()).inflate(
                R.layout.item_savings_goal, binding.savingsGoalList, false);
        ViewGroup.MarginLayoutParams cardParams =
                (ViewGroup.MarginLayoutParams) card.getLayoutParams();
        cardParams.topMargin = dp(requireContext(), 12);
        card.setLayoutParams(cardParams);

        TextView kicker = card.findViewById(R.id.savings_goal_kicker);
        TextView state = card.findViewById(R.id.savings_goal_state);
        ImageView stageImage = card.findViewById(R.id.savings_goal_stage_image);
        TextView name = card.findViewById(R.id.savings_goal_name);
        TextView target = card.findViewById(R.id.savings_goal_target);
        TextView percentLabel = card.findViewById(R.id.savings_goal_percent);
        ProgressBar progress = card.findViewById(R.id.savings_goal_progress);
        TextView saved = card.findViewById(R.id.savings_goal_saved);
        TextView remaining = card.findViewById(R.id.savings_goal_remaining);
        TextView depositButton = card.findViewById(R.id.savings_goal_deposit_button);
        TextView editButton = card.findViewById(R.id.savings_goal_edit_button);

        int percent = goal.getProgressPercent();
        kicker.setText("攒钱心愿卡");
        stageImage.setImageResource(stageDrawable(percent));
        state.setText(goal.isComplete() ? "已完成" : "进行中");
        name.setText(goal.getName());
        saved.setText(MoneyParser.formatCents(goal.getSavedCents()));
        target.setText("/ " + MoneyParser.formatCents(goal.getTargetCents()));
        percentLabel.setText(percent + "%");
        progress.setProgress(percent);
        remaining.setText(goal.isComplete()
                ? "已达成目标，还可继续存入"
                : "还差 " + MoneyParser.formatCents(goal.getRemainingCents()));

        depositButton.setText(goal.isComplete() ? "＋ 继续存入" : "＋ 存入");
        depositButton.setOnClickListener(v -> showDepositSheet(goal));
        editButton.setOnClickListener(v -> showGoalEditorSheet(goal));
        ForestMotion.bind(depositButton);
        ForestMotion.bind(editButton);
        return card;
    }

    private void showDepositSheet(SavingsGoal goal) {
        Context ctx = requireContext();
        BottomSheetDialog sheet = createManagedSheet();
        String subtitle = "已存 " + MoneyParser.formatCents(goal.getSavedCents())
                + " / 目标 " + MoneyParser.formatCents(goal.getTargetCents());
        LinearLayout root = ForestSheetHelper.createSheetRoot(
                ctx, sheet, "存入「" + goal.getName() + "」", subtitle
        );

        root.addView(ForestSheetHelper.fieldLabel(ctx, "存入金额（元）"));
        EditText amountInput = ForestSheetHelper.underlineInput(
                ctx, "0.00", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        amountInput.setTextSize(26f);
        amountInput.setTypeface(Typeface.create("serif", Typeface.BOLD));
        amountInput.setFontFeatureSettings("tnum");
        root.addView(amountInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout quickRow = row(ctx);
        int[] quickAmounts = {50, 100, 200};
        for (int i = 0; i < quickAmounts.length; i++) {
            int yuan = quickAmounts[i];
            TextView chip = ForestSheetHelper.quickChip(ctx, "+" + yuan, v -> {
                amountInput.setText(String.valueOf(yuan));
                amountInput.setSelection(amountInput.getText().length());
                amountInput.setError(null);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.setMarginStart(dp(ctx, 10));
            quickRow.addView(chip, lp);
        }
        ForestUi.add(root, quickRow, 14);

        MaterialButton confirmBtn = ForestSheetHelper.primaryButton(ctx, "放进罐子", v -> {
            try {
                long amountCents = MoneyParser.parseYuanToCents(amountInput.getText().toString());
                renderedGoals = null;
                SavingsGoal updated = viewModel.depositSavingsGoal(
                        goal.getId(), amountCents, System.currentTimeMillis());
                sheet.dismiss();
                Toast.makeText(ctx, updated.isComplete()
                        ? "目标完成，森林又长大了一点" : "已存入 " + MoneyParser.formatCents(amountCents),
                        Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException exception) {
                amountInput.setError(exception.getMessage());
                amountInput.requestFocus();
            }
        });
        ForestUi.add(root, confirmBtn, 18);

        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root);
        sheet.show();
    }

    private void showGoalEditorSheet(@Nullable SavingsGoal goal) {
        Context ctx = requireContext();
        BottomSheetDialog sheet = createManagedSheet();
        LinearLayout root = ForestSheetHelper.createSheetRoot(
                ctx,
                sheet,
                goal == null ? "新建攒钱目标" : "编辑攒钱目标",
                goal == null ? "定一个小目标，每次存入都会记入生活账本" : "修改名称或目标金额不会影响已存入金额"
        );

        root.addView(ForestSheetHelper.fieldLabel(ctx, "目标名称"));
        EditText nameInput = ForestSheetHelper.underlineInput(
                ctx, "例如：周末去看海", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        if (goal != null) {
            nameInput.setText(goal.getName());
            nameInput.setSelection(nameInput.getText().length());
        }
        root.addView(nameInput, new LinearLayout.LayoutParams(-1, -2));

        ForestUi.add(root, ForestSheetHelper.fieldLabel(ctx, "目标金额（元）"), 16);
        EditText targetInput = ForestSheetHelper.underlineInput(
                ctx, "例如：3000", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        targetInput.setTextSize(22f);
        targetInput.setTypeface(Typeface.create("serif", Typeface.BOLD));
        targetInput.setFontFeatureSettings("tnum");
        if (goal != null) {
            targetInput.setText(formatInputYuan(goal.getTargetCents()));
        }
        root.addView(targetInput, new LinearLayout.LayoutParams(-1, -2));

        MaterialButton saveBtn = ForestSheetHelper.primaryButton(
                ctx, goal == null ? "保存这张目标卡" : "保存修改", v -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameInput.setError("请输入目标名称");
                        nameInput.requestFocus();
                        return;
                    }
                    try {
                        long targetCents = MoneyParser.parseYuanToCents(targetInput.getText().toString());
                        renderedGoals = null;
                        if (goal == null) {
                            viewModel.createSavingsGoal(name, targetCents);
                            Toast.makeText(ctx, "目标已加入森林", Toast.LENGTH_SHORT).show();
                        } else {
                            viewModel.updateSavingsGoal(goal.getId(), name, targetCents);
                            Toast.makeText(ctx, "目标已更新", Toast.LENGTH_SHORT).show();
                        }
                        sheet.dismiss();
                    } catch (IllegalArgumentException exception) {
                        targetInput.setError(exception.getMessage());
                        targetInput.requestFocus();
                    }
                });
        ForestUi.add(root, saveBtn, 18);

        if (goal != null) {
            TextView deleteBtn = ForestSheetHelper.quietAction(
                    ctx, "删除这个目标", ForestSheetHelper.TERRACOTTA, v -> {
                        sheet.dismiss();
                        confirmDeleteGoal(goal);
                    });
            ForestUi.add(root, deleteBtn, 6);
        }

        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root);
        sheet.show();
    }

    private int stageDrawable(int progressPercent) {
        if (progressPercent >= 100) {
            return R.drawable.savings_tree_stage_3;
        }
        if (progressPercent >= 50) {
            return R.drawable.savings_seedling_stage_2;
        }
        return R.drawable.savings_seedling_stage_1;
    }

    private void confirmDeleteGoal(SavingsGoal goal) {
        new ForestDialogBuilder(requireContext())
                .setTitle("删除「" + goal.getName() + "」？")
                .setMessage("目标卡会删除，但已存入的记录会保留在资产、明细和图表中。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除目标", (dialog, which) -> {
                    renderedGoals = null;
                    if (viewModel.deleteSavingsGoal(goal.getId())) {
                        Toast.makeText(requireContext(), "目标已删除", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private BottomSheetDialog createManagedSheet() {
        if (activeSheet != null) {
            activeSheet.dismiss();
        }
        BottomSheetDialog sheet = ForestSheetHelper.createSheet(requireContext());
        activeSheet = sheet;
        sheet.setOnDismissListener(d -> {
            if (activeSheet == sheet) activeSheet = null;
        });
        return sheet;
    }

    private static String formatInputYuan(long cents) {
        BigDecimal bd = BigDecimal.valueOf(cents, 2).stripTrailingZeros();
        return bd.scale() <= 0 ? bd.toBigInteger().toString() : bd.toPlainString();
    }

    @Override
    public void onDestroyView() {
        if (activeSheet != null) {
            activeSheet.dismiss();
            activeSheet = null;
        }
        binding = null;
        renderedGoals = null;
        super.onDestroyView();
    }
}

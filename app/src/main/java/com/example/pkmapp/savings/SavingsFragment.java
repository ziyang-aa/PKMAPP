package com.example.pkmapp.savings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.databinding.FragmentSavingsBinding;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.MoneyParser;

import java.math.BigDecimal;
import java.util.List;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class SavingsFragment extends Fragment {
    private FragmentSavingsBinding binding;
    private SavingsGoalStore goalStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSavingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        goalStore = new SavingsGoalStore(requireContext());
        binding.pageBackButton.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.savingsAddGoalButton.setOnClickListener(v -> addNewGoalCard());
        renderGoals();
    }

    private void renderGoals() {
        if (binding == null || goalStore == null) {
            return;
        }
        List<SavingsGoal> goals = goalStore.getGoals();
        binding.savingsGoalCount.setText(goals.size() + " 个目标");
        binding.savingsGoalList.removeAllViews();
        if (goals.isEmpty()) {
            binding.savingsGoalList.addView(createGoalCard(null));
        } else {
            for (SavingsGoal goal : goals) {
                binding.savingsGoalList.addView(createGoalCard(goal));
            }
        }
        binding.savingsAddGoalButton.setVisibility(goals.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void addNewGoalCard() {
        if (binding == null) {
            return;
        }
        binding.savingsGoalList.addView(createGoalCard(null));
    }

    private View createGoalCard(@Nullable SavingsGoal goal) {
        View card = LayoutInflater.from(requireContext()).inflate(
                R.layout.item_savings_goal, binding.savingsGoalList, false);
        ViewGroup.MarginLayoutParams cardParams =
                (ViewGroup.MarginLayoutParams) card.getLayoutParams();
        cardParams.topMargin = dp(12);
        card.setLayoutParams(cardParams);

        TextView kicker = card.findViewById(R.id.savings_goal_kicker);
        TextView state = card.findViewById(R.id.savings_goal_state);
        ImageView stageImage = card.findViewById(R.id.savings_goal_stage_image);
        TextView name = card.findViewById(R.id.savings_goal_name);
        TextView description = card.findViewById(R.id.savings_goal_description);
        View stats = card.findViewById(R.id.savings_goal_stats);
        TextView target = card.findViewById(R.id.savings_goal_target);
        TextView percentLabel = card.findViewById(R.id.savings_goal_percent);
        ProgressBar progress = card.findViewById(R.id.savings_goal_progress);
        TextView saved = card.findViewById(R.id.savings_goal_saved);
        View bottom = card.findViewById(R.id.savings_goal_bottom);
        TextView remaining = card.findViewById(R.id.savings_goal_remaining);
        View deposit = card.findViewById(R.id.savings_goal_deposit);
        EditText nameInput = card.findViewById(R.id.savings_goal_name_input);
        EditText targetInput = card.findViewById(R.id.savings_goal_target_input);
        TextView message = card.findViewById(R.id.savings_goal_message);
        TextView saveButton = card.findViewById(R.id.savings_goal_save_button);
        EditText depositInput = card.findViewById(R.id.savings_goal_deposit_input);
        TextView depositButton = card.findViewById(R.id.savings_goal_deposit_button);
        TextView deleteButton = card.findViewById(R.id.savings_goal_delete_button);

        kicker.setText("GOAL CARD · SEEDLING");
        if (goal == null) {
            stageImage.setImageResource(R.drawable.savings_seedling_stage_1);
            state.setText("空白目标");
            name.setText("种下一颗愿望");
            description.setText("给它一个名字和金额，今天就可以开始慢慢靠近。");
            stats.setVisibility(View.GONE);
            progress.setVisibility(View.GONE);
            bottom.setVisibility(View.GONE);
            deposit.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
            depositButton.setEnabled(false);
        } else {
            int percent = goal.getProgressPercent();
            stageImage.setImageResource(stageDrawable(percent));
            state.setText(goal.isComplete() ? "已经完成" : "进行中");
            name.setText(goal.getName());
            description.setText("每一次存入，都会让这张卡片长出新的叶子。");
            stats.setVisibility(View.VISIBLE);
            percentLabel.setText(percent + "%");
            target.setText(MoneyParser.formatCents(goal.getTargetCents()));
            progress.setProgress(percent);
            progress.setVisibility(View.VISIBLE);
            bottom.setVisibility(View.VISIBLE);
            saved.setText("已存入 " + MoneyParser.formatCents(goal.getSavedCents()));
            remaining.setText(goal.isComplete() ? "已达到目标，还可继续存入"
                : "还差 " + MoneyParser.formatCents(goal.getRemainingCents()));
            deposit.setVisibility(View.VISIBLE);
            nameInput.setText(goal.getName());
            targetInput.setText(formatInputYuan(goal.getTargetCents()));
            depositButton.setText(goal.isComplete() ? "继续存入" : "放进罐子");
            deleteButton.setOnClickListener(v -> confirmDeleteGoal(goal));
        }

        saveButton.setText(goal == null ? "保存这张目标卡" : "保存修改");
        saveButton.setOnClickListener(v -> saveGoal(goal, nameInput, targetInput, message));
        depositButton.setOnClickListener(v -> deposit(goal, depositInput, message));
        return card;
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

    private void saveGoal(@Nullable SavingsGoal goal, EditText nameInput, EditText targetInput,
            TextView message) {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("请输入目标名称");
            return;
        }
        try {
            long targetCents = MoneyParser.parseYuanToCents(targetInput.getText().toString());
            if (goal == null) {
                goalStore.createGoal(name, targetCents);
                Toast.makeText(requireContext(), "目标已加入森林", Toast.LENGTH_SHORT).show();
            } else {
                goalStore.updateGoal(goal.getId(), name, targetCents);
                Toast.makeText(requireContext(), "目标已更新", Toast.LENGTH_SHORT).show();
            }
            renderGoals();
        } catch (IllegalArgumentException exception) {
            targetInput.setError(exception.getMessage());
            message.setText(exception.getMessage());
        }
    }

    private void deposit(@Nullable SavingsGoal goal, EditText depositInput, TextView message) {
        if (goal == null) {
            message.setText("请先保存目标，再存入金额");
            return;
        }
        try {
            long amountCents = MoneyParser.parseYuanToCents(depositInput.getText().toString());
            SavingsGoal updated = goalStore.deposit(goal.getId(), amountCents);
            InMemoryLedgerRepository.getInstance().addTransaction(TransactionType.INCOME, amountCents,
                    "攒钱 · " + updated.getName(), "为「" + updated.getName() + "」存入",
                    System.currentTimeMillis());
            Toast.makeText(requireContext(), updated.isComplete()
                    ? "目标完成，森林又长大了一点" : "已存入目标", Toast.LENGTH_SHORT).show();
            renderGoals();
        } catch (IllegalArgumentException exception) {
            depositInput.setError(exception.getMessage());
            message.setText(exception.getMessage());
        }
    }

    private void confirmDeleteGoal(SavingsGoal goal) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除「" + goal.getName() + "」？")
                .setMessage("目标卡会删除，但已存入的记录会保留在资产、明细和图表中。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除目标", (dialog, which) -> {
                    if (goalStore.deleteGoal(goal.getId())) {
                        renderGoals();
                        Toast.makeText(requireContext(), "目标已删除", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private String formatInputYuan(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        goalStore = null;
        super.onDestroyView();
    }
}

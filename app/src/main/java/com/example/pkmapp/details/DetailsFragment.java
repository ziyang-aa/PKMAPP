package com.example.pkmapp.details;

import static com.example.pkmapp.forest.ForestUi.DEEP;
import static com.example.pkmapp.forest.ForestUi.MUTED;
import static com.example.pkmapp.forest.ForestUi.dp;
import static com.example.pkmapp.forest.ForestUi.ripple;
import static com.example.pkmapp.forest.ForestUi.rounded;
import static com.example.pkmapp.forest.ForestUi.row;
import static com.example.pkmapp.forest.ForestUi.text;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.data.MonthlyBudgetEvaluator;
import com.example.pkmapp.data.MonthlyTotals;
import com.example.pkmapp.databinding.FragmentDetailsBinding;
import com.example.pkmapp.forest.ForestDialogBuilder;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.ForestSheetHelper;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.life.LifeSharedViewModel;
import com.example.pkmapp.life.LifeSnapshot;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.MoneyParser;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class DetailsFragment extends Fragment {
    private LifeSharedViewModel viewModel;
    private FragmentDetailsBinding binding;
    private TransactionListAdapter transactionAdapter;
    private BottomSheetDialog activeSheet;
    private final Calendar selectedMonth = Calendar.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentDetailsBinding.inflate(inflater, container, false);
        binding.pageBackButton.setOnClickListener(
                view -> ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.detailsLedgerSwitchButton.setOnClickListener(view -> showLedgerChooser());
        binding.detailsBudgetSection.setOnClickListener(view -> showBudgetSheet());
        binding.detailsSearchButton.setOnClickListener(view -> showSearchSheet());
        binding.detailsRecurringToolButton.setOnClickListener(view ->
                ((MainActivity) requireActivity()).openLifeTool(AppDestination.RECURRING, AppDestination.DETAILS));
        binding.detailsFxToolButton.setOnClickListener(view ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.EXCHANGE));
        binding.detailsBorrowingToolButton.setOnClickListener(view ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.BORROWING));
        binding.detailsAddRecordButton.setOnClickListener(
                view -> ((MainActivity) requireActivity()).showDestination(AppDestination.RECORD));
        binding.detailsMonthPickerButton.setOnClickListener(view -> showMonthPicker());
        binding.detailsCurrentMonthButton.setOnClickListener(view -> {
            Calendar now = Calendar.getInstance();
            selectedMonth.setTimeInMillis(DetailsPickerLabels.monthStart(now).getTimeInMillis());
            render();
        });
        transactionAdapter = new TransactionListAdapter(this::confirmDeleteTransaction,
                transaction -> ((MainActivity) requireActivity()).showEditTransaction(transaction));
        binding.detailsTransactionList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.detailsTransactionList.setAdapter(transactionAdapter);
        binding.detailsTransactionList.setNestedScrollingEnabled(false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = LifeSharedViewModel.get(requireActivity());
        viewModel.observe(getViewLifecycleOwner(), this::renderSnapshot);
    }

    private void showBudgetSheet() {
        Context ctx = requireContext();
        Ledger current = viewModel.getCurrentSnapshot().getCurrentLedger();
        long currentBudget = current.getMonthlyBudgetCents();
        BottomSheetDialog sheet = createManagedSheet();
        LinearLayout root = ForestSheetHelper.createSheetRoot(
                ctx,
                sheet,
                "设置月度预算",
                "当前账本 · " + current.getName() + "（作为本月与历史月的基准预算）"
        );

        root.addView(ForestSheetHelper.fieldLabel(ctx, "每月预算金额（元）"));
        EditText amountInput = ForestSheetHelper.underlineInput(
                ctx,
                "例如：2000",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        amountInput.setTextSize(24f);
        amountInput.setTypeface(Typeface.create("serif", Typeface.BOLD));
        amountInput.setFontFeatureSettings("tnum");
        if (currentBudget > 0L) {
            amountInput.setText(formatPlainYuan(currentBudget));
            amountInput.setSelection(amountInput.getText().length());
        }
        root.addView(amountInput, new LinearLayout.LayoutParams(-1, -2));

        // Quick preset chips (wrap_content, hairline porcelain)
        LinearLayout presets = row(ctx);
        int[] presetValues = {1500, 2000, 3000, 5000};
        for (int i = 0; i < presetValues.length; i++) {
            int yuan = presetValues[i];
            TextView chip = ForestSheetHelper.quickChip(ctx, "¥" + yuan, v -> {
                amountInput.setText(String.valueOf(yuan));
                amountInput.setSelection(amountInput.getText().length());
                amountInput.setError(null);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.setMarginStart(dp(ctx, 8));
            presets.addView(chip, lp);
        }
        ForestUi.add(root, presets, 14);

        MaterialButton saveBtn = ForestSheetHelper.primaryButton(ctx, "保存月度预算", v -> {
            String raw = amountInput.getText().toString().trim();
            if (raw.isEmpty() || "0".equals(raw) || "0.00".equals(raw)) {
                viewModel.updateCurrentLedgerMonthlyBudget(0L);
                sheet.dismiss();
                Toast.makeText(ctx, "已关闭本账本月度预算", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                long cents = MoneyParser.parseYuanToCents(raw);
                viewModel.updateCurrentLedgerMonthlyBudget(cents);
                sheet.dismiss();
                Toast.makeText(ctx, "已设定月度预算 " + MoneyParser.formatCents(cents),
                        Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                amountInput.setError(e.getMessage());
                amountInput.requestFocus();
            }
        });
        ForestUi.add(root, saveBtn, 18);

        if (currentBudget > 0L) {
            TextView clearBtn = ForestSheetHelper.quietAction(ctx, "清除月度预算",
                    ForestSheetHelper.TERRACOTTA, v -> {
                        viewModel.updateCurrentLedgerMonthlyBudget(0L);
                        sheet.dismiss();
                        Toast.makeText(ctx, "已清除月度预算", Toast.LENGTH_SHORT).show();
                    });
            ForestUi.add(root, clearBtn, 6);
        }

        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root);
        sheet.show();
    }

    private void showLedgerChooser() {
        Context ctx = requireContext();
        LifeSnapshot snapshot = viewModel.getCurrentSnapshot();
        List<Ledger> ledgers = snapshot.getLedgers();
        String currentLedgerId = snapshot.getCurrentLedger().getId();

        BottomSheetDialog sheet = createManagedSheet();
        LinearLayout root = ForestSheetHelper.createSheetRoot(
                ctx, sheet, "切换账本", "每本账本独立统计收支、资产与月度预算"
        );

        for (int i = 0; i < ledgers.size(); i++) {
            Ledger ledger = ledgers.get(i);
            boolean selected = ledger.getId().equals(currentLedgerId);
            LinearLayout itemRow = row(ctx);
            itemRow.setMinimumHeight(dp(ctx, 50));
            itemRow.setPadding(dp(ctx, 4), dp(ctx, 10), dp(ctx, 4), dp(ctx, 10));
            itemRow.setBackground(ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 10), false));
            itemRow.setContentDescription("切换到" + ledger.getName());

            TextView nameView = text(ctx, ledger.getName(), 15,
                    selected ? DEEP : ForestSheetHelper.INK);
            nameView.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif",
                    selected ? Typeface.BOLD : Typeface.NORMAL));
            itemRow.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1));

            String rightLabel;
            if (selected) {
                rightLabel = ledger.getMonthlyBudgetCents() > 0L
                        ? "预算 " + MoneyParser.formatCents(ledger.getMonthlyBudgetCents()) + " · 当前"
                        : "当前";
            } else if (ledger.getMonthlyBudgetCents() > 0L) {
                rightLabel = "预算 " + MoneyParser.formatCents(ledger.getMonthlyBudgetCents());
            } else {
                rightLabel = "切换 ›";
            }
            TextView tagView = text(ctx, rightLabel, 12, selected ? DEEP : MUTED);
            tagView.setFontFeatureSettings("tnum");
            itemRow.addView(tagView);

            itemRow.setOnClickListener(v -> {
                viewModel.switchLedger(ledger.getId());
                sheet.dismiss();
            });
            ForestMotion.bind(itemRow);
            root.addView(itemRow, new LinearLayout.LayoutParams(-1, -2));
            if (i < ledgers.size() - 1) {
                ForestSheetHelper.addHairline(root, 0, 0);
            }
        }

        ForestSheetHelper.addHairline(root, 8, 14);
        TextView newBtn = ForestSheetHelper.quickChip(ctx, "＋ 新建一本账本", v -> {
            sheet.dismiss();
            showCreateLedgerSheet();
        });
        newBtn.setMinimumHeight(dp(ctx, 44));
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(-2, -2);
        newLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(newBtn, newLp);

        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root);
        sheet.show();
    }

    private void showCreateLedgerSheet() {
        Context ctx = requireContext();
        BottomSheetDialog sheet = createManagedSheet();
        LinearLayout root = ForestSheetHelper.createSheetRoot(
                ctx, sheet, "新建一本账本", "可随时为不同生活场景分别记账"
        );

        root.addView(ForestSheetHelper.fieldLabel(ctx, "账本名称"));
        EditText nameInput = ForestSheetHelper.underlineInput(
                ctx, "例如：旅行账本、校园日常", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        root.addView(nameInput, new LinearLayout.LayoutParams(-1, -2));

        MaterialButton createBtn = ForestSheetHelper.primaryButton(ctx, "创建并切换", v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError("请输入账本名称");
                nameInput.requestFocus();
                return;
            }
            viewModel.createAndSwitchLedger(name);
            sheet.dismiss();
        });
        ForestUi.add(root, createBtn, 18);

        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root);
        sheet.show();
    }

    private void render() {
        if (viewModel != null) {
            renderSnapshot(viewModel.getCurrentSnapshot());
        }
    }

    private void renderSnapshot(LifeSnapshot snapshot) {
        if (binding == null || !isAdded()) {
            return;
        }
        MonthlyTotals totals = viewModel.monthTotalsFor(selectedMonth.getTimeInMillis());
        Ledger currentLedger = snapshot.getCurrentLedger();
        String ledgerName = currentLedger.getName();
        binding.detailsLedgerSwitchButton.setText(ledgerName + "  ▾");
        binding.detailsLedgerName.setText("当前账本 · " + ledgerName);
        binding.detailsIncome.setText(flowText("↑ 收入", totals.getIncomeInCents(),
                R.color.forest_green), TextView.BufferType.SPANNABLE);
        binding.detailsExpense.setText(flowText("↓ 支出", totals.getExpenseInCents(),
                R.color.expense_red), TextView.BufferType.SPANNABLE);
        binding.detailsBalance.setText(balanceText(totals.getBalanceInCents()),
                TextView.BufferType.SPANNABLE);

        renderBudgetBar(currentLedger.getMonthlyBudgetCents(), totals.getExpenseInCents());

        binding.detailsMonthPickerButton.setText(DetailsPickerLabels.monthButtonText(
                selectedMonth.get(Calendar.YEAR), selectedMonth.get(Calendar.MONTH)));
        List<com.example.pkmapp.data.Transaction> transactions = viewModel.transactionsForMonth(
                selectedMonth.get(Calendar.YEAR), selectedMonth.get(Calendar.MONTH));
        binding.detailsEmptyState.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
        binding.detailsTransactionList.setVisibility(transactions.isEmpty() ? View.GONE : View.VISIBLE);
        transactionAdapter.submit(DetailsListItem.fromTransactions(transactions,
                System.currentTimeMillis()));
    }

    private void renderBudgetBar(long budgetCents, long expenseCents) {
        Calendar now = Calendar.getInstance();
        boolean isCurrentMonth = selectedMonth.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && selectedMonth.get(Calendar.MONTH) == now.get(Calendar.MONTH);
        String prefix = isCurrentMonth
                ? "月度预算"
                : String.format(Locale.CHINA, "%d月复盘", selectedMonth.get(Calendar.MONTH) + 1);
        binding.detailsBudgetLabel.setText(prefix);

        MonthlyBudgetEvaluator.Status status = MonthlyBudgetEvaluator.evaluate(budgetCents, expenseCents);
        if (!status.isSet()) {
            binding.detailsBudgetBadge.setText("未设定");
            binding.detailsBudgetBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.brown_soft));
            binding.detailsBudgetSummary.setText("设预算 ›");
            binding.detailsBudgetSummary.setTextColor(ContextCompat.getColor(requireContext(), R.color.forest_green));
            binding.detailsBudgetTrackWrap.setVisibility(View.GONE);
            return;
        }

        int accentColor;
        String badgeText;
        String rightText;
        if (status.getLevel() == MonthlyBudgetEvaluator.Level.EXCEEDED) {
            accentColor = ForestSheetHelper.TERRACOTTA;
            badgeText = "超支 " + status.getUsagePercent() + "%";
            rightText = "超支 " + MoneyParser.formatCents(status.getOverBudgetCents())
                    + " / " + MoneyParser.formatCents(status.getBudgetCents()) + " ›";
        } else if (status.getLevel() == MonthlyBudgetEvaluator.Level.WARNING) {
            accentColor = ForestSheetHelper.AMBER;
            badgeText = "已用 " + status.getUsagePercent() + "% · 接近上限";
            rightText = "余 " + MoneyParser.formatCents(status.getRemainingCents())
                    + " / " + MoneyParser.formatCents(status.getBudgetCents()) + " ›";
        } else {
            accentColor = ForestSheetHelper.PINE;
            badgeText = "已用 " + status.getUsagePercent() + "%";
            rightText = "余 " + MoneyParser.formatCents(status.getRemainingCents())
                    + " / " + MoneyParser.formatCents(status.getBudgetCents()) + " ›";
        }

        binding.detailsBudgetBadge.setText(badgeText);
        binding.detailsBudgetBadge.setTextColor(accentColor);
        binding.detailsBudgetSummary.setText(rightText);
        binding.detailsBudgetSummary.setTextColor(
                status.getLevel() == MonthlyBudgetEvaluator.Level.EXCEEDED
                        ? ForestSheetHelper.TERRACOTTA
                        : ContextCompat.getColor(requireContext(), R.color.ink));
        binding.detailsBudgetTrackWrap.setVisibility(View.VISIBLE);
        binding.detailsBudgetFill.setBackgroundColor(accentColor);

        final int permille = status.getProgressPermille();
        binding.detailsBudgetTrackWrap.post(() -> {
            if (binding == null) return;
            int totalWidth = binding.detailsBudgetTrackWrap.getWidth();
            if (totalWidth <= 0) return;
            ViewGroup.LayoutParams lp = binding.detailsBudgetFill.getLayoutParams();
            lp.width = Math.round(totalWidth * (permille / 1000f));
            binding.detailsBudgetFill.setLayoutParams(lp);
        });
    }

    private void showMonthPicker() {
        Context ctx = requireContext();
        BottomSheetDialog sheet = createManagedSheet();
        LinearLayout root = ForestSheetHelper.createSheetRoot(
                ctx, sheet, "选择查看月份", "支持切换历史月份复盘收支与预算"
        );

        final int[] chosen = {selectedMonth.get(Calendar.YEAR), selectedMonth.get(Calendar.MONTH)};
        LinearLayout columns = row(ctx);

        ScrollView yearScroll = new ScrollView(ctx);
        yearScroll.setVerticalScrollBarEnabled(false);
        LinearLayout yearOptions = ForestUi.column(ctx);
        yearScroll.addView(yearOptions, new ViewGroup.LayoutParams(-1, -2));

        ScrollView monthScroll = new ScrollView(ctx);
        monthScroll.setVerticalScrollBarEnabled(false);
        LinearLayout monthOptions = ForestUi.column(ctx);
        monthScroll.addView(monthOptions, new ViewGroup.LayoutParams(-1, -2));

        columns.addView(yearScroll, new LinearLayout.LayoutParams(0, dp(ctx, 210), 1f));
        View divider = new View(ctx);
        divider.setBackgroundColor(ForestSheetHelper.HAIRLINE);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                Math.max(1, Math.round(0.75f * getResources().getDisplayMetrics().density)),
                dp(ctx, 210));
        divLp.setMarginStart(dp(ctx, 8));
        divLp.setMarginEnd(dp(ctx, 8));
        columns.addView(divider, divLp);
        columns.addView(monthScroll, new LinearLayout.LayoutParams(0, dp(ctx, 210), 1f));
        root.addView(columns, new LinearLayout.LayoutParams(-1, -2));

        renderYearOptions(yearOptions, chosen);
        renderMonthOptions(monthOptions, chosen);

        MaterialButton confirmBtn = ForestSheetHelper.primaryButton(ctx, "查看该月明细", v -> {
            selectedMonth.set(chosen[0], chosen[1], 1, 12, 0, 0);
            selectedMonth.set(Calendar.MILLISECOND, 0);
            render();
            sheet.dismiss();
        });
        ForestUi.add(root, confirmBtn, 16);

        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root);
        sheet.show();
    }

    private void showSearchSheet() {
        LifeSnapshot snapshot = viewModel.getCurrentSnapshot();
        BottomSheetDialog sheet = createManagedSheet();
        TransactionSearchSheet.show(
                requireContext(),
                sheet,
                snapshot.getCurrentLedger().getName(),
                selectedMonth,
                () -> viewModel.getCurrentSnapshot().getTransactions(),
                transaction -> {
                    ((MainActivity) requireActivity()).showEditTransaction(transaction);
                    return kotlin.Unit.INSTANCE;
                },
                (transaction, onDeleted) -> {
                    confirmDeleteTransaction(transaction, onDeleted::invoke);
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    private void confirmDeleteTransaction(com.example.pkmapp.data.Transaction transaction) {
        confirmDeleteTransaction(transaction, null);
    }

    private void confirmDeleteTransaction(com.example.pkmapp.data.Transaction transaction,
            @Nullable Runnable onDeleted) {
        String summary = transaction.getCategory() + " · "
                + MoneyParser.formatCents(transaction.getAmountInCents());
        new ForestDialogBuilder(requireContext())
                .setTitle("删除这笔记录？")
                .setMessage(summary + "\n删除后本月收入、支出、预算与结余会立即更新。")
                .setNegativeButton("保留记录", null)
                .setPositiveButton("确认删除", (d, w) -> {
                    viewModel.deleteTransaction(transaction.getId());
                    if (onDeleted != null) {
                        onDeleted.run();
                    }
                })
                .show();
    }

    private void renderYearOptions(LinearLayout container, int[] chosen) {
        renderIntegerOptions(container, 2020, 2035, chosen[0], "年", value -> {
            chosen[0] = value;
            renderYearOptions(container, chosen);
        });
    }

    private void renderMonthOptions(LinearLayout container, int[] chosen) {
        renderIntegerOptions(container, 0, 11, chosen[1], "月", value -> {
            chosen[1] = value;
            renderMonthOptions(container, chosen);
        });
    }

    private void renderIntegerOptions(LinearLayout container, int min, int max, int selected,
            String suffix, PickerValueListener listener) {
        Context ctx = container.getContext();
        container.removeAllViews();
        for (int value = min; value <= max; value++) {
            boolean isSelected = value == selected;
            String label = DetailsPickerLabels.pickerOptionText(value, suffix);
            TextView option = text(ctx, label, 14, isSelected ? DEEP : MUTED);
            option.setGravity(Gravity.CENTER);
            option.setMinimumHeight(dp(ctx, 42));
            option.setFontFeatureSettings("tnum");
            option.setTypeface(Typeface.create(isSelected ? "sans-serif-medium" : "sans-serif",
                    isSelected ? Typeface.BOLD : Typeface.NORMAL));
            option.setBackground(ripple(ctx,
                    rounded(ctx, isSelected ? 0xffeef4ea : Color.TRANSPARENT, 0, 10), false));
            option.setContentDescription("选择" + label);
            final int chosenValue = value;
            option.setOnClickListener(view -> listener.onSelected(chosenValue));
            container.addView(option, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private interface PickerValueListener {
        void onSelected(int value);
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

    private static String formatPlainYuan(long cents) {
        BigDecimal bd = BigDecimal.valueOf(cents, 2).stripTrailingZeros();
        return bd.scale() <= 0 ? bd.toBigInteger().toString() : bd.toPlainString();
    }

    private CharSequence balanceText(long balanceInCents) {
        long absolute = balanceInCents == Long.MIN_VALUE
                ? Long.MAX_VALUE : Math.abs(balanceInCents);
        String amount = MoneyParser.formatCents(absolute);
        if (balanceInCents >= 0L) {
            return amount;
        }
        SpannableString text = new SpannableString("−" + amount);
        text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(),
                        R.color.expense_red)),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private CharSequence flowText(String label, long amountInCents, int amountColor) {
        long absolute = amountInCents == Long.MIN_VALUE
                ? Long.MAX_VALUE : Math.abs(amountInCents);
        String amount = MoneyParser.formatCents(absolute);
        SpannableString text = new SpannableString(label + "\n" + amount);
        text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(),
                        R.color.wood_brown)),
                0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), amountColor)),
                label.length() + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    @Override
    public void onDestroyView() {
        if (activeSheet != null) {
            activeSheet.dismiss();
            activeSheet = null;
        }
        binding = null;
        transactionAdapter = null;
        super.onDestroyView();
    }
}

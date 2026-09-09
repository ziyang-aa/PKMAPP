package com.example.pkmapp.details;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.data.LedgerDataListener;
import com.example.pkmapp.data.MonthlyTotals;
import com.example.pkmapp.databinding.FragmentDetailsBinding;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.MoneyParser;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class DetailsFragment extends Fragment {
    private final InMemoryLedgerRepository repository = InMemoryLedgerRepository.getInstance();
    private final LedgerDataListener listener = this::render;
    private FragmentDetailsBinding binding;
    private TransactionListAdapter transactionAdapter;
    private final Calendar selectedMonth = Calendar.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentDetailsBinding.inflate(inflater, container, false);
        binding.pageBackButton.setOnClickListener(
                view -> ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.detailsLedgerSwitchButton.setOnClickListener(view -> showLedgerChooser());
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
        transactionAdapter = new TransactionListAdapter(this::confirmDeleteTransaction);
        binding.detailsTransactionList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.detailsTransactionList.setAdapter(transactionAdapter);
        binding.detailsTransactionList.setNestedScrollingEnabled(false);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        repository.addListener(listener);
        render();
    }

    @Override
    public void onPause() {
        repository.removeListener(listener);
        super.onPause();
    }

    private void showLedgerChooser() {
        List<Ledger> ledgers = repository.getLedgers();
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View root = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_details_ledger_picker, null);
        LinearLayout options = root.findViewById(R.id.details_ledger_options);
        String currentLedgerId = repository.getCurrentLedger().getId();
        for (Ledger ledger : ledgers) {
            boolean selected = ledger.getId().equals(currentLedgerId);
            TextView option = pickerOption(ledger.getName() + (selected ? "  · 当前" : ""), selected);
            option.setContentDescription("切换到" + ledger.getName());
            option.setOnClickListener(view -> {
                repository.switchLedger(ledger.getId());
                dialog.dismiss();
            });
            options.addView(option);
        }
        root.findViewById(R.id.details_ledger_picker_close).setOnClickListener(view -> dialog.dismiss());
        root.findViewById(R.id.details_ledger_picker_cancel).setOnClickListener(view -> dialog.dismiss());
        root.findViewById(R.id.details_new_ledger_button).setOnClickListener(view -> {
            dialog.dismiss();
            showCreateLedgerSheet();
        });
        showPaperSheet(dialog, root);
    }

    private void showCreateLedgerSheet() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(16));
        root.setBackgroundResource(R.drawable.bg_details_picker_sheet);

        TextView eyebrow = pickerLabel("NEW FOREST LEDGER", 9, R.color.forest_green);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow, wrapContent());
        TextView title = pickerLabel("给新账本取个名字", 21, R.color.wood_brown);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = wrapContent();
        titleParams.topMargin = dp(4);
        root.addView(title, titleParams);

        EditText nameInput = new EditText(requireContext());
        nameInput.setSingleLine(true);
        nameInput.setHint("例如：北京旅行账本");
        nameInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink));
        nameInput.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.wood_brown));
        nameInput.setPadding(dp(16), 0, dp(16), 0);
        nameInput.setBackgroundResource(R.drawable.bg_details_picker_option);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        inputParams.topMargin = dp(16);
        root.addView(nameInput, inputParams);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView cancel = pickerLabel("取消", 13, R.color.wood_brown);
        cancel.setGravity(android.view.Gravity.CENTER);
        TextView create = pickerLabel("创建并切换", 13, R.color.forest_green);
        create.setGravity(android.view.Gravity.CENTER);
        create.setTypeface(null, android.graphics.Typeface.BOLD);
        create.setBackgroundResource(R.drawable.bg_details_picker_selected);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        createParams.leftMargin = dp(8);
        actions.addView(create, createParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        actionsParams.topMargin = dp(12);
        root.addView(actions, actionsParams);

        cancel.setOnClickListener(view -> dialog.dismiss());
        create.setOnClickListener(view -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError("请输入账本名称");
                return;
            }
            Ledger ledger = repository.createLedger(name);
            repository.switchLedger(ledger.getId());
            dialog.dismiss();
        });
        showPaperSheet(dialog, root);
    }

    private void render() {
        if (binding == null || !isAdded()) {
            return;
        }
        MonthlyTotals totals = repository.getCurrentMonthTotals(selectedMonth.getTimeInMillis());
        binding.detailsLedgerSwitchButton.setText(repository.getCurrentLedger().getName() + "  ▾");
        binding.detailsLedgerName.setText("当前账本 · " + repository.getCurrentLedger().getName());
        binding.detailsIncome.setText("↑ 收入\n" + MoneyParser.formatCents(totals.getIncomeInCents()));
        binding.detailsExpense.setText("↓ 支出\n" + MoneyParser.formatCents(totals.getExpenseInCents()));
        binding.detailsBalance.setText(MoneyParser.formatCents(totals.getBalanceInCents()));

        binding.detailsMonthPickerButton.setText(DetailsPickerLabels.monthButtonText(
                selectedMonth.get(Calendar.YEAR), selectedMonth.get(Calendar.MONTH)));
        List<com.example.pkmapp.data.Transaction> transactions = transactionsForSelectedMonth();
        binding.detailsEmptyState.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
        binding.detailsTransactionList.setVisibility(transactions.isEmpty() ? View.GONE : View.VISIBLE);
        transactionAdapter.submit(DetailsListItem.fromTransactions(transactions,
                System.currentTimeMillis()));
    }

    private void showMonthPicker() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View root = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_details_month_picker, null);
        LinearLayout yearOptions = root.findViewById(R.id.details_year_options);
        LinearLayout monthOptions = root.findViewById(R.id.details_month_options);
        final int[] chosen = {selectedMonth.get(Calendar.YEAR), selectedMonth.get(Calendar.MONTH)};
        renderYearOptions(yearOptions, chosen);
        renderMonthOptions(monthOptions, chosen);
        root.findViewById(R.id.details_month_picker_close).setOnClickListener(view -> dialog.dismiss());
        root.findViewById(R.id.details_month_picker_cancel).setOnClickListener(view -> dialog.dismiss());
        root.findViewById(R.id.details_month_picker_confirm).setOnClickListener(view -> {
            selectedMonth.set(chosen[0], chosen[1], 1, 12, 0, 0);
            selectedMonth.set(Calendar.MILLISECOND, 0);
            render();
            dialog.dismiss();
        });
        showPaperSheet(dialog, root);
    }

    private void confirmDeleteTransaction(com.example.pkmapp.data.Transaction transaction) {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(16));
        root.setBackgroundResource(R.drawable.bg_details_picker_sheet);

        TextView eyebrow = pickerLabel("FOREST RECORD", 9, R.color.forest_green);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow, wrapContent());
        TextView title = pickerLabel("删除这笔记录？", 21, R.color.wood_brown);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = wrapContent();
        titleParams.topMargin = dp(4);
        root.addView(title, titleParams);

        TextView summary = pickerLabel(transaction.getCategory() + "  ·  "
                + MoneyParser.formatCents(transaction.getAmountInCents()), 15, R.color.ink);
        LinearLayout.LayoutParams summaryParams = wrapContent();
        summaryParams.topMargin = dp(16);
        root.addView(summary, summaryParams);
        TextView hint = pickerLabel("删除后本月收入、支出和结余会立即更新", 12, R.color.wood_brown);
        LinearLayout.LayoutParams hintParams = wrapContent();
        hintParams.topMargin = dp(4);
        root.addView(hint, hintParams);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView cancel = pickerLabel("保留记录", 13, R.color.wood_brown);
        cancel.setGravity(android.view.Gravity.CENTER);
        TextView delete = pickerLabel("确认删除", 13, R.color.expense_red);
        delete.setGravity(android.view.Gravity.CENTER);
        delete.setTypeface(null, android.graphics.Typeface.BOLD);
        delete.setBackgroundResource(R.drawable.bg_details_delete_action);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        deleteParams.leftMargin = dp(8);
        actions.addView(delete, deleteParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        actionsParams.topMargin = dp(16);
        root.addView(actions, actionsParams);

        cancel.setOnClickListener(view -> dialog.dismiss());
        delete.setOnClickListener(view -> {
            repository.deleteTransaction(transaction.getId());
            dialog.dismiss();
        });
        showPaperSheet(dialog, root);
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
        container.removeAllViews();
        for (int value = min; value <= max; value++) {
            TextView option = pickerOption(DetailsPickerLabels.pickerOptionText(value, suffix),
                    value == selected);
            final int chosenValue = value;
            option.setOnClickListener(view -> listener.onSelected(chosenValue));
            container.addView(option);
        }
    }

    private interface PickerValueListener {
        void onSelected(int value);
    }

    private TextView pickerOption(String text, boolean selected) {
        TextView option = (TextView) LayoutInflater.from(requireContext()).inflate(
                R.layout.item_details_picker_option, null, false);
        option.setText(text);
        option.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        option.setTextColor(ContextCompat.getColor(requireContext(),
                selected ? R.color.forest_green : R.color.wood_brown));
        option.setContentDescription("选择" + text);
        return option;
    }

    private TextView pickerLabel(String text, int size, int color) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(ContextCompat.getColor(requireContext(), color));
        return label;
    }

    private LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void showPaperSheet(BottomSheetDialog dialog, View root) {
        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private List<com.example.pkmapp.data.Transaction> transactionsForSelectedMonth() {
        List<com.example.pkmapp.data.Transaction> filtered = new ArrayList<>();
        for (com.example.pkmapp.data.Transaction transaction : repository.getTransactionsForCurrentLedger()) {
            Calendar date = Calendar.getInstance();
            date.setTimeInMillis(transaction.getOccurredAtMillis());
            if (date.get(Calendar.YEAR) == selectedMonth.get(Calendar.YEAR)
                    && date.get(Calendar.MONTH) == selectedMonth.get(Calendar.MONTH)) {
                filtered.add(transaction);
            }
        }
        return filtered;
    }

    @Override
    public void onDestroyView() {
        binding = null;
        transactionAdapter = null;
        super.onDestroyView();
    }
}

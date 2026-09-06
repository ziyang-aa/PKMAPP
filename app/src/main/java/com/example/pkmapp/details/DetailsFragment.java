package com.example.pkmapp.details;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.data.LedgerDataListener;
import com.example.pkmapp.data.MonthlyTotals;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.databinding.FragmentDetailsBinding;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.MoneyParser;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DateFormat;
import java.util.List;
import java.util.Locale;

public final class DetailsFragment extends Fragment {
    private final InMemoryLedgerRepository repository = InMemoryLedgerRepository.getInstance();
    private final LedgerDataListener listener = this::render;
    private FragmentDetailsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentDetailsBinding.inflate(inflater, container, false);
        binding.pageBackButton.setOnClickListener(
                view -> ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.detailsLedgerSwitchButton.setOnClickListener(view -> showLedgerChooser());
        binding.detailsFxToolButton.setOnClickListener(view -> showComingSoonDialog(
                "汇率换算", "汇率换算的页面入口已准备好；联网自动更新将在数据功能阶段接入。"));
        binding.detailsBorrowingToolButton.setOnClickListener(view -> showComingSoonDialog(
                "借钱统计", "借入、借出和联系人资料将在数据功能阶段接入。"));
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
        String[] names = new String[ledgers.size()];
        int checkedItem = 0;
        String currentLedgerId = repository.getCurrentLedger().getId();
        for (int index = 0; index < ledgers.size(); index++) {
            Ledger ledger = ledgers.get(index);
            names[index] = ledger.getName();
            if (ledger.getId().equals(currentLedgerId)) {
                checkedItem = index;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("切换账本")
                .setSingleChoiceItems(names, checkedItem, (dialog, which) -> {
                    repository.switchLedger(ledgers.get(which).getId());
                    dialog.dismiss();
                })
                .setNeutralButton("新建账本", (dialog, which) -> showCreateLedgerDialog())
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCreateLedgerDialog() {
        EditText nameInput = new EditText(requireContext());
        nameInput.setHint("例如：北京旅行账本");
        nameInput.setSingleLine(true);
        int horizontalPadding = getResources().getDimensionPixelSize(R.dimen.space_24);
        nameInput.setPadding(horizontalPadding, 0, horizontalPadding, 0);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("新建账本")
                .setView(nameInput)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建并切换", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameInput.setError("请输入账本名称");
                        return;
                    }
                    Ledger ledger = repository.createLedger(name);
                    repository.switchLedger(ledger.getId());
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showComingSoonDialog(String title, String message) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void render() {
        if (binding == null || !isAdded()) {
            return;
        }
        MonthlyTotals totals = repository.getCurrentMonthTotals(System.currentTimeMillis());
        binding.detailsLedgerSwitchButton.setText(repository.getCurrentLedger().getName() + "  ▾");
        binding.detailsLedgerName.setText("当前账本 · " + repository.getCurrentLedger().getName());
        binding.detailsIncome.setText("收入  " + MoneyParser.formatCents(totals.getIncomeInCents()));
        binding.detailsExpense.setText("支出  " + MoneyParser.formatCents(totals.getExpenseInCents()));
        binding.detailsBalance.setText(MoneyParser.formatCents(totals.getBalanceInCents()));

        binding.detailsTransactionList.removeAllViews();
        for (Transaction transaction : repository.getTransactionsForCurrentLedger()) {
            binding.detailsTransactionList.addView(createTransactionRow(transaction));
        }
    }

    private TextView createTransactionRow(Transaction transaction) {
        TextView row = new TextView(requireContext());
        int verticalPadding = getResources().getDimensionPixelSize(R.dimen.space_8);
        row.setPadding(0, verticalPadding, 0, verticalPadding);
        String sign = transaction.getType() == TransactionType.INCOME ? "+" : "-";
        String note = transaction.getNote().isEmpty() ? "未写备注" : transaction.getNote();
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.CHINA);
        row.setText(transaction.getCategory() + "  ·  " + note + "\n"
                + dateFormat.format(transaction.getOccurredAtMillis()) + "    " + sign
                + MoneyParser.formatCents(transaction.getAmountInCents()));
        int colorRes = transaction.getType() == TransactionType.INCOME
                ? R.color.forest_green : R.color.expense_red;
        row.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        return row;
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}

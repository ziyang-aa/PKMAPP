package com.example.pkmapp.record;

import android.os.Bundle;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.borrowing.BorrowingDirection;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.AssetStore;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.databinding.FragmentRecordBinding;
import com.example.pkmapp.navigation.AppDestination;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;

public final class RecordFragment extends Fragment {
    private final RecordDateState date = RecordDateState.today();
    private final CalculatorEngine calculator = new CalculatorEngine();
    private FragmentRecordBinding binding;
    private TransactionType type = TransactionType.EXPENSE;
    private String category;
    private View selectedCategoryView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
            @Nullable Bundle state) {
        binding = FragmentRecordBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        binding.pageBackButton.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.recordTypeExpense.setSelected(true);
        binding.recordTypeExpense.setOnClickListener(v -> {
            type = TransactionType.EXPENSE;
            binding.recordTypeExpense.setSelected(true);
            binding.recordTypeIncome.setSelected(false);
            renderCategories();
        });
        binding.recordTypeIncome.setOnClickListener(v -> {
            type = TransactionType.INCOME;
            binding.recordTypeIncome.setSelected(true);
            binding.recordTypeExpense.setSelected(false);
            renderCategories();
        });
        binding.recordDateYear.setOnClickListener(v -> showDateDropdown(binding.recordDateYear, DatePart.YEAR));
        binding.recordDateMonth.setOnClickListener(v -> showDateDropdown(binding.recordDateMonth, DatePart.MONTH));
        binding.recordDateDay.setOnClickListener(v -> showDateDropdown(binding.recordDateDay, DatePart.DAY));
        binding.recordSaveButton.setOnClickListener(v -> save());
        bindCalculatorKeys();
        dateLabel();
        renderCategories();
    }

    private void renderCategories() {
        category = null;
        selectedCategoryView = null;
        binding.recordEntryCard.setVisibility(View.GONE);
        binding.recordCategoryGroup.removeAllViews();
        String[] labels = type == TransactionType.EXPENSE
                ? new String[]{"餐饮", "购物", "饮料", "交通", "日用", "学习", "水果", "娱乐", "服饰", "礼物", "红包", "人情", "医疗", "订阅", "借出", "还款", "自定义"}
                : new String[]{"工资", "零花", "兼职", "转卖", "借入", "自定义"};
        for (String label : labels) {
            LinearLayout item = new LinearLayout(requireContext());
            item.setGravity(Gravity.CENTER);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setMinimumHeight(dp(116));
            item.setPadding(dp(3), dp(7), dp(3), dp(7));
            item.setBackgroundResource(R.drawable.bg_category_tile);
            item.setForeground(ContextCompat.getDrawable(requireContext(), R.drawable.ripple_category_tile));
            item.setClickable(true);
            item.setFocusable(true);
            ImageView icon = new ImageView(requireContext());
            icon.setContentDescription(label);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImageResource(iconForCategory(label));
            TextView name = new TextView(requireContext());
            name.setText(label);
            name.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink));
            name.setGravity(Gravity.CENTER);
            name.setTextSize(11);
            item.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
            item.addView(name, new LinearLayout.LayoutParams(-1, dp(22)));
            item.setOnClickListener(v -> {
                if ("自定义".equals(label)) {
                    showCustomCategory(item);
                } else {
                    category = label;
                    selectCategoryView(item, label);
                    showEntry();
                }
            });
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(2), dp(6), dp(2), dp(6));
            binding.recordCategoryGroup.addView(item, params);
        }
    }

    private int iconForCategory(String label) {
        int resource = getResources().getIdentifier(CategoryIconResolver.resourceName(type, label),
                "drawable", requireContext().getPackageName());
        if (resource == 0) resource = R.drawable.norm_type_expense_custom;
        return resource;
    }

    private void showCustomCategory(View customItem) {
        EditText input = new EditText(requireContext());
        input.setHint("输入分类名称");
        new MaterialAlertDialogBuilder(requireContext()).setTitle("自定义分类").setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(requireContext(), "请输入分类名称", Toast.LENGTH_SHORT).show();
                    } else {
                        category = value;
                        selectCategoryView(customItem, value);
                        showEntry();
                    }
                }).show();
    }

    private void showEntry() {
        binding.recordEntryCard.setVisibility(View.VISIBLE);
        binding.recordSelectedCategory.setText("已选择：" + category);
        Drawable icon = ContextCompat.getDrawable(requireContext(), iconForCategory(category));
        if (icon != null) {
            int size = dp(40);
            icon.setBounds(0, 0, size, size);
            binding.recordSelectedCategory.setCompoundDrawables(icon, null, null, null);
            binding.recordSelectedCategory.setCompoundDrawablePadding(dp(12));
        }
        binding.recordCalculatorPanel.setVisibility(View.VISIBLE);
        binding.recordCalculatorPanel.post(() -> {
            int[] panelLocation = new int[2];
            int[] scrollLocation = new int[2];
            binding.recordCalculatorPanel.getLocationOnScreen(panelLocation);
            binding.getRoot().getLocationOnScreen(scrollLocation);
            ((android.widget.ScrollView) binding.getRoot()).smoothScrollTo(0,
                    Math.max(0, panelLocation[1] - scrollLocation[1] - dp(16)));
        });
    }

    private void selectCategoryView(View view, String selectedLabel) {
        if (selectedCategoryView != null) {
            selectedCategoryView.setSelected(false);
        }
        if (view != null) {
            view.setSelected(true);
            selectedCategoryView = view;
        }
    }

    private void bindCalculatorKeys() {
        int[] ids = {R.id.record_key_7, R.id.record_key_8, R.id.record_key_9, R.id.record_key_4,
                R.id.record_key_5, R.id.record_key_6, R.id.record_key_1, R.id.record_key_2,
                R.id.record_key_3, R.id.record_key_dot, R.id.record_key_0,
                R.id.record_key_add, R.id.record_key_subtract};
        String[] tokens = {"7", "8", "9", "4", "5", "6", "1", "2", "3", ".", "0", "+", "-"};
        for (int i = 0; i < ids.length; i++) {
            final String token = tokens[i];
            requireView().findViewById(ids[i]).setOnClickListener(v -> appendCalculatorToken(token));
        }
        binding.recordKeyAc.setOnClickListener(v -> {
            calculator.clear();
            updateCalculatorDisplay();
            binding.recordAmountInput.setText("");
        });
        binding.recordKeyBackspace.setOnClickListener(v -> {
            calculator.backspace();
            updateCalculatorDisplay();
        });
        binding.recordKeyEquals.setOnClickListener(v -> evaluateCalculator());
    }

    private void appendCalculatorToken(String token) {
        try {
            calculator.append(token);
            updateCalculatorDisplay();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void evaluateCalculator() {
        try {
            long cents = calculator.evaluateToCents();
            String amount = MoneyParser.formatCents(cents).replace("¥", "").trim();
            binding.recordAmountInput.setText(amount);
            binding.recordCalculatorDisplay.setText(amount);
            binding.recordCalculatorExpression.setText(calculator.getExpression());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCalculatorDisplay() {
        String expression = calculator.getExpression();
        binding.recordCalculatorExpression.setText(expression.isEmpty() ? "输入花销" : expression);
        binding.recordCalculatorDisplay.setText(expression.isEmpty() ? "0" : expression);
    }

    private void dateLabel() {
        binding.recordDateYear.setText(date.year() + "年");
        binding.recordDateMonth.setText(String.format(java.util.Locale.CHINA, "%02d月", date.month()));
        binding.recordDateDay.setText(String.format(java.util.Locale.CHINA, "%02d日", date.day()));
    }

    private void showDateDropdown(TextView anchor, DatePart part) {
        List<Integer> values;
        int current;
        if (part == DatePart.YEAR) {
            values = date.years();
            current = date.year();
        } else if (part == DatePart.MONTH) {
            values = date.months();
            current = date.month();
        } else {
            values = date.days();
            current = date.day();
        }
        List<String> labels = new ArrayList<>();
        for (Integer value : values) {
            labels.add(dateOptionLabel(part, value));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                R.layout.item_date_dropdown, R.id.date_dropdown_text, labels);
        ListView list = new ListView(requireContext());
        list.setDivider(null);
        list.setAdapter(adapter);
        PopupWindow popup = new PopupWindow(list, anchor.getWidth(), dp(300), true);
        popup.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.bg_date_dropdown_popup));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(6));
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        list.setOnItemClickListener((parent, view, position, id) -> {
            int selected = values.get(position);
            if (part == DatePart.YEAR) {
                date.selectYear(selected);
            } else if (part == DatePart.MONTH) {
                date.selectMonth(selected);
            } else {
                date.selectDay(selected);
            }
            dateLabel();
            popup.dismiss();
        });
        popup.showAsDropDown(anchor);
        int selectedIndex = values.indexOf(current);
        if (selectedIndex >= 0) {
            list.setSelection(selectedIndex);
        }
    }

    private String dateOptionLabel(DatePart part, int value) {
        if (part == DatePart.YEAR) return value + "年";
        return String.format(java.util.Locale.CHINA, "%02d%s", value,
                part == DatePart.MONTH ? "月" : "日");
    }

    private void save() {
        try {
            if (category == null) {
                Toast.makeText(requireContext(), "请选择分类", Toast.LENGTH_SHORT).show();
                return;
            }
            long cents = RecordAmountResolver.resolveCents(
                    String.valueOf(binding.recordAmountInput.getText()), calculator.getExpression());
            Transaction transaction = InMemoryLedgerRepository.getInstance().addTransaction(type, cents,
                    category, String.valueOf(binding.recordNoteInput.getText()), date.timeInMillis());
            BorrowingDirection borrowingDirection = BorrowingCategoryLink.directionFor(type, category);
            if (borrowingDirection == null) {
                showSaveSuccessDialog();
            } else {
                if (borrowingDirection == BorrowingDirection.BORROW) {
                    new AssetStore(requireContext()).addLiability(
                            InMemoryLedgerRepository.getInstance().getCurrentLedger().getId(), cents);
                }
                showBorrowingPrompt(transaction, borrowingDirection);
            }
        } catch (IllegalArgumentException exception) {
            binding.recordCalculatorExpression.setText(exception.getMessage());
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showSaveSuccessDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_record_success, null, false);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .create();
        content.findViewById(R.id.record_success_continue).setOnClickListener(view -> {
            dialog.dismiss();
            resetForNextRecord();
        });
        content.findViewById(R.id.record_success_details).setOnClickListener(view -> {
            dialog.dismiss();
            ((MainActivity) requireActivity()).showDestination(AppDestination.DETAILS);
        });
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.86f),
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private void showBorrowingPrompt(Transaction transaction, BorrowingDirection direction) {
        View content = getLayoutInflater().inflate(R.layout.dialog_borrowing_prompt, null, false);
        TextView message = content.findViewById(R.id.borrowing_prompt_message);
        message.setText(direction == BorrowingDirection.LEND
                ? "这笔借出已经记入账本，再补充对方和日期就能长期追踪。"
                : "这笔借入已经记入账本，再补充对方和日期就能长期追踪。");
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .create();
        content.findViewById(R.id.borrowing_prompt_skip).setOnClickListener(view -> {
            dialog.dismiss();
            showSaveSuccessDialog();
        });
        content.findViewById(R.id.borrowing_prompt_fill).setOnClickListener(view -> {
            dialog.dismiss();
            ((MainActivity) requireActivity()).showBorrowingComposer(direction,
                    transaction.getAmountInCents(), transaction.getOccurredAtMillis(), transaction.getId());
        });
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.86f),
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private void resetForNextRecord() {
        category = null;
        selectedCategoryView = null;
        calculator.clear();
        binding.recordAmountInput.setText("");
        binding.recordNoteInput.setText("");
        binding.recordEntryCard.setVisibility(View.GONE);
        updateCalculatorDisplay();
        binding.getRoot().smoothScrollTo(0, 0);
        renderCategories();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum DatePart { YEAR, MONTH, DAY }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

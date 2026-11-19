package com.example.pkmapp.charts;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.graphics.drawable.ColorDrawable;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.databinding.FragmentChartsBinding;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.CategoryIconResolver;
import com.example.pkmapp.record.MoneyParser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ChartsFragment extends Fragment {
    private static final String STATE_SELECTED_CHART_DATE = "selected_chart_date";
    private static final String STATE_SELECTED_CHART_PERIOD = "selected_chart_period";
    private static final String STATE_SELECTED_CHART_TYPE = "selected_chart_type";

    private FragmentChartsBinding binding;
    private TransactionType type = TransactionType.EXPENSE;
    private ChartDataCalculator.Period period = ChartDataCalculator.Period.MONTH;
    private Calendar selectedDate = Calendar.getInstance();
    private PopupWindow periodPopup;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
            @Nullable Bundle state) {
        binding = FragmentChartsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (state != null) {
            if (state.containsKey(STATE_SELECTED_CHART_DATE)) {
                selectedDate.setTimeInMillis(state.getLong(STATE_SELECTED_CHART_DATE));
            }
            if (state.containsKey(STATE_SELECTED_CHART_PERIOD)) {
                period = ChartDataCalculator.Period.valueOf(
                        state.getString(STATE_SELECTED_CHART_PERIOD));
            }
            if (state.containsKey(STATE_SELECTED_CHART_TYPE)) {
                type = TransactionType.valueOf(state.getString(STATE_SELECTED_CHART_TYPE));
            }
        }
        binding.pageBackButton.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.chartExpenseButton.setChecked(type == TransactionType.EXPENSE);
        binding.chartIncomeButton.setChecked(type == TransactionType.INCOME);
        binding.chartWeekButton.setChecked(period == ChartDataCalculator.Period.WEEK);
        binding.chartMonthButton.setChecked(period == ChartDataCalculator.Period.MONTH);
        binding.chartYearButton.setChecked(period == ChartDataCalculator.Period.YEAR);
        binding.chartExpenseButton.setOnClickListener(v -> {
            type = TransactionType.EXPENSE;
            render();
        });
        binding.chartIncomeButton.setOnClickListener(v -> {
            type = TransactionType.INCOME;
            render();
        });
        binding.chartWeekButton.setOnClickListener(v -> {
            dismissPeriodPopup();
            period = ChartDataCalculator.Period.WEEK;
            render();
        });
        binding.chartMonthButton.setOnClickListener(v -> {
            dismissPeriodPopup();
            period = ChartDataCalculator.Period.MONTH;
            render();
        });
        binding.chartYearButton.setOnClickListener(v -> {
            dismissPeriodPopup();
            period = ChartDataCalculator.Period.YEAR;
            render();
        });
        binding.chartPeriodPrevious.setOnClickListener(v -> shiftSelectedWeek(-1));
        binding.chartPeriodNext.setOnClickListener(v -> shiftSelectedWeek(1));
        binding.chartPeriodSelector.setOnClickListener(v -> togglePeriodSelector());
        render();
    }

    private void render() {
        InMemoryLedgerRepository repository = InMemoryLedgerRepository.getInstance();
        List<Transaction> transactions = repository.getTransactionsForCurrentLedger();
        Calendar reference = (Calendar) selectedDate.clone();
        long[] points = ChartDataCalculator.bucketTotals(transactions, type, reference, period);
        int[] pointCounts = ChartDataCalculator.bucketCounts(transactions, type, reference, period);
        Map<String, ChartDataCalculator.CategorySummary> ranks =
                ChartDataCalculator.categorySummary(transactions, type, reference, period);
        long total = ChartDataCalculator.total(points);
        long average = ChartDataCalculator.average(points);
        long peak = ChartDataCalculator.maximum(points);
        long previousTotal = ChartDataCalculator.totalForPeriod(transactions, type, reference, period, -1);
        boolean expense = type == TransactionType.EXPENSE;
        String moneyType = expense ? "支出" : "收入";
        binding.chartSummary.setText(moneyType + "趋势 · " + periodName());
        binding.chartTotalLabel.setText("总" + moneyType);
        binding.chartTotal.setText(MoneyParser.formatCents(total));
        binding.chartAverage.setText((period == ChartDataCalculator.Period.YEAR ? "月均" : "日均")
                + moneyType + "  " + MoneyParser.formatCents(average));
        binding.chartRankingCaption.setText(expense ? "支出占比" : "收入占比");
        binding.chartComparison.setText(comparisonText(total, previousTotal));
        binding.chartTrendView.setValues(points, pointCounts, pointLabels(reference, points.length),
                startLabel(points.length), middleLabel(points.length), endLabel(points.length),
                moneyType, "最高" + (expense ? "消费" : "收入") + " "
                        + MoneyParser.formatCents(peak));
        renderRanking(ranks);
        binding.chartPeriodPrevious.setVisibility(period == ChartDataCalculator.Period.WEEK
                ? View.VISIBLE : View.INVISIBLE);
        binding.chartPeriodNext.setVisibility(period == ChartDataCalculator.Period.WEEK
                ? View.VISIBLE : View.INVISIBLE);
        boolean selectablePeriod = period != ChartDataCalculator.Period.WEEK;
        binding.chartPeriodSelector.setText(periodName() + (selectablePeriod ? "  ▾" : ""));
        binding.chartPeriodSelector.setBackgroundResource(selectablePeriod
                ? R.drawable.bg_period_selector : android.R.color.transparent);
        binding.chartPeriodSelector.setClickable(selectablePeriod);
        binding.chartPeriodSelector.setFocusable(selectablePeriod);
        binding.chartPeriodSelector.setContentDescription(selectablePeriod
                ? "选择统计时间，当前" + periodName() : periodName());
    }

    private String[] pointLabels(Calendar reference, int pointCount) {
        String[] labels = new String[pointCount];
        Calendar periodStart = ChartDataCalculator.startOfPeriod(reference, period, 0);
        for (int i = 0; i < pointCount; i++) {
            Calendar pointDate = (Calendar) periodStart.clone();
            if (period == ChartDataCalculator.Period.YEAR) {
                pointDate.add(Calendar.MONTH, i);
                labels[i] = String.format(java.util.Locale.CHINA, "%d年%d月",
                        pointDate.get(Calendar.YEAR), pointDate.get(Calendar.MONTH) + 1);
            } else {
                pointDate.add(Calendar.DAY_OF_MONTH, i);
                labels[i] = period == ChartDataCalculator.Period.WEEK
                        ? String.format(java.util.Locale.CHINA, "%d月%d日 周%s",
                                pointDate.get(Calendar.MONTH) + 1,
                                pointDate.get(Calendar.DAY_OF_MONTH),
                                new String[]{"一", "二", "三", "四", "五", "六", "日"}[i])
                        : String.format(java.util.Locale.CHINA, "%d月%d日",
                                pointDate.get(Calendar.MONTH) + 1,
                                pointDate.get(Calendar.DAY_OF_MONTH));
            }
        }
        return labels;
    }

    private void renderRanking(Map<String, ChartDataCalculator.CategorySummary> ranks) {
        binding.chartRankingGroup.removeAllViews();
        if (ranks.isEmpty()) {
            TextView empty = text("本周期还没有" + (type == TransactionType.EXPENSE ? "支出" : "收入")
                    + "记录", 13, R.color.wood_brown);
            empty.setGravity(Gravity.CENTER);
            binding.chartRankingGroup.addView(empty, new LinearLayout.LayoutParams(-1, dp(60)));
            return;
        }
        List<Map.Entry<String, ChartDataCalculator.CategorySummary>> entries =
                new ArrayList<>(ranks.entrySet());
        entries.sort(Comparator.comparingLong(
                (Map.Entry<String, ChartDataCalculator.CategorySummary> entry) ->
                        entry.getValue().getAmountInCents()).reversed());
        int rank = 1;
        for (Map.Entry<String, ChartDataCalculator.CategorySummary> entry : entries) {
            if (rank > 3) break;
            String category = entry.getKey();
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setContentDescription(String.format(LocaleHolder.ROOT, "%02d，%s，%s",
                    rank, category, MoneyParser.formatCents(entry.getValue().getAmountInCents())));

            TextView number = text(String.format(LocaleHolder.ROOT, "%02d", rank), 14, R.color.warm_orange);
            number.setTypeface(null, Typeface.BOLD);
            row.addView(number, new LinearLayout.LayoutParams(dp(30), dp(44)));

            ImageView icon = new ImageView(requireContext());
            icon.setContentDescription(category + "分类");
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImageResource(iconForCategory(category));
            GradientDrawable iconSurface = new GradientDrawable();
            iconSurface.setShape(GradientDrawable.OVAL);
            iconSurface.setColor(ContextCompat.getColor(requireContext(), R.color.paper_deep));
            icon.setBackground(iconSurface);
            row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout detail = new LinearLayout(requireContext());
            detail.setOrientation(LinearLayout.VERTICAL);
            detail.setPadding(dp(10), 0, dp(6), 0);
            TextView name = text(category, 15, R.color.ink);
            name.setTypeface(null, Typeface.BOLD);
            TextView count = text(periodCountName() + " " + entry.getValue().getCount() + " 笔",
                    12, R.color.wood_brown);
            detail.addView(name);
            detail.addView(count);
            row.addView(detail, new LinearLayout.LayoutParams(0, dp(48), 1));

            TextView amount = text(MoneyParser.formatCents(entry.getValue().getAmountInCents()), 14,
                    type == TransactionType.INCOME ? R.color.forest_green : R.color.expense_red);
            amount.setTypeface(null, Typeface.BOLD);
            row.addView(amount, new LinearLayout.LayoutParams(-2, dp(44)));
            row.setBackgroundResource(R.drawable.bg_transaction_row);
            binding.chartRankingGroup.addView(row, new LinearLayout.LayoutParams(-1, dp(60)));
            rank++;
        }
    }

    private int iconForCategory(String category) {
        int resource = getResources().getIdentifier(CategoryIconResolver.resourceName(type, category),
                "drawable", requireContext().getPackageName());
        return resource == 0 ? (type == TransactionType.INCOME
                ? R.drawable.norm_type_income_allowance : R.drawable.norm_type_expense_custom) : resource;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(requireContext(), color));
        return view;
    }

    private String periodName() {
        Calendar start = ChartDataCalculator.startOfPeriod(selectedDate, period, 0);
        switch (period) {
            case WEEK:
                Calendar end = (Calendar) start.clone();
                end.add(Calendar.DAY_OF_MONTH, 6);
                return String.format(LocaleHolder.ROOT, "%d年%d月%d日 - %d月%d日",
                        start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1,
                        start.get(Calendar.DAY_OF_MONTH), end.get(Calendar.MONTH) + 1,
                        end.get(Calendar.DAY_OF_MONTH));
            case YEAR:
                return String.format(LocaleHolder.ROOT, "%d年", start.get(Calendar.YEAR));
            case MONTH:
            default:
                return String.format(LocaleHolder.ROOT, "%d年%d月",
                        start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1);
        }
    }

    private String periodCountName() {
        switch (period) {
            case WEEK: return "本周";
            case YEAR: return "本年";
            case MONTH:
            default: return "本月";
        }
    }

    private void shiftSelectedWeek(int amount) {
        if (period != ChartDataCalculator.Period.WEEK) {
            return;
        }
        selectedDate.add(Calendar.DAY_OF_MONTH, amount * 7);
        render();
    }

    private void togglePeriodSelector() {
        if (periodPopup != null && periodPopup.isShowing()) {
            periodPopup.dismiss();
            return;
        }
        if (period == ChartDataCalculator.Period.WEEK) {
            return;
        }

        final boolean monthMode = period == ChartDataCalculator.Period.MONTH;
        final int[] chosenYear = {selectedDate.get(Calendar.YEAR)};
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(ContextCompat.getDrawable(requireContext(),
                R.drawable.bg_date_dropdown_popup));

        TextView title = text(monthMode ? "选择统计年月" : "选择统计年份", 14, R.color.wood_brown);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(4), 0, 0, dp(4));
        card.addView(title, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout yearRow = new LinearLayout(requireContext());
        yearRow.setGravity(Gravity.CENTER);
        TextView previousYear = yearArrow("上一年", "‹");
        TextView yearLabel = text(chosenYear[0] + "年", 17, R.color.forest_green);
        yearLabel.setGravity(Gravity.CENTER);
        yearLabel.setTypeface(null, Typeface.BOLD);
        TextView nextYear = yearArrow("下一年", "›");
        yearRow.addView(previousYear, new LinearLayout.LayoutParams(dp(48), dp(44)));
        yearRow.addView(yearLabel, new LinearLayout.LayoutParams(0, dp(44), 1));
        yearRow.addView(nextYear, new LinearLayout.LayoutParams(dp(48), dp(44)));
        card.addView(yearRow);

        Runnable refreshYear = () -> {
            yearLabel.setText(chosenYear[0] + "年");
            if (!monthMode) {
                setSelectedDate(chosenYear[0], Calendar.JANUARY, 1);
                render();
            }
        };
        previousYear.setOnClickListener(v -> {
            if (chosenYear[0] > 1970) {
                chosenYear[0]--;
                refreshYear.run();
            }
        });
        nextYear.setOnClickListener(v -> {
            if (chosenYear[0] < 2100) {
                chosenYear[0]++;
                refreshYear.run();
            }
        });

        if (monthMode) {
            TextView monthHeading = text("月份", 12, R.color.wood_brown);
            monthHeading.setPadding(dp(4), dp(4), 0, dp(4));
            card.addView(monthHeading, new LinearLayout.LayoutParams(-1, dp(28)));
            for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
                LinearLayout monthRow = new LinearLayout(requireContext());
                monthRow.setGravity(Gravity.CENTER);
                for (int column = 0; column < 4; column++) {
                    int month = rowIndex * 4 + column;
                    boolean selected = chosenYear[0] == selectedDate.get(Calendar.YEAR)
                            && month == selectedDate.get(Calendar.MONTH);
                    TextView monthCell = text((month + 1) + "月", 13,
                            selected ? android.R.color.white : R.color.wood_brown);
                    monthCell.setGravity(Gravity.CENTER);
                    monthCell.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
                    monthCell.setBackground(monthCellBackground(selected));
                    monthCell.setContentDescription((month + 1) + "月"
                            + (selected ? "，当前选择" : ""));
                    monthCell.setClickable(true);
                    monthCell.setFocusable(true);
                    LinearLayout.LayoutParams cellParams =
                            new LinearLayout.LayoutParams(0, dp(44), 1);
                    cellParams.setMargins(dp(3), dp(3), dp(3), dp(3));
                    monthRow.addView(monthCell, cellParams);
                    monthCell.setOnClickListener(v -> {
                        setSelectedDate(chosenYear[0], month, 1);
                        render();
                        if (periodPopup != null) periodPopup.dismiss();
                    });
                }
                card.addView(monthRow, new LinearLayout.LayoutParams(-1, dp(50)));
            }
        }

        periodPopup = new PopupWindow(card, binding.chartPeriodSelector.getWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        periodPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        periodPopup.setOutsideTouchable(true);
        periodPopup.setElevation(dp(8));
        periodPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        periodPopup.setOnDismissListener(() -> periodPopup = null);
        periodPopup.showAsDropDown(binding.chartPeriodSelector, 0, dp(4),
                Gravity.CENTER_HORIZONTAL);
    }

    private void dismissPeriodPopup() {
        if (periodPopup != null) {
            periodPopup.dismiss();
            periodPopup = null;
        }
    }

    private TextView yearArrow(String description, String label) {
        TextView arrow = text(label, 24, R.color.wood_brown);
        arrow.setGravity(Gravity.CENTER);
        arrow.setContentDescription(description);
        arrow.setBackground(monthCellBackground(false));
        arrow.setClickable(true);
        arrow.setFocusable(true);
        return arrow;
    }

    private GradientDrawable monthCellBackground(boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(10));
        background.setColor(ContextCompat.getColor(requireContext(), selected
                ? R.color.forest_green : R.color.paper_card));
        background.setStroke(dp(1), ContextCompat.getColor(requireContext(), selected
                ? R.color.forest_green : R.color.paper_deep));
        return background;
    }

    private void setSelectedDate(int year, int month, int day) {
        selectedDate.set(year, month, day, 12, 0, 0);
        selectedDate.set(Calendar.MILLISECOND, 0);
    }

    private String comparisonText(long current, long previous) {
        String previousName;
        switch (period) {
            case WEEK: previousName = "上周"; break;
            case YEAR: previousName = "上年"; break;
            case MONTH:
            default: previousName = "上月"; break;
        }
        if (previous == 0L) {
            return current == 0L ? "暂无对比数据" : "较" + previousName + " 新增";
        }
        double change = (current - previous) * 100.0 / previous;
        String sign = change >= 0 ? "+" : "−";
        return String.format(java.util.Locale.CHINA, "较%s %s%.1f%%", previousName, sign,
                Math.abs(change));
    }

    private String startLabel(int pointCount) {
        if (period == ChartDataCalculator.Period.WEEK) {
            Calendar start = ChartDataCalculator.startOfPeriod(selectedDate, period, 0);
            return String.format(LocaleHolder.ROOT, "周一\n%d.%02d",
                    start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH));
        }
        return period == ChartDataCalculator.Period.YEAR ? "1月" : "01日";
    }

    private String middleLabel(int pointCount) {
        if (period == ChartDataCalculator.Period.WEEK) return "周四";
        if (period == ChartDataCalculator.Period.YEAR) return "6月";
        return String.format(java.util.Locale.CHINA, "%02d日", Math.max(1, (pointCount + 1) / 2));
    }

    private String endLabel(int pointCount) {
        if (period == ChartDataCalculator.Period.WEEK) {
            Calendar end = ChartDataCalculator.startOfPeriod(selectedDate, period, 0);
            end.add(Calendar.DAY_OF_MONTH, 6);
            return String.format(LocaleHolder.ROOT, "周日\n%d.%02d",
                    end.get(Calendar.MONTH) + 1, end.get(Calendar.DAY_OF_MONTH));
        }
        if (period == ChartDataCalculator.Period.YEAR) return "12月";
        return String.format(java.util.Locale.CHINA, "%02d日", pointCount);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        state.putLong(STATE_SELECTED_CHART_DATE, selectedDate.getTimeInMillis());
        state.putString(STATE_SELECTED_CHART_PERIOD, period.name());
        state.putString(STATE_SELECTED_CHART_TYPE, type.name());
        super.onSaveInstanceState(state);
    }

    private static final class LocaleHolder {
        private static final java.util.Locale ROOT = java.util.Locale.ROOT;
    }

    @Override
    public void onDestroyView() {
        dismissPeriodPopup();
        binding = null;
        super.onDestroyView();
    }
}

package com.example.pkmapp.charts;

import static com.example.pkmapp.forest.ForestUi.DEEP;
import static com.example.pkmapp.forest.ForestUi.MUTED;
import static com.example.pkmapp.forest.ForestUi.ripple;
import static com.example.pkmapp.forest.ForestUi.rounded;
import static com.example.pkmapp.forest.ForestUi.row;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.databinding.FragmentChartsBinding;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.ForestSheetHelper;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.life.LifeSharedViewModel;
import com.example.pkmapp.life.LifeSnapshot;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.CategoryIconResolver;
import com.example.pkmapp.record.MoneyParser;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChartsFragment extends Fragment {
    private static final String STATE_SELECTED_CHART_DATE = "selected_chart_date";
    private static final String STATE_SELECTED_CHART_PERIOD = "selected_chart_period";
    private static final String STATE_SELECTED_CHART_TYPE = "selected_chart_type";

    private LifeSharedViewModel viewModel;
    private FragmentChartsBinding binding;
    private TransactionType type = TransactionType.EXPENSE;
    private ChartDataCalculator.Period period = ChartDataCalculator.Period.MONTH;
    private Calendar selectedDate = Calendar.getInstance();
    private BottomSheetDialog activeSheet;

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

        View.OnClickListener selectExpense = v -> {
            type = TransactionType.EXPENSE;
            render();
        };
        View.OnClickListener selectIncome = v -> {
            type = TransactionType.INCOME;
            render();
        };
        binding.chartExpenseTab.setOnClickListener(selectExpense);
        binding.chartExpenseButton.setOnClickListener(selectExpense);
        binding.chartIncomeTab.setOnClickListener(selectIncome);
        binding.chartIncomeButton.setOnClickListener(selectIncome);
        binding.chartModeMascot.setOnClickListener(v -> {
            type = type == TransactionType.EXPENSE ? TransactionType.INCOME : TransactionType.EXPENSE;
            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).withEndAction(() ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
            render();
        });

        binding.chartWeekButton.setOnClickListener(v -> {
            dismissActiveSheet();
            period = ChartDataCalculator.Period.WEEK;
            render();
        });
        binding.chartMonthButton.setOnClickListener(v -> {
            dismissActiveSheet();
            period = ChartDataCalculator.Period.MONTH;
            render();
        });
        binding.chartYearButton.setOnClickListener(v -> {
            dismissActiveSheet();
            period = ChartDataCalculator.Period.YEAR;
            render();
        });
        binding.chartPeriodPrevious.setOnClickListener(v -> shiftSelectedPeriod(-1));
        binding.chartPeriodNext.setOnClickListener(v -> shiftSelectedPeriod(1));
        binding.chartPeriodSelector.setOnClickListener(v -> openPeriodSheet());

        ForestMotion.bind(binding.chartWeekButton);
        ForestMotion.bind(binding.chartMonthButton);
        ForestMotion.bind(binding.chartYearButton);
        ForestMotion.bind(binding.chartPeriodPrevious);
        ForestMotion.bind(binding.chartPeriodNext);
        ForestMotion.bind(binding.chartPeriodSelector);

        viewModel = LifeSharedViewModel.get(requireActivity());
        viewModel.observe(getViewLifecycleOwner(), this::renderSnapshot);
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
        Context ctx = requireContext();
        boolean expense = type == TransactionType.EXPENSE;

        // 1. Editorial underline primary tabs (Expense / Income)
        styleEditorialTab(binding.chartExpenseButton, binding.chartExpenseIndicator, expense);
        styleEditorialTab(binding.chartIncomeButton, binding.chartIncomeIndicator, !expense);
        binding.chartModeMascot.setImageResource(
                expense ? R.drawable.chart_expense_character : R.drawable.chart_income_character);

        // 2. Compact right-aligned granularity toggles (W / M / Y)
        styleCompactPeriodChip(binding.chartWeekButton, period == ChartDataCalculator.Period.WEEK);
        styleCompactPeriodChip(binding.chartMonthButton, period == ChartDataCalculator.Period.MONTH);
        styleCompactPeriodChip(binding.chartYearButton, period == ChartDataCalculator.Period.YEAR);

        List<Transaction> transactions = snapshot.getTransactions();
        Calendar reference = (Calendar) selectedDate.clone();
        long[] points = ChartDataCalculator.bucketTotals(transactions, type, reference, period);
        int[] pointCounts = ChartDataCalculator.bucketCounts(transactions, type, reference, period);
        Map<String, ChartDataCalculator.CategorySummary> ranks =
                ChartDataCalculator.categorySummary(transactions, type, reference, period);
        long total = ChartDataCalculator.total(points);
        long average = ChartDataCalculator.average(points);
        long peak = ChartDataCalculator.maximum(points);
        long previousTotal = ChartDataCalculator.totalForPeriod(transactions, type, reference, period, -1);
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

        // 3. Integrated borderless card-header date stepper (works across Week / Month / Year)
        boolean isWeek = period == ChartDataCalculator.Period.WEEK;
        String prevDesc = isWeek ? "上一周" : (period == ChartDataCalculator.Period.YEAR ? "上一年" : "上个月");
        String nextDesc = isWeek ? "下一周" : (period == ChartDataCalculator.Period.YEAR ? "下一年" : "下个月");
        binding.chartPeriodPrevious.setVisibility(View.VISIBLE);
        binding.chartPeriodNext.setVisibility(View.VISIBLE);
        binding.chartPeriodPrevious.setContentDescription(prevDesc);
        binding.chartPeriodNext.setContentDescription(nextDesc);
        binding.chartPeriodPrevious.setBackground(
                ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 999), false));
        binding.chartPeriodNext.setBackground(
                ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 999), false));
        binding.chartPeriodSelector.setTextColor(ForestSheetHelper.INK);
        if (isWeek) {
            binding.chartPeriodSelector.setBackground(null);
            binding.chartPeriodSelector.setText(periodName());
            binding.chartPeriodSelector.setClickable(false);
            binding.chartPeriodSelector.setFocusable(false);
            binding.chartPeriodSelector.setContentDescription(periodName());
        } else {
            String label = periodName();
            SpannableString formatted = new SpannableString(label + " ▾");
            formatted.setSpan(new ForegroundColorSpan(0xff7b5c3b), label.length(), formatted.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            formatted.setSpan(new RelativeSizeSpan(0.78f), label.length(), formatted.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            binding.chartPeriodSelector.setBackground(
                    ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 10), false));
            binding.chartPeriodSelector.setText(formatted);
            binding.chartPeriodSelector.setClickable(true);
            binding.chartPeriodSelector.setFocusable(true);
            binding.chartPeriodSelector.setContentDescription("选择统计时间，当前" + label);
        }
    }

    private void styleEditorialTab(TextView label, View indicator, boolean selected) {
        label.setTextColor(selected ? ForestSheetHelper.INK : 0xff827b6d);
        label.setTextSize(selected ? 22f : 19f);
        label.setTypeface(Typeface.create("serif", selected ? Typeface.BOLD : Typeface.NORMAL));
        indicator.setBackground(rounded(
                requireContext(),
                selected ? ForestSheetHelper.PINE : Color.TRANSPARENT,
                0,
                99
        ));
    }

    private void styleCompactPeriodChip(TextView chip, boolean selected) {
        Context ctx = requireContext();
        chip.setTextColor(selected ? DEEP : MUTED);
        chip.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif",
                selected ? Typeface.BOLD : Typeface.NORMAL));
        chip.setBackground(ripple(
                ctx,
                rounded(
                        ctx,
                        selected ? ForestSheetHelper.PORCELAIN : Color.TRANSPARENT,
                        selected ? 0x33315e3a : 0,
                        999
                ),
                false
        ));
    }

    private String[] pointLabels(Calendar reference, int pointCount) {
        String[] labels = new String[pointCount];
        Calendar periodStart = ChartDataCalculator.startOfPeriod(reference, period, 0);
        for (int i = 0; i < pointCount; i++) {
            Calendar pointDate = (Calendar) periodStart.clone();
            if (period == ChartDataCalculator.Period.YEAR) {
                pointDate.add(Calendar.MONTH, i);
                labels[i] = String.format(Locale.CHINA, "%d年%d月",
                        pointDate.get(Calendar.YEAR), pointDate.get(Calendar.MONTH) + 1);
            } else {
                pointDate.add(Calendar.DAY_OF_MONTH, i);
                labels[i] = period == ChartDataCalculator.Period.WEEK
                        ? String.format(Locale.CHINA, "%d月%d日 周%s",
                                pointDate.get(Calendar.MONTH) + 1,
                                pointDate.get(Calendar.DAY_OF_MONTH),
                                new String[]{"一", "二", "三", "四", "五", "六", "日"}[i])
                        : String.format(Locale.CHINA, "%d月%d日",
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
            number.setFontFeatureSettings("tnum");
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
            amount.setFontFeatureSettings("tnum");
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
                return String.format(LocaleHolder.ROOT, "%d.%02d – %d.%02d",
                        start.get(Calendar.MONTH) + 1,
                        start.get(Calendar.DAY_OF_MONTH),
                        end.get(Calendar.MONTH) + 1,
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

    private void shiftSelectedPeriod(int amount) {
        switch (period) {
            case WEEK:
                selectedDate.add(Calendar.DAY_OF_MONTH, amount * 7);
                break;
            case YEAR:
                selectedDate.add(Calendar.YEAR, amount);
                break;
            case MONTH:
            default:
                selectedDate.add(Calendar.MONTH, amount);
                break;
        }
        render();
    }

    private void openPeriodSheet() {
        if (period == ChartDataCalculator.Period.WEEK) {
            return;
        }
        dismissActiveSheet();
        Context ctx = requireContext();
        final boolean monthMode = period == ChartDataCalculator.Period.MONTH;
        final int[] chosenYear = {selectedDate.get(Calendar.YEAR)};

        BottomSheetDialog sheet = ForestSheetHelper.createSheet(ctx);
        activeSheet = sheet;
        sheet.setOnDismissListener(d -> {
            if (activeSheet == sheet) activeSheet = null;
        });

        LinearLayout root = ForestSheetHelper.createSheetRoot(
                ctx,
                sheet,
                monthMode ? "选择统计年月" : "选择统计年份",
                "切换周期查看历史收支趋势与分类占比"
        );

        LinearLayout yearRow = row(ctx);
        yearRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView prevYear = yearArrow(ctx, "上一年", "‹");
        TextView yearLabel = ForestUi.heading(ctx, chosenYear[0] + "年", 19);
        yearLabel.setTypeface(Typeface.create("serif", Typeface.BOLD));
        yearLabel.setFontFeatureSettings("tnum");
        yearLabel.setGravity(Gravity.CENTER);
        yearLabel.setTextColor(ForestSheetHelper.INK);
        TextView nextYear = yearArrow(ctx, "下一年", "›");
        yearRow.addView(prevYear, new LinearLayout.LayoutParams(dp(44), dp(42)));
        yearRow.addView(yearLabel, new LinearLayout.LayoutParams(0, dp(42), 1f));
        yearRow.addView(nextYear, new LinearLayout.LayoutParams(dp(44), dp(42)));
        root.addView(yearRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout monthGrid = ForestUi.column(ctx);
        Runnable renderMonths = () -> {
            monthGrid.removeAllViews();
            if (!monthMode) return;
            for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
                LinearLayout mRow = row(ctx);
                for (int col = 0; col < 4; col++) {
                    int month = rowIndex * 4 + col;
                    boolean selected = chosenYear[0] == selectedDate.get(Calendar.YEAR)
                            && month == selectedDate.get(Calendar.MONTH);
                    TextView cell = ForestUi.text(
                            ctx,
                            (month + 1) + "月",
                            13,
                            selected ? 0xfffffefa : ForestSheetHelper.INK
                    );
                    cell.setGravity(Gravity.CENTER);
                    cell.setFontFeatureSettings("tnum");
                    cell.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif",
                            selected ? Typeface.BOLD : Typeface.NORMAL));
                    cell.setBackground(ripple(
                            ctx,
                            rounded(
                                    ctx,
                                    selected ? ForestSheetHelper.PINE : Color.TRANSPARENT,
                                    selected ? 0 : 0x1f5c4938,
                                    12
                            ),
                            selected
                    ));
                    cell.setContentDescription((month + 1) + "月" + (selected ? "，当前选择" : ""));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
                    lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                    mRow.addView(cell, lp);
                    cell.setOnClickListener(v -> {
                        setSelectedDate(chosenYear[0], month, 1);
                        render();
                        sheet.dismiss();
                    });
                }
                monthGrid.addView(mRow, new LinearLayout.LayoutParams(-1, -2));
            }
        };

        prevYear.setOnClickListener(v -> {
            if (chosenYear[0] > 1970) {
                chosenYear[0]--;
                yearLabel.setText(chosenYear[0] + "年");
                if (monthMode) {
                    renderMonths.run();
                } else {
                    setSelectedDate(chosenYear[0], Calendar.JANUARY, 1);
                    render();
                }
            }
        });
        nextYear.setOnClickListener(v -> {
            if (chosenYear[0] < 2100) {
                chosenYear[0]++;
                yearLabel.setText(chosenYear[0] + "年");
                if (monthMode) {
                    renderMonths.run();
                } else {
                    setSelectedDate(chosenYear[0], Calendar.JANUARY, 1);
                    render();
                }
            }
        });

        if (monthMode) {
            ForestSheetHelper.addHairline(root, 12, 10);
            renderMonths.run();
            root.addView(monthGrid, new LinearLayout.LayoutParams(-1, -2));
        } else {
            ForestUi.add(root, ForestSheetHelper.primaryButton(ctx, "确认年份", v -> sheet.dismiss()), 16);
        }

        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root);
        sheet.show();
    }

    private void dismissActiveSheet() {
        if (activeSheet != null) {
            activeSheet.dismiss();
            activeSheet = null;
        }
    }

    private TextView yearArrow(Context ctx, String description, String label) {
        TextView arrow = ForestUi.text(ctx, label, 22, DEEP);
        arrow.setGravity(Gravity.CENTER);
        arrow.setContentDescription(description);
        arrow.setBackground(ripple(ctx, rounded(ctx, 0x125c4938, 0, 999), false));
        ForestMotion.bind(arrow);
        return arrow;
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
        if (current == 0L) {
            return previous == 0L ? "暂无对比数据" : "本周期暂无记录";
        }
        if (previous == 0L) {
            return "较" + previousName + " 新增";
        }
        double change = (current - previous) * 100.0 / previous;
        String sign = change >= 0 ? "+" : "−";
        return String.format(Locale.CHINA, "较%s %s%.1f%%", previousName, sign,
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
        return String.format(Locale.CHINA, "%02d日", Math.max(1, (pointCount + 1) / 2));
    }

    private String endLabel(int pointCount) {
        if (period == ChartDataCalculator.Period.WEEK) {
            Calendar end = ChartDataCalculator.startOfPeriod(selectedDate, period, 0);
            end.add(Calendar.DAY_OF_MONTH, 6);
            return String.format(LocaleHolder.ROOT, "周日\n%d.%02d",
                    end.get(Calendar.MONTH) + 1, end.get(Calendar.DAY_OF_MONTH));
        }
        if (period == ChartDataCalculator.Period.YEAR) return "12月";
        return String.format(Locale.CHINA, "%02d日", pointCount);
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
        private static final Locale ROOT = Locale.ROOT;
    }

    @Override
    public void onDestroyView() {
        dismissActiveSheet();
        binding = null;
        super.onDestroyView();
    }
}

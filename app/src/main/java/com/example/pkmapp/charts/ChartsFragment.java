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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChartsFragment extends Fragment {
    private FragmentChartsBinding binding;
    private TransactionType type = TransactionType.EXPENSE;
    private ChartDataCalculator.Period period = ChartDataCalculator.Period.MONTH;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
            @Nullable Bundle state) {
        binding = FragmentChartsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        binding.pageBackButton.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.HOME));
        binding.chartExpenseButton.setChecked(true);
        binding.chartMonthButton.setChecked(true);
        binding.chartExpenseButton.setOnClickListener(v -> {
            type = TransactionType.EXPENSE;
            render();
        });
        binding.chartIncomeButton.setOnClickListener(v -> {
            type = TransactionType.INCOME;
            render();
        });
        binding.chartWeekButton.setOnClickListener(v -> {
            period = ChartDataCalculator.Period.WEEK;
            render();
        });
        binding.chartMonthButton.setOnClickListener(v -> {
            period = ChartDataCalculator.Period.MONTH;
            render();
        });
        binding.chartYearButton.setOnClickListener(v -> {
            period = ChartDataCalculator.Period.YEAR;
            render();
        });
        render();
    }

    private void render() {
        InMemoryLedgerRepository repository = InMemoryLedgerRepository.getInstance();
        List<Transaction> transactions = repository.getTransactionsForCurrentLedger();
        Calendar reference = Calendar.getInstance();
        long[] points = ChartDataCalculator.bucketTotals(transactions, type, reference, period);
        int[] pointCounts = ChartDataCalculator.bucketCounts(transactions, type, reference, period);
        Map<String, Long> ranks = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Calendar periodStart = ChartDataCalculator.startOfPeriod(reference, period, 0);
        Calendar periodEnd = ChartDataCalculator.endOfPeriod(reference, period, 0);
        for (Transaction transaction : transactions) {
            if (transaction.getType() == type
                    && transaction.getOccurredAtMillis() >= periodStart.getTimeInMillis()
                    && transaction.getOccurredAtMillis() < periodEnd.getTimeInMillis()) {
                ranks.put(transaction.getCategory(), ranks.getOrDefault(transaction.getCategory(), 0L)
                        + transaction.getAmountInCents());
                counts.put(transaction.getCategory(), counts.getOrDefault(transaction.getCategory(), 0) + 1);
            }
        }
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
        renderRanking(ranks, counts);
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
                        ? "周" + new String[]{"一", "二", "三", "四", "五", "六", "日"}[i]
                        : String.format(java.util.Locale.CHINA, "%d月%d日",
                                pointDate.get(Calendar.MONTH) + 1,
                                pointDate.get(Calendar.DAY_OF_MONTH));
            }
        }
        return labels;
    }

    private void renderRanking(Map<String, Long> ranks, Map<String, Integer> counts) {
        binding.chartRankingGroup.removeAllViews();
        if (ranks.isEmpty()) {
            TextView empty = text("本周期还没有" + (type == TransactionType.EXPENSE ? "支出" : "收入")
                    + "记录", 13, R.color.wood_brown);
            empty.setGravity(Gravity.CENTER);
            binding.chartRankingGroup.addView(empty, new LinearLayout.LayoutParams(-1, dp(60)));
            return;
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<>(ranks.entrySet());
        entries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        int rank = 1;
        for (Map.Entry<String, Long> entry : entries) {
            if (rank > 3) break;
            String category = entry.getKey();
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setContentDescription(String.format(LocaleHolder.ROOT, "%02d，%s，%s",
                    rank, category, MoneyParser.formatCents(entry.getValue())));

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
            TextView count = text("本月 " + counts.getOrDefault(category, 0) + " 笔", 12, R.color.wood_brown);
            detail.addView(name);
            detail.addView(count);
            row.addView(detail, new LinearLayout.LayoutParams(0, dp(48), 1));

            TextView amount = text(MoneyParser.formatCents(entry.getValue()), 14,
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
        switch (period) {
            case WEEK: return "本周";
            case YEAR: return "本年";
            case MONTH:
            default: return "本月";
        }
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
        return period == ChartDataCalculator.Period.WEEK ? "周一"
                : period == ChartDataCalculator.Period.YEAR ? "1月" : "01日";
    }

    private String middleLabel(int pointCount) {
        if (period == ChartDataCalculator.Period.WEEK) return "周四";
        if (period == ChartDataCalculator.Period.YEAR) return "6月";
        return String.format(java.util.Locale.CHINA, "%02d日", Math.max(1, (pointCount + 1) / 2));
    }

    private String endLabel(int pointCount) {
        if (period == ChartDataCalculator.Period.WEEK) return "周日";
        if (period == ChartDataCalculator.Period.YEAR) return "12月";
        return String.format(java.util.Locale.CHINA, "%02d日", pointCount);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LocaleHolder {
        private static final java.util.Locale ROOT = java.util.Locale.ROOT;
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}

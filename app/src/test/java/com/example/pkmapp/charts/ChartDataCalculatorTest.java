package com.example.pkmapp.charts;

import static org.junit.Assert.assertEquals;

import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public final class ChartDataCalculatorTest {
    @Test
    public void averageAndPeakDescribeTheVisiblePeriodBuckets() {
        long[] values = {100L, 350L, 0L, 150L};

        assertEquals(150L, ChartDataCalculator.average(values));
        assertEquals(350L, ChartDataCalculator.maximum(values));
    }

    @Test
    public void monthBucketsUseOnlySelectedTypeAndMonth() {
        List<Transaction> transactions = Arrays.asList(
                transaction("expense-8", TransactionType.EXPENSE, 100L,
                        "餐饮", date(2026, Calendar.SEPTEMBER, 8)),
                transaction("expense-2", TransactionType.EXPENSE, 250L,
                        "交通", date(2026, Calendar.SEPTEMBER, 2)),
                transaction("income-8", TransactionType.INCOME, 500L,
                        "工资", date(2026, Calendar.SEPTEMBER, 8)),
                transaction("expense-august", TransactionType.EXPENSE, 999L,
                        "日用", date(2026, Calendar.AUGUST, 31)));

        long[] points = ChartDataCalculator.bucketTotals(transactions,
                TransactionType.EXPENSE, dateCalendar(2026, Calendar.SEPTEMBER, 8),
                ChartDataCalculator.Period.MONTH);

        assertEquals(30, points.length);
        assertEquals(250L, points[1]);
        assertEquals(100L, points[7]);
        assertEquals(350L, ChartDataCalculator.total(points));

        int[] counts = ChartDataCalculator.bucketCounts(transactions,
                TransactionType.EXPENSE, dateCalendar(2026, Calendar.SEPTEMBER, 8),
                ChartDataCalculator.Period.MONTH);
        assertEquals(1, counts[1]);
        assertEquals(1, counts[7]);
    }

    @Test
    public void categorySummaryUsesTheSelectedPeriodAndReportsMatchingCounts() {
        List<Transaction> transactions = Arrays.asList(
                transaction("week-food-1", TransactionType.EXPENSE, 120L,
                        "餐饮", date(2026, Calendar.OCTOBER, 5)),
                transaction("week-food-2", TransactionType.EXPENSE, 80L,
                        "餐饮", date(2026, Calendar.OCTOBER, 11)),
                transaction("next-week-food", TransactionType.EXPENSE, 900L,
                        "餐饮", date(2026, Calendar.OCTOBER, 12)),
                transaction("week-income", TransactionType.INCOME, 500L,
                        "工资", date(2026, Calendar.OCTOBER, 6)));

        Map<String, ChartDataCalculator.CategorySummary> summary =
                ChartDataCalculator.categorySummary(transactions, TransactionType.EXPENSE,
                        dateCalendar(2026, Calendar.OCTOBER, 8),
                        ChartDataCalculator.Period.WEEK);

        assertEquals(200L, summary.get("餐饮").getAmountInCents());
        assertEquals(2, summary.get("餐饮").getCount());
        assertEquals(false, summary.containsKey("工资"));
    }

    @Test
    public void tappingSamePopulatedTrendPointTogglesItOffAndAnotherPointSelectsIt() {
        assertEquals(-1, ChartDataCalculator.togglePointSelection(2, 2, true));
        assertEquals(4, ChartDataCalculator.togglePointSelection(2, 4, true));
        assertEquals(2, ChartDataCalculator.togglePointSelection(2, 4, false));
    }

    @Test
    public void emptyTrendBucketCannotHitAnAdjacentPopulatedPoint() {
        float[] xCoordinates = {10f, 20f, 30f, 40f};
        float[] yCoordinates = {50f, 50f, 50f, 50f};
        int[] counts = {1, 0, 0, 1};

        assertEquals(0, ChartDataCalculator.nearestPopulatedPointIndex(xCoordinates,
                yCoordinates, counts, 10f, 50f, 28f));
        assertEquals(-1, ChartDataCalculator.nearestPopulatedPointIndex(xCoordinates,
                yCoordinates, counts, 20f, 50f, 28f));
        assertEquals(3, ChartDataCalculator.nearestPopulatedPointIndex(xCoordinates,
                yCoordinates, counts, 40f, 50f, 28f));
        assertEquals(0, ChartDataCalculator.nearestPopulatedPointIndex(xCoordinates,
                yCoordinates, counts, 15f, 85f, 40f));
        assertEquals(-1, ChartDataCalculator.nearestPopulatedPointIndex(xCoordinates,
                yCoordinates, counts, 15f, 91f, 40f));
    }

    @Test
    public void selectedWeekAndYearBucketsUseTheirCalendarBoundaries() {
        List<Transaction> transactions = Arrays.asList(
                transaction("monday", TransactionType.EXPENSE, 100L,
                        "餐饮", date(2026, Calendar.OCTOBER, 5)),
                transaction("sunday", TransactionType.EXPENSE, 200L,
                        "餐饮", date(2026, Calendar.OCTOBER, 11)),
                transaction("next-monday", TransactionType.EXPENSE, 300L,
                        "餐饮", date(2026, Calendar.OCTOBER, 12)));

        long[] week = ChartDataCalculator.bucketTotals(transactions, TransactionType.EXPENSE,
                dateCalendar(2026, Calendar.OCTOBER, 8), ChartDataCalculator.Period.WEEK);
        long[] previousWeek = ChartDataCalculator.bucketTotals(transactions,
                TransactionType.EXPENSE, dateCalendar(2026, Calendar.OCTOBER, 8),
                ChartDataCalculator.Period.WEEK, -1);

        assertEquals(7, week.length);
        assertEquals(300L, ChartDataCalculator.total(week));
        assertEquals(0L, ChartDataCalculator.total(previousWeek));
        assertEquals(12, ChartDataCalculator.bucketTotals(transactions,
                TransactionType.EXPENSE, dateCalendar(2026, Calendar.OCTOBER, 8),
                ChartDataCalculator.Period.YEAR).length);
    }

    private static Transaction transaction(String id, TransactionType type, long amount,
            String category, long occurredAt) {
        return new Transaction(id, "ledger", type, amount, category, "", occurredAt);
    }

    private static long date(int year, int month, int day) {
        return dateCalendar(year, month, day).getTimeInMillis();
    }

    private static Calendar dateCalendar(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, 12, 0, 0);
        return calendar;
    }
}

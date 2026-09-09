package com.example.pkmapp.charts;

import static org.junit.Assert.assertEquals;

import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

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

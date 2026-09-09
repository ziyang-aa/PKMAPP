package com.example.pkmapp.charts;

import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;

import java.util.Calendar;
import java.util.List;
import java.util.Objects;

/** Builds chart buckets from the transactions in the active ledger. */
public final class ChartDataCalculator {
    public enum Period {
        WEEK,
        MONTH,
        YEAR
    }

    private ChartDataCalculator() {
    }

    public static long[] bucketTotals(List<Transaction> transactions, TransactionType type,
            Calendar reference, Period period) {
        return bucketTotals(transactions, type, reference, period, 0);
    }

    public static long[] bucketTotals(List<Transaction> transactions, TransactionType type,
            Calendar reference, Period period, int periodOffset) {
        Objects.requireNonNull(transactions, "交易列表不能为空");
        Objects.requireNonNull(type, "收支类型不能为空");
        Objects.requireNonNull(reference, "参考日期不能为空");
        Objects.requireNonNull(period, "统计周期不能为空");

        Calendar start = startOfPeriod(reference, period, periodOffset);
        int bucketCount = bucketCount(start, period);
        long[] totals = new long[bucketCount];

        for (int bucket = 0; bucket < bucketCount; bucket++) {
            Calendar bucketStart = (Calendar) start.clone();
            addBucket(bucketStart, period, bucket);
            Calendar bucketEnd = (Calendar) bucketStart.clone();
            addBucket(bucketEnd, period, 1);
            for (Transaction transaction : transactions) {
                if (transaction.getType() != type) {
                    continue;
                }
                long occurredAt = transaction.getOccurredAtMillis();
                if (occurredAt >= bucketStart.getTimeInMillis()
                        && occurredAt < bucketEnd.getTimeInMillis()) {
                    totals[bucket] += transaction.getAmountInCents();
                }
            }
        }
        return totals;
    }

    public static int[] bucketCounts(List<Transaction> transactions, TransactionType type,
            Calendar reference, Period period) {
        return bucketCounts(transactions, type, reference, period, 0);
    }

    public static int[] bucketCounts(List<Transaction> transactions, TransactionType type,
            Calendar reference, Period period, int periodOffset) {
        Objects.requireNonNull(transactions, "交易列表不能为空");
        Objects.requireNonNull(type, "收支类型不能为空");
        Objects.requireNonNull(reference, "参考日期不能为空");
        Objects.requireNonNull(period, "统计周期不能为空");

        Calendar start = startOfPeriod(reference, period, periodOffset);
        int[] counts = new int[bucketCount(start, period)];
        for (int bucket = 0; bucket < counts.length; bucket++) {
            Calendar bucketStart = (Calendar) start.clone();
            addBucket(bucketStart, period, bucket);
            Calendar bucketEnd = (Calendar) bucketStart.clone();
            addBucket(bucketEnd, period, 1);
            for (Transaction transaction : transactions) {
                if (transaction.getType() != type) {
                    continue;
                }
                long occurredAt = transaction.getOccurredAtMillis();
                if (occurredAt >= bucketStart.getTimeInMillis()
                        && occurredAt < bucketEnd.getTimeInMillis()) {
                    counts[bucket]++;
                }
            }
        }
        return counts;
    }

    public static long totalForPeriod(List<Transaction> transactions, TransactionType type,
            Calendar reference, Period period, int periodOffset) {
        return total(bucketTotals(transactions, type, reference, period, periodOffset));
    }

    public static Calendar startOfPeriod(Calendar reference, Period period, int periodOffset) {
        Objects.requireNonNull(reference, "参考日期不能为空");
        Objects.requireNonNull(period, "统计周期不能为空");
        Calendar start = periodStart(reference, period);
        shiftPeriod(start, period, periodOffset);
        return start;
    }

    public static Calendar endOfPeriod(Calendar reference, Period period, int periodOffset) {
        Calendar end = startOfPeriod(reference, period, periodOffset);
        shiftPeriod(end, period, 1);
        return end;
    }

    public static long total(long[] values) {
        long sum = 0L;
        for (long value : values) {
            sum += value;
        }
        return sum;
    }

    public static long average(long[] values) {
        if (values == null || values.length == 0) {
            return 0L;
        }
        return Math.round(total(values) / (double) values.length);
    }

    public static long maximum(long[] values) {
        long maximum = 0L;
        if (values == null) {
            return maximum;
        }
        for (long value : values) {
            maximum = Math.max(maximum, value);
        }
        return maximum;
    }

    private static Calendar periodStart(Calendar reference, Period period) {
        Calendar start = (Calendar) reference.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        switch (period) {
            case WEEK:
                int daysFromMonday = start.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
                if (daysFromMonday < 0) {
                    daysFromMonday += 7;
                }
                start.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
                break;
            case MONTH:
                start.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case YEAR:
                start.set(Calendar.MONTH, Calendar.JANUARY);
                start.set(Calendar.DAY_OF_MONTH, 1);
                break;
            default:
                throw new IllegalArgumentException("未知统计周期");
        }
        return start;
    }

    private static int bucketCount(Calendar start, Period period) {
        switch (period) {
            case WEEK:
                return 7;
            case MONTH:
                return start.getActualMaximum(Calendar.DAY_OF_MONTH);
            case YEAR:
                return 12;
            default:
                throw new IllegalArgumentException("未知统计周期");
        }
    }

    private static void shiftPeriod(Calendar date, Period period, int amount) {
        switch (period) {
            case WEEK:
                date.add(Calendar.DAY_OF_MONTH, amount * 7);
                break;
            case MONTH:
                date.add(Calendar.MONTH, amount);
                break;
            case YEAR:
                date.add(Calendar.YEAR, amount);
                break;
            default:
                throw new IllegalArgumentException("未知统计周期");
        }
    }

    private static void addBucket(Calendar date, Period period, int amount) {
        if (period == Period.WEEK || period == Period.MONTH) {
            date.add(Calendar.DAY_OF_MONTH, amount);
        } else {
            date.add(Calendar.MONTH, amount);
        }
    }
}

package com.example.pkmapp.borrowing;

import java.util.List;
import java.util.Objects;

public final class BorrowingSummary {
    private final long totalInCents;
    private final int recordCount;

    private BorrowingSummary(long totalInCents, int recordCount) {
        this.totalInCents = totalInCents;
        this.recordCount = recordCount;
    }

    public static BorrowingSummary from(List<BorrowingRecord> records,
            BorrowingDirection direction) {
        Objects.requireNonNull(records, "借钱记录不能为空");
        Objects.requireNonNull(direction, "借钱方向不能为空");
        long total = 0L;
        int count = 0;
        for (BorrowingRecord record : records) {
            if (record != null && record.getDirection() == direction) {
                total += record.getAmountInCents();
                count++;
            }
        }
        return new BorrowingSummary(total, count);
    }

    public long getTotalInCents() {
        return totalInCents;
    }

    public int getRecordCount() {
        return recordCount;
    }
}

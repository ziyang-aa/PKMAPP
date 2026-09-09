package com.example.pkmapp.borrowing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public final class BorrowingSummaryTest {
    @Test(expected = IllegalArgumentException.class)
    public void requiresPersonAndDateForEveryRecord() {
        new BorrowingRecord("id", "ledger", BorrowingDirection.LEND, 1_000L,
                0L, "小林", "", null);
    }

    @Test
    public void summarizesLendAndBorrowSeparately() {
        BorrowingRecord lend = record("lend", BorrowingDirection.LEND, 10_000L, "小林");
        BorrowingRecord secondLend = record("lend-2", BorrowingDirection.LEND, 2_500L, "小夏");
        BorrowingRecord borrow = record("borrow", BorrowingDirection.BORROW, 4_000L, "阿木");

        BorrowingSummary lendSummary = BorrowingSummary.from(
                Arrays.asList(lend, secondLend, borrow), BorrowingDirection.LEND);
        BorrowingSummary borrowSummary = BorrowingSummary.from(
                Arrays.asList(lend, secondLend, borrow), BorrowingDirection.BORROW);

        assertEquals(12_500L, lendSummary.getTotalInCents());
        assertEquals(2, lendSummary.getRecordCount());
        assertEquals(4_000L, borrowSummary.getTotalInCents());
        assertEquals(1, borrowSummary.getRecordCount());
    }

    private static BorrowingRecord record(String id, BorrowingDirection direction,
            long amountInCents, String person) {
        return new BorrowingRecord(id, "ledger", direction, amountInCents, 1L, person, "", null);
    }
}

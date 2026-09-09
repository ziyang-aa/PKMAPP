package com.example.pkmapp.borrowing;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BorrowingRecordPresentationTest {
    @Test
    public void formatsDirectionCounterpartyAndSignedAmountForForestList() {
        assertEquals("借出", BorrowingRecordPresentation.directionLabel(BorrowingDirection.LEND));
        assertEquals("给 小林", BorrowingRecordPresentation.counterpartyLabel(
                BorrowingDirection.LEND, "小林"));
        assertEquals("−¥36.80", BorrowingRecordPresentation.amountLabel(
                BorrowingDirection.LEND, 3680));

        assertEquals("借入", BorrowingRecordPresentation.directionLabel(BorrowingDirection.BORROW));
        assertEquals("向 小林", BorrowingRecordPresentation.counterpartyLabel(
                BorrowingDirection.BORROW, "小林"));
        assertEquals("+¥36.80", BorrowingRecordPresentation.amountLabel(
                BorrowingDirection.BORROW, 3680));
        assertEquals("小", BorrowingRecordPresentation.initials("小林"));
    }
}

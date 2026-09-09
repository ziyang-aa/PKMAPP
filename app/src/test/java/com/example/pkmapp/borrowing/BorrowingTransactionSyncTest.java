package com.example.pkmapp.borrowing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.pkmapp.data.TransactionType;

import org.junit.Test;

public final class BorrowingTransactionSyncTest {
    @Test
    public void standaloneBorrowingRecordCreatesMainTransaction() {
        assertTrue(BorrowingTransactionSync.shouldCreateMainTransaction(null));
        assertTrue(BorrowingTransactionSync.shouldCreateMainTransaction(""));
        assertEquals(TransactionType.EXPENSE,
                BorrowingTransactionSync.transactionType(BorrowingDirection.LEND));
        assertEquals("借出", BorrowingTransactionSync.category(BorrowingDirection.LEND));
        assertEquals("给 小林 · 午餐垫付",
                BorrowingTransactionSync.note(BorrowingDirection.LEND, "小林", "午餐垫付"));
    }

    @Test
    public void recordOpenedFromLedgerDoesNotCreateDuplicateTransaction() {
        assertFalse(BorrowingTransactionSync.shouldCreateMainTransaction("transaction-1"));
        assertEquals(TransactionType.INCOME,
                BorrowingTransactionSync.transactionType(BorrowingDirection.BORROW));
        assertEquals("借入", BorrowingTransactionSync.category(BorrowingDirection.BORROW));
        assertEquals("向 阿木", BorrowingTransactionSync.note(
                BorrowingDirection.BORROW, "阿木", ""));
    }
}

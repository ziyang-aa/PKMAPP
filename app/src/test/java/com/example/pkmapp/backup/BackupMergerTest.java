package com.example.pkmapp.backup;

import static org.junit.Assert.assertEquals;

import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.borrowing.BorrowingDirection;
import com.example.pkmapp.borrowing.BorrowingRecord;
import com.example.pkmapp.savings.SavingsGoal;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class BackupMergerTest {
    @Test
    public void merge_skipsTransactionWithSameTimeAmountAndNote() {
        BackupSnapshot existing = snapshot(
                Collections.singletonList(new Transaction("old", "ledger-1",
                        TransactionType.EXPENSE, 1000L, "早餐", "今天的早餐", 5000L)),
                Collections.emptyList());
        BackupSnapshot incoming = snapshot(
                Arrays.asList(
                        new Transaction("duplicate", "ledger-1", TransactionType.EXPENSE,
                                1000L, "其他分类", "今天的早餐", 5000L),
                        new Transaction("new", "ledger-1", TransactionType.EXPENSE,
                                2000L, "交通", "公交", 6000L)),
                Collections.emptyList());

        BackupMerger.Result result = BackupMerger.merge(existing, incoming);

        assertEquals(1, result.getAddedTransactions());
        assertEquals(1, result.getSkippedTransactions());
        assertEquals(2, result.getSnapshot().getTransactions().size());
    }

    @Test
    public void merge_doesNotDuplicateSavingsGoal() {
        SavingsGoal existingGoal = new SavingsGoal("goal-1", "旅行", 100000L, 2000L, 1L);
        BackupSnapshot existing = snapshot(Collections.emptyList(),
                Collections.singletonList(existingGoal));
        BackupSnapshot incoming = snapshot(Collections.emptyList(),
                Arrays.asList(existingGoal,
                        new SavingsGoal("goal-2", "电脑", 200000L, 0L, 2L)));

        BackupMerger.Result result = BackupMerger.merge(existing, incoming);

        assertEquals(1, result.getAddedSavingsGoals());
        assertEquals(1, result.getSkippedSavingsGoals());
        assertEquals(2, result.getSnapshot().getSavingsGoals().size());
        assertEquals(2000L, result.getSnapshot().getSavingsGoals().get(0).getSavedCents());
    }

    @Test
    public void merge_remapsBorrowingSourceWhenTransactionWasSkippedAsDuplicate() {
        Transaction existingTransaction = new Transaction("old-tx", "ledger-1",
                TransactionType.EXPENSE, 1000L, "餐饮", "早餐", 5000L);
        Transaction incomingTransaction = new Transaction("new-tx", "ledger-1",
                TransactionType.EXPENSE, 1000L, "其他分类", "早餐", 5000L);
        BorrowingRecord incomingRecord = new BorrowingRecord("borrow-1", "ledger-1",
                BorrowingDirection.BORROW, 1000L, 5000L, "小明", "早餐", "new-tx");

        BackupSnapshot existing = snapshot(Collections.singletonList(existingTransaction),
                Collections.emptyList());
        BackupSnapshot incoming = new BackupSnapshot(2L,
                Collections.singletonList(new Ledger("ledger-1", "生活账本")),
                Collections.singletonList(incomingTransaction), "ledger-1",
                Collections.emptyList(), Collections.singletonList(
                        new BackupSnapshot.AssetValues("ledger-1", 0L, 0L)),
                Collections.singletonList(incomingRecord));

        BackupMerger.Result result = BackupMerger.merge(existing, incoming);

        assertEquals(0, result.getAddedTransactions());
        assertEquals(1, result.getSkippedTransactions());
        assertEquals("old-tx", result.getSnapshot().getBorrowingRecords().get(0)
                .getSourceTransactionId());
    }

    private BackupSnapshot snapshot(java.util.List<Transaction> transactions,
            java.util.List<SavingsGoal> goals) {
        return new BackupSnapshot(1L,
                Collections.singletonList(new Ledger("ledger-1", "生活账本")), transactions,
                "ledger-1", goals, Collections.singletonList(
                        new BackupSnapshot.AssetValues("ledger-1", 0L, 0L)),
                Collections.emptyList());
    }
}

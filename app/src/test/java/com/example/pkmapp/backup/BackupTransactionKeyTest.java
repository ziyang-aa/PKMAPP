package com.example.pkmapp.backup;

import static org.junit.Assert.assertEquals;

import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;

import org.junit.Test;

public final class BackupTransactionKeyTest {
    @Test
    public void key_usesOnlyTimeAmountAndNote() {
        Transaction first = new Transaction("1", "ledger-1", TransactionType.EXPENSE,
                1000L, "餐饮", "早餐", 5000L);
        Transaction sameKey = new Transaction("2", "ledger-2", TransactionType.INCOME,
                1000L, "工资", "早餐", 5000L);

        assertEquals(BackupTransactionKey.from(first), BackupTransactionKey.from(sameKey));
    }
}

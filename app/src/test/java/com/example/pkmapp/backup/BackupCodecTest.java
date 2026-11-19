package com.example.pkmapp.backup;

import static org.junit.Assert.assertEquals;

import com.example.pkmapp.borrowing.BorrowingDirection;
import com.example.pkmapp.borrowing.BorrowingRecord;
import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.savings.SavingsGoal;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class BackupCodecTest {
    @Test
    public void encodeAndDecode_preservesAllFinancialData() {
        BackupSnapshot original = new BackupSnapshot(
                3000L,
                Collections.singletonList(new Ledger("ledger-1", "生活账本")),
                Collections.singletonList(new Transaction("tx-1", "ledger-1",
                        TransactionType.EXPENSE, 1250L, "餐饮", "午饭", 1000L)),
                "ledger-1",
                Collections.singletonList(new SavingsGoal("goal-1", "旅行", 100000L,
                        2500L, 2000L)),
                Collections.singletonList(new BackupSnapshot.AssetValues("ledger-1", 50000L,
                        1000L)),
                Collections.singletonList(new BorrowingRecord("borrow-1", "ledger-1",
                        BorrowingDirection.BORROW, 3000L, 1500L, "小明", "午餐", "tx-1")));

        BackupSnapshot restored = BackupCodec.decode(BackupCodec.encode(original));

        assertEquals(original.getExportedAtMillis(), restored.getExportedAtMillis());
        assertEquals(original.getLedgers().size(), restored.getLedgers().size());
        assertEquals(original.getTransactions().get(0).getNote(),
                restored.getTransactions().get(0).getNote());
        assertEquals(original.getSavingsGoals().get(0).getSavedCents(),
                restored.getSavingsGoals().get(0).getSavedCents());
        assertEquals(original.getAssetValues().get(0).getLiabilitiesInCents(),
                restored.getAssetValues().get(0).getLiabilitiesInCents());
        assertEquals(original.getBorrowingRecords().get(0).getPerson(),
                restored.getBorrowingRecords().get(0).getPerson());
    }

    @Test(expected = IllegalArgumentException.class)
    public void decode_rejectsUnknownVersion() {
        BackupCodec.decode("{\"format\":\"pkmapp-backup\",\"version\":99}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void decode_rejectsTransactionWithUnknownLedger() {
        BackupCodec.decode("{"
                + "\"format\":\"pkmapp-backup\",\"version\":1,"
                + "\"exportedAtMillis\":1,\"currentLedgerId\":\"ledger-1\","
                + "\"ledgers\":[{\"id\":\"ledger-1\",\"name\":\"生活账本\"}],"
                + "\"transactions\":[{\"id\":\"tx-1\",\"ledgerId\":\"missing\","
                + "\"type\":\"EXPENSE\",\"amountInCents\":100,"
                + "\"category\":\"餐饮\",\"note\":\"早餐\",\"occurredAtMillis\":1}],"
                + "\"savingsGoals\":[],\"assets\":[],\"borrowingRecords\":[]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void decode_rejectsMissingAssetForLedger() {
        BackupCodec.decode("{"
                + "\"format\":\"pkmapp-backup\",\"version\":1,"
                + "\"exportedAtMillis\":1,\"currentLedgerId\":\"ledger-1\","
                + "\"ledgers\":[{\"id\":\"ledger-1\",\"name\":\"生活账本\"}],"
                + "\"transactions\":[],\"savingsGoals\":[],\"assets\":[],"
                + "\"borrowingRecords\":[]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void decode_rejectsFractionalAmount() {
        BackupCodec.decode("{"
                + "\"format\":\"pkmapp-backup\",\"version\":1,"
                + "\"exportedAtMillis\":1,\"currentLedgerId\":\"ledger-1\","
                + "\"ledgers\":[{\"id\":\"ledger-1\",\"name\":\"生活账本\"}],"
                + "\"transactions\":[{\"id\":\"tx-1\",\"ledgerId\":\"ledger-1\","
                + "\"type\":\"EXPENSE\",\"amountInCents\":100.5,"
                + "\"category\":\"餐饮\",\"note\":\"早餐\",\"occurredAtMillis\":1}],"
                + "\"savingsGoals\":[],\"assets\":[{\"ledgerId\":\"ledger-1\","
                + "\"initialAssetsInCents\":0,\"liabilitiesInCents\":0}],"
                + "\"borrowingRecords\":[]}");
    }
}

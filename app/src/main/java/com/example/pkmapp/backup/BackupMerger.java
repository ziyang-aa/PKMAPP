package com.example.pkmapp.backup;

import com.example.pkmapp.borrowing.BorrowingRecord;
import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.savings.SavingsGoal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure merge rules used before changing any local store. */
public final class BackupMerger {
    private BackupMerger() {
    }

    public static Result merge(BackupSnapshot existing, BackupSnapshot incoming) {
        if (existing == null || incoming == null) {
            throw new IllegalArgumentException("合并数据不能为空");
        }

        List<Ledger> ledgers = new ArrayList<>(existing.getLedgers());
        Map<String, String> ledgerIdMap = new HashMap<>();
        Map<String, String> existingNames = new HashMap<>();
        for (Ledger ledger : ledgers) {
            ledgerIdMap.put(ledger.getId(), ledger.getId());
            existingNames.putIfAbsent(ledger.getName(), ledger.getId());
        }
        int addedLedgers = 0;
        for (Ledger ledger : incoming.getLedgers()) {
            String targetId = ledgerIdMap.get(ledger.getId());
            if (targetId == null) {
                targetId = existingNames.get(ledger.getName());
            }
            if (targetId == null) {
                targetId = ledger.getId();
                ledgers.add(ledger);
                existingNames.put(ledger.getName(), targetId);
                addedLedgers++;
            }
            ledgerIdMap.put(ledger.getId(), targetId);
        }

        List<Transaction> transactions = new ArrayList<>(existing.getTransactions());
        Set<BackupTransactionKey> transactionKeys = new HashSet<>();
        Set<String> transactionIds = new HashSet<>();
        Map<BackupTransactionKey, String> transactionKeyIds = new HashMap<>();
        Map<String, String> transactionIdMap = new HashMap<>();
        for (Transaction transaction : transactions) {
            transactionKeys.add(BackupTransactionKey.from(transaction));
            transactionIds.add(transaction.getId());
            transactionKeyIds.putIfAbsent(BackupTransactionKey.from(transaction), transaction.getId());
            transactionIdMap.put(transaction.getId(), transaction.getId());
        }
        int addedTransactions = 0;
        int skippedTransactions = 0;
        for (Transaction transaction : incoming.getTransactions()) {
            BackupTransactionKey key = BackupTransactionKey.from(transaction);
            if (transactionKeys.contains(key)) {
                transactionIdMap.put(transaction.getId(), transactionKeyIds.get(key));
                skippedTransactions++;
                continue;
            }
            String targetId = transaction.getId();
            if (transactionIds.contains(targetId)) {
                targetId = UUID.randomUUID().toString();
            }
            transactions.add(remap(transaction, targetId,
                    ledgerIdMap.get(transaction.getLedgerId())));
            transactionKeys.add(key);
            transactionKeyIds.put(key, targetId);
            transactionIds.add(targetId);
            transactionIdMap.put(transaction.getId(), targetId);
            addedTransactions++;
        }

        List<SavingsGoal> goals = new ArrayList<>(existing.getSavingsGoals());
        Set<String> goalIds = new HashSet<>();
        for (SavingsGoal goal : goals) {
            goalIds.add(goal.getId());
        }
        int addedSavingsGoals = 0;
        int skippedSavingsGoals = 0;
        for (SavingsGoal goal : incoming.getSavingsGoals()) {
            if (!goalIds.add(goal.getId())) {
                skippedSavingsGoals++;
            } else {
                goals.add(goal);
                addedSavingsGoals++;
            }
        }

        List<BackupSnapshot.AssetValues> assets = new ArrayList<>(existing.getAssetValues());
        Set<String> assetLedgerIds = new HashSet<>();
        for (BackupSnapshot.AssetValues values : assets) {
            assetLedgerIds.add(values.getLedgerId());
        }
        int addedAssets = 0;
        for (BackupSnapshot.AssetValues values : incoming.getAssetValues()) {
            String targetId = ledgerIdMap.get(values.getLedgerId());
            if (assetLedgerIds.add(targetId)) {
                assets.add(new BackupSnapshot.AssetValues(targetId,
                        values.getInitialAssetsInCents(), values.getLiabilitiesInCents()));
                addedAssets++;
            }
        }

        List<BorrowingRecord> borrowingRecords = new ArrayList<>(existing.getBorrowingRecords());
        Set<String> borrowingIds = new HashSet<>();
        for (BorrowingRecord record : borrowingRecords) {
            borrowingIds.add(record.getId());
        }
        int addedBorrowingRecords = 0;
        int skippedBorrowingRecords = 0;
        for (BorrowingRecord record : incoming.getBorrowingRecords()) {
            if (!borrowingIds.add(record.getId())) {
                skippedBorrowingRecords++;
            } else {
                String sourceTransactionId = record.getSourceTransactionId();
                if (sourceTransactionId != null) {
                    sourceTransactionId = transactionIdMap.getOrDefault(sourceTransactionId,
                            sourceTransactionId);
                }
                borrowingRecords.add(remap(record, ledgerIdMap.get(record.getLedgerId()),
                        sourceTransactionId));
                addedBorrowingRecords++;
            }
        }

        String currentLedgerId = ledgerIdMap.get(incoming.getCurrentLedgerId());
        if (currentLedgerId == null) {
            currentLedgerId = existing.getCurrentLedgerId();
        }
        BackupSnapshot merged = new BackupSnapshot(System.currentTimeMillis(), ledgers, transactions,
                currentLedgerId, goals, assets, borrowingRecords);
        return new Result(merged, addedLedgers, addedTransactions, skippedTransactions,
                addedSavingsGoals, skippedSavingsGoals, addedAssets, addedBorrowingRecords,
                skippedBorrowingRecords);
    }

    private static Transaction remap(Transaction transaction, String id, String ledgerId) {
        if (ledgerId == null) {
            throw new IllegalArgumentException("交易所属账本不存在");
        }
        return new Transaction(id, ledgerId, transaction.getType(),
                transaction.getAmountInCents(), transaction.getCategory(), transaction.getNote(),
                transaction.getOccurredAtMillis());
    }

    private static BorrowingRecord remap(BorrowingRecord record, String ledgerId,
            String sourceTransactionId) {
        if (ledgerId == null) {
            throw new IllegalArgumentException("借钱记录所属账本不存在");
        }
        return new BorrowingRecord(record.getId(), ledgerId, record.getDirection(),
                record.getAmountInCents(), record.getOccurredAtMillis(), record.getPerson(),
                record.getNote(), sourceTransactionId);
    }

    public static final class Result {
        private final BackupSnapshot snapshot;
        private final int addedLedgers;
        private final int addedTransactions;
        private final int skippedTransactions;
        private final int addedSavingsGoals;
        private final int skippedSavingsGoals;
        private final int addedAssets;
        private final int addedBorrowingRecords;
        private final int skippedBorrowingRecords;

        private Result(BackupSnapshot snapshot, int addedLedgers, int addedTransactions,
                int skippedTransactions, int addedSavingsGoals, int skippedSavingsGoals,
                int addedAssets, int addedBorrowingRecords, int skippedBorrowingRecords) {
            this.snapshot = snapshot;
            this.addedLedgers = addedLedgers;
            this.addedTransactions = addedTransactions;
            this.skippedTransactions = skippedTransactions;
            this.addedSavingsGoals = addedSavingsGoals;
            this.skippedSavingsGoals = skippedSavingsGoals;
            this.addedAssets = addedAssets;
            this.addedBorrowingRecords = addedBorrowingRecords;
            this.skippedBorrowingRecords = skippedBorrowingRecords;
        }

        public BackupSnapshot getSnapshot() {
            return snapshot;
        }

        public int getAddedLedgers() {
            return addedLedgers;
        }

        public int getAddedTransactions() {
            return addedTransactions;
        }

        public int getSkippedTransactions() {
            return skippedTransactions;
        }

        public int getAddedSavingsGoals() {
            return addedSavingsGoals;
        }

        public int getSkippedSavingsGoals() {
            return skippedSavingsGoals;
        }

        public int getAddedAssets() {
            return addedAssets;
        }

        public int getAddedBorrowingRecords() {
            return addedBorrowingRecords;
        }

        public int getSkippedBorrowingRecords() {
            return skippedBorrowingRecords;
        }
    }
}

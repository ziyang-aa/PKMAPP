package com.example.pkmapp.backup;

import com.example.pkmapp.borrowing.BorrowingRecord;
import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.savings.SavingsGoal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable in-memory representation of a complete financial backup. */
public final class BackupSnapshot {
    private final long exportedAtMillis;
    private final List<Ledger> ledgers;
    private final List<Transaction> transactions;
    private final String currentLedgerId;
    private final List<SavingsGoal> savingsGoals;
    private final List<AssetValues> assetValues;
    private final List<BorrowingRecord> borrowingRecords;

    public BackupSnapshot(long exportedAtMillis, List<Ledger> ledgers,
            List<Transaction> transactions, String currentLedgerId,
            List<SavingsGoal> savingsGoals, List<AssetValues> assetValues,
            List<BorrowingRecord> borrowingRecords) {
        if (exportedAtMillis <= 0L) {
            throw new IllegalArgumentException("导出时间不能为空");
        }
        this.ledgers = immutableCopy(ledgers, "账本列表不能为空");
        this.transactions = immutableCopy(transactions, "交易列表不能为空");
        this.currentLedgerId = requireText(currentLedgerId, "当前账本不能为空");
        this.savingsGoals = immutableCopy(savingsGoals, "攒钱目标列表不能为空");
        this.assetValues = immutableCopy(assetValues, "资产列表不能为空");
        this.borrowingRecords = immutableCopy(borrowingRecords, "借钱记录列表不能为空");
        this.exportedAtMillis = exportedAtMillis;
        validateRelationships();
    }

    public long getExportedAtMillis() {
        return exportedAtMillis;
    }

    public List<Ledger> getLedgers() {
        return ledgers;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public String getCurrentLedgerId() {
        return currentLedgerId;
    }

    public List<SavingsGoal> getSavingsGoals() {
        return savingsGoals;
    }

    public List<AssetValues> getAssetValues() {
        return assetValues;
    }

    public List<BorrowingRecord> getBorrowingRecords() {
        return borrowingRecords;
    }

    private void validateRelationships() {
        Set<String> ledgerIds = new HashSet<>();
        for (Ledger ledger : ledgers) {
            if (ledger == null || !ledgerIds.add(ledger.getId())) {
                throw new IllegalArgumentException("备份中存在重复或无效账本");
            }
        }
        if (!ledgerIds.contains(currentLedgerId)) {
            throw new IllegalArgumentException("当前账本不存在");
        }

        Set<String> transactionIds = new HashSet<>();
        for (Transaction transaction : transactions) {
            if (transaction == null || !ledgerIds.contains(transaction.getLedgerId())
                    || !transactionIds.add(transaction.getId())) {
                throw new IllegalArgumentException("备份中存在重复或无效交易");
            }
        }

        Set<String> goalIds = new HashSet<>();
        for (SavingsGoal goal : savingsGoals) {
            if (goal == null || !goalIds.add(goal.getId())) {
                throw new IllegalArgumentException("备份中存在重复或无效攒钱目标");
            }
        }

        Set<String> assetLedgerIds = new HashSet<>();
        for (AssetValues values : assetValues) {
            if (values == null || !ledgerIds.contains(values.getLedgerId())
                    || !assetLedgerIds.add(values.getLedgerId())) {
                throw new IllegalArgumentException("备份中存在重复或无效资产");
            }
        }
        if (assetLedgerIds.size() != ledgerIds.size()) {
            throw new IllegalArgumentException("备份中缺少账本资产数据");
        }

        Set<String> borrowingIds = new HashSet<>();
        for (BorrowingRecord record : borrowingRecords) {
            if (record == null || !ledgerIds.contains(record.getLedgerId())
                    || !borrowingIds.add(record.getId())) {
                throw new IllegalArgumentException("备份中存在重复或无效借钱记录");
            }
        }
    }

    private static <T> List<T> immutableCopy(List<T> values, String message) {
        if (values == null) {
            throw new IllegalArgumentException(message);
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static String requireText(String value, String message) {
        String trimmed = Objects.requireNonNull(value, message).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    public static final class AssetValues {
        private final String ledgerId;
        private final long initialAssetsInCents;
        private final long liabilitiesInCents;

        public AssetValues(String ledgerId, long initialAssetsInCents, long liabilitiesInCents) {
            this.ledgerId = requireText(ledgerId, "资产所属账本不能为空");
            if (initialAssetsInCents < 0L || liabilitiesInCents < 0L) {
                throw new IllegalArgumentException("资产和负债不能为负数");
            }
            this.initialAssetsInCents = initialAssetsInCents;
            this.liabilitiesInCents = liabilitiesInCents;
        }

        public String getLedgerId() {
            return ledgerId;
        }

        public long getInitialAssetsInCents() {
            return initialAssetsInCents;
        }

        public long getLiabilitiesInCents() {
            return liabilitiesInCents;
        }
    }
}

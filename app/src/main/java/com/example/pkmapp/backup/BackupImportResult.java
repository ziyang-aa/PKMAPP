package com.example.pkmapp.backup;

/** Counts the changes made by one backup import. */
public final class BackupImportResult {
    private final int importedLedgers;
    private final int importedTransactions;
    private final int skippedTransactions;
    private final int importedSavingsGoals;
    private final int skippedSavingsGoals;
    private final int importedBorrowingRecords;
    private final int skippedBorrowingRecords;

    public BackupImportResult(int importedLedgers, int importedTransactions,
            int skippedTransactions, int importedSavingsGoals, int skippedSavingsGoals,
            int importedBorrowingRecords, int skippedBorrowingRecords) {
        this.importedLedgers = importedLedgers;
        this.importedTransactions = importedTransactions;
        this.skippedTransactions = skippedTransactions;
        this.importedSavingsGoals = importedSavingsGoals;
        this.skippedSavingsGoals = skippedSavingsGoals;
        this.importedBorrowingRecords = importedBorrowingRecords;
        this.skippedBorrowingRecords = skippedBorrowingRecords;
    }

    public int getImportedLedgers() {
        return importedLedgers;
    }

    public int getImportedTransactions() {
        return importedTransactions;
    }

    public int getSkippedTransactions() {
        return skippedTransactions;
    }

    public int getImportedSavingsGoals() {
        return importedSavingsGoals;
    }

    public int getSkippedSavingsGoals() {
        return skippedSavingsGoals;
    }

    public int getImportedBorrowingRecords() {
        return importedBorrowingRecords;
    }

    public int getSkippedBorrowingRecords() {
        return skippedBorrowingRecords;
    }
}

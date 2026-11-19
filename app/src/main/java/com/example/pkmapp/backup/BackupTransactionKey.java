package com.example.pkmapp.backup;

import com.example.pkmapp.data.Transaction;

import java.util.Objects;

/** Identifies a transaction for merge imports using the user's duplicate rules. */
public final class BackupTransactionKey {
    private final long occurredAtMillis;
    private final long amountInCents;
    private final String note;

    private BackupTransactionKey(long occurredAtMillis, long amountInCents, String note) {
        this.occurredAtMillis = occurredAtMillis;
        this.amountInCents = amountInCents;
        this.note = note == null ? "" : note;
    }

    public static BackupTransactionKey from(Transaction transaction) {
        Objects.requireNonNull(transaction, "交易记录不能为空");
        return new BackupTransactionKey(transaction.getOccurredAtMillis(),
                transaction.getAmountInCents(), transaction.getNote());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BackupTransactionKey)) return false;
        BackupTransactionKey that = (BackupTransactionKey) other;
        return occurredAtMillis == that.occurredAtMillis
                && amountInCents == that.amountInCents
                && note.equals(that.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(occurredAtMillis, amountInCents, note);
    }
}

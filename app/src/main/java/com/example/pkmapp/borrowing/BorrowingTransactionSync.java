package com.example.pkmapp.borrowing;

import com.example.pkmapp.data.TransactionType;

/** Maps a standalone borrowing record to the main ledger transaction. */
public final class BorrowingTransactionSync {
    private BorrowingTransactionSync() {
    }

    public static boolean shouldCreateMainTransaction(String sourceTransactionId) {
        return sourceTransactionId == null || sourceTransactionId.trim().isEmpty();
    }

    public static TransactionType transactionType(BorrowingDirection direction) {
        return direction == BorrowingDirection.BORROW
                ? TransactionType.INCOME : TransactionType.EXPENSE;
    }

    public static String category(BorrowingDirection direction) {
        return direction == BorrowingDirection.BORROW ? "借入" : "借出";
    }

    public static String note(BorrowingDirection direction, String person, String note) {
        String prefix = direction == BorrowingDirection.BORROW ? "向 " : "给 ";
        String result = prefix + person.trim();
        String detail = note == null ? "" : note.trim();
        return detail.isEmpty() ? result : result + " · " + detail;
    }
}

package com.example.pkmapp.record;

import androidx.annotation.Nullable;

import com.example.pkmapp.borrowing.BorrowingDirection;
import com.example.pkmapp.data.TransactionType;

public final class BorrowingCategoryLink {
    private BorrowingCategoryLink() {
    }

    @Nullable
    public static BorrowingDirection directionFor(TransactionType type, String category) {
        if (type == TransactionType.EXPENSE && "借出".equals(category)) {
            return BorrowingDirection.LEND;
        }
        if (type == TransactionType.INCOME && "借入".equals(category)) {
            return BorrowingDirection.BORROW;
        }
        return null;
    }
}

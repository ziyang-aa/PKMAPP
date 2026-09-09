package com.example.pkmapp.borrowing;

import com.example.pkmapp.record.MoneyParser;

public final class BorrowingRecordPresentation {
    private BorrowingRecordPresentation() {
    }

    public static String directionLabel(BorrowingDirection direction) {
        return direction == BorrowingDirection.BORROW ? "借入" : "借出";
    }

    public static String counterpartyLabel(BorrowingDirection direction, String person) {
        return (direction == BorrowingDirection.BORROW ? "向 " : "给 ") + person;
    }

    public static String amountLabel(BorrowingDirection direction, long amountInCents) {
        return (direction == BorrowingDirection.BORROW ? "+" : "−")
                + MoneyParser.formatCents(amountInCents);
    }

    public static String initials(String person) {
        String value = person == null ? "" : person.trim();
        return value.isEmpty() ? "友" : value.substring(0, 1);
    }
}

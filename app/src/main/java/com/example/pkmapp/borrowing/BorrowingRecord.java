package com.example.pkmapp.borrowing;

import java.util.Objects;

public final class BorrowingRecord {
    private final String id;
    private final String ledgerId;
    private final BorrowingDirection direction;
    private final long amountInCents;
    private final long occurredAtMillis;
    private final String person;
    private final String note;
    private final String sourceTransactionId;

    public BorrowingRecord(String id, String ledgerId, BorrowingDirection direction,
            long amountInCents, long occurredAtMillis, String person, String note,
            String sourceTransactionId) {
        this.id = required(id, "借钱记录编号不能为空");
        this.ledgerId = required(ledgerId, "账本编号不能为空");
        this.direction = Objects.requireNonNull(direction, "借钱方向不能为空");
        if (amountInCents <= 0L) {
            throw new IllegalArgumentException("金额必须大于零");
        }
        if (occurredAtMillis <= 0L) {
            throw new IllegalArgumentException("发生日期不能为空");
        }
        this.amountInCents = amountInCents;
        this.occurredAtMillis = occurredAtMillis;
        this.person = required(person, "人物不能为空");
        this.note = note == null ? "" : note.trim();
        String source = sourceTransactionId == null ? "" : sourceTransactionId.trim();
        this.sourceTransactionId = source.isEmpty() ? null : source;
    }

    public String getId() {
        return id;
    }

    public String getLedgerId() {
        return ledgerId;
    }

    public BorrowingDirection getDirection() {
        return direction;
    }

    public long getAmountInCents() {
        return amountInCents;
    }

    public long getOccurredAtMillis() {
        return occurredAtMillis;
    }

    public String getPerson() {
        return person;
    }

    public String getNote() {
        return note;
    }

    public String getSourceTransactionId() {
        return sourceTransactionId;
    }

    private static String required(String value, String message) {
        String trimmed = Objects.requireNonNull(value, message).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}

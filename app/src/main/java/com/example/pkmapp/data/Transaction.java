package com.example.pkmapp.data;

import java.util.Objects;

public final class Transaction {
    private final String id;
    private final String ledgerId;
    private final TransactionType type;
    private final long amountInCents;
    private final String category;
    private final String note;
    private final long occurredAtMillis;

    public Transaction(String id, String ledgerId, TransactionType type, long amountInCents,
            String category, String note, long occurredAtMillis) {
        this.id = requireText(id, "记录编号不能为空");
        this.ledgerId = requireText(ledgerId, "账本编号不能为空");
        this.type = Objects.requireNonNull(type, "收支类型不能为空");
        if (amountInCents <= 0L) {
            throw new IllegalArgumentException("金额必须大于零");
        }
        this.amountInCents = amountInCents;
        this.category = requireText(category, "分类不能为空");
        this.note = note == null ? "" : note.trim();
        if (occurredAtMillis <= 0L) {
            throw new IllegalArgumentException("日期不能为空");
        }
        this.occurredAtMillis = occurredAtMillis;
    }

    public String getId() {
        return id;
    }

    public String getLedgerId() {
        return ledgerId;
    }

    public TransactionType getType() {
        return type;
    }

    public long getAmountInCents() {
        return amountInCents;
    }

    public String getCategory() {
        return category;
    }

    public String getNote() {
        return note;
    }

    public long getOccurredAtMillis() {
        return occurredAtMillis;
    }

    private static String requireText(String value, String message) {
        String trimmed = Objects.requireNonNull(value, message).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}

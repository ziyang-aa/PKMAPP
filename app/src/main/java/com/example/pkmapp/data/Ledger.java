package com.example.pkmapp.data;

import java.util.Objects;

public final class Ledger {
    private final String id;
    private final String name;
    private final long monthlyBudgetCents;

    public Ledger(String id, String name) {
        this(id, name, 0L);
    }

    public Ledger(String id, String name, long monthlyBudgetCents) {
        this.id = requireText(id, "账本编号不能为空");
        this.name = requireText(name, "账本名称不能为空");
        if (monthlyBudgetCents < 0L) {
            throw new IllegalArgumentException("月度预算不能为负数");
        }
        this.monthlyBudgetCents = monthlyBudgetCents;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getMonthlyBudgetCents() {
        return monthlyBudgetCents;
    }

    public Ledger withMonthlyBudgetCents(long nextBudgetCents) {
        return new Ledger(id, name, nextBudgetCents);
    }

    private static String requireText(String value, String message) {
        String trimmed = Objects.requireNonNull(value, message).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}

package com.example.pkmapp.savings;

import java.util.Objects;

public final class SavingsGoal {
    private final String id;
    private final String name;
    private final long targetCents;
    private final long savedCents;
    private final long createdAtMillis;

    public SavingsGoal(String id, String name, long targetCents, long savedCents,
            long createdAtMillis) {
        this.id = requireText(id, "目标编号不能为空");
        this.name = requireText(name, "目标名称不能为空");
        if (targetCents <= 0L) {
            throw new IllegalArgumentException("目标金额必须大于零");
        }
        if (savedCents < 0L) {
            throw new IllegalArgumentException("已存金额不能为负数");
        }
        if (createdAtMillis <= 0L) {
            throw new IllegalArgumentException("创建日期不能为空");
        }
        this.targetCents = targetCents;
        this.savedCents = savedCents;
        this.createdAtMillis = createdAtMillis;
    }

    public SavingsGoal deposit(long amountCents) {
        if (amountCents <= 0L) {
            throw new IllegalArgumentException("存入金额必须大于零");
        }
        long nextSaved = savedCents + amountCents;
        return new SavingsGoal(id, name, targetCents, nextSaved, createdAtMillis);
    }

    public SavingsGoal updateDetails(String updatedName, long updatedTargetCents) {
        return new SavingsGoal(id, updatedName, updatedTargetCents, savedCents, createdAtMillis);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getTargetCents() {
        return targetCents;
    }

    public long getSavedCents() {
        return savedCents;
    }

    public long getRemainingCents() {
        return Math.max(0L, targetCents - savedCents);
    }

    public int getProgressPercent() {
        return (int) Math.min(100L, savedCents * 100L / targetCents);
    }

    public boolean isComplete() {
        return savedCents >= targetCents;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    private static String requireText(String value, String message) {
        String trimmed = Objects.requireNonNull(value, message).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}

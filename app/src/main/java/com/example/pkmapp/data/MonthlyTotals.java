package com.example.pkmapp.data;

public final class MonthlyTotals {
    private final long incomeInCents;
    private final long expenseInCents;

    public MonthlyTotals(long incomeInCents, long expenseInCents) {
        this.incomeInCents = incomeInCents;
        this.expenseInCents = expenseInCents;
    }

    public long getIncomeInCents() {
        return incomeInCents;
    }

    public long getExpenseInCents() {
        return expenseInCents;
    }

    public long getBalanceInCents() {
        return incomeInCents - expenseInCents;
    }
}

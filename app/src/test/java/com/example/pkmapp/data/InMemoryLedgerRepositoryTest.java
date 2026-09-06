package com.example.pkmapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class InMemoryLedgerRepositoryTest {
    @Test
    public void addTransaction_notifiesListenerAndUpdatesOnlyActiveLedger() {
        InMemoryLedgerRepository repository = InMemoryLedgerRepository.createForTest();
        AtomicInteger notifications = new AtomicInteger();
        LedgerDataListener listener = notifications::incrementAndGet;
        repository.addListener(listener);

        Ledger first = repository.getCurrentLedger();
        Ledger second = repository.createLedger("旅行账本");
        repository.switchLedger(second.getId());
        repository.addTransaction(TransactionType.EXPENSE, 2_580L, "餐饮", "午饭",
                noonUtc(2026, Calendar.SEPTEMBER, 6));

        assertEquals(3, notifications.get());
        assertEquals(1, repository.getTransactionsForCurrentLedger().size());
        repository.switchLedger(first.getId());
        assertTrue(repository.getTransactionsForCurrentLedger().isEmpty());
    }

    @Test
    public void currentMonthTotals_separatesIncomeExpenseAndComputesBalance() {
        InMemoryLedgerRepository repository = InMemoryLedgerRepository.createForTest();
        repository.addTransaction(TransactionType.INCOME, 100_000L, "工资", "九月工资",
                noonUtc(2026, Calendar.SEPTEMBER, 1));
        repository.addTransaction(TransactionType.EXPENSE, 2_580L, "餐饮", "午饭",
                noonUtc(2026, Calendar.SEPTEMBER, 6));
        repository.addTransaction(TransactionType.EXPENSE, 3_000L, "交通", "八月车票",
                noonUtc(2026, Calendar.AUGUST, 30));

        MonthlyTotals totals = repository.getCurrentMonthTotals(
                noonUtc(2026, Calendar.SEPTEMBER, 15));

        assertEquals(100_000L, totals.getIncomeInCents());
        assertEquals(2_580L, totals.getExpenseInCents());
        assertEquals(97_420L, totals.getBalanceInCents());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addTransaction_rejectsZeroAmount() {
        InMemoryLedgerRepository.createForTest().addTransaction(
                TransactionType.EXPENSE, 0L, "餐饮", "", noonUtc(2026, Calendar.SEPTEMBER, 6));
    }

    private static long noonUtc(int year, int month, int dayOfMonth) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month, dayOfMonth, 12, 0, 0);
        return calendar.getTimeInMillis();
    }
}

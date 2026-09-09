package com.example.pkmapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.List;
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

    @Test
    public void currentLedgerNetFlow_sumsAllIncomeAndExpenses() {
        InMemoryLedgerRepository repository = InMemoryLedgerRepository.createForTest();
        repository.addTransaction(TransactionType.INCOME, 100_000L, "工资", "收入",
                noonUtc(2026, Calendar.JANUARY, 1));
        repository.addTransaction(TransactionType.EXPENSE, 2_580L, "餐饮", "支出",
                noonUtc(2026, Calendar.AUGUST, 6));

        assertEquals(97_420L, repository.getCurrentLedgerNetFlowInCents());
    }

    @Test
    public void recreatedRepository_retainsSavedTransaction() {
        MemoryPersistence persistence = new MemoryPersistence();
        InMemoryLedgerRepository firstLaunch = InMemoryLedgerRepository.createForTest(persistence);
        firstLaunch.addTransaction(TransactionType.EXPENSE, 1_280L, "餐饮", "早餐",
                noonUtc(2026, Calendar.SEPTEMBER, 9));

        InMemoryLedgerRepository afterRestart = InMemoryLedgerRepository.createForTest(persistence);

        assertEquals(1, afterRestart.getTransactionsForCurrentLedger().size());
        assertEquals("早餐", afterRestart.getTransactionsForCurrentLedger().get(0).getNote());
    }

    @Test
    public void deleteTransaction_removesRecord_recalculatesTotals_andPersists() {
        MemoryPersistence persistence = new MemoryPersistence();
        InMemoryLedgerRepository repository = InMemoryLedgerRepository.createForTest(persistence);
        repository.addTransaction(TransactionType.INCOME, 100_000L, "工资", "收入",
                noonUtc(2026, Calendar.SEPTEMBER, 1));
        Transaction expense = repository.addTransaction(TransactionType.EXPENSE, 2_580L, "餐饮", "午饭",
                noonUtc(2026, Calendar.SEPTEMBER, 6));

        assertTrue(repository.deleteTransaction(expense.getId()));

        assertTrue(repository.getTransactionsForCurrentLedger().stream()
                .noneMatch(transaction -> transaction.getId().equals(expense.getId())));
        MonthlyTotals totals = repository.getCurrentMonthTotals(
                noonUtc(2026, Calendar.SEPTEMBER, 15));
        assertEquals(100_000L, totals.getIncomeInCents());
        assertEquals(0L, totals.getExpenseInCents());
        assertEquals(100_000L, totals.getBalanceInCents());
        InMemoryLedgerRepository afterRestart = InMemoryLedgerRepository.createForTest(persistence);
        assertEquals(1, afterRestart.getTransactionsForCurrentLedger().size());
    }

    @Test
    public void deleteTransaction_unknownId_doesNotChangeDataOrNotify() {
        InMemoryLedgerRepository repository = InMemoryLedgerRepository.createForTest();
        Transaction transaction = repository.addTransaction(TransactionType.EXPENSE, 1_280L, "餐饮", "早餐",
                noonUtc(2026, Calendar.SEPTEMBER, 9));
        AtomicInteger notifications = new AtomicInteger();
        repository.addListener(notifications::incrementAndGet);

        assertFalse(repository.deleteTransaction("missing-transaction"));

        assertEquals(1, repository.getTransactionsForCurrentLedger().size());
        assertEquals(transaction.getId(), repository.getTransactionsForCurrentLedger().get(0).getId());
        assertEquals(0, notifications.get());
    }

    @Test
    public void resetToInitialState_replacesAllLedgersAndTransactions_andPersists() {
        MemoryPersistence persistence = new MemoryPersistence();
        InMemoryLedgerRepository repository = InMemoryLedgerRepository.createForTest(persistence);
        repository.createLedger("旅行账本");
        repository.addTransaction(TransactionType.EXPENSE, 1_280L, "餐饮", "早餐",
                noonUtc(2026, Calendar.SEPTEMBER, 9));
        AtomicInteger notifications = new AtomicInteger();
        repository.addListener(notifications::incrementAndGet);

        repository.resetToInitialState();

        assertEquals(1, repository.getLedgers().size());
        assertEquals("生活账本", repository.getCurrentLedger().getName());
        assertTrue(repository.getTransactionsForCurrentLedger().isEmpty());
        assertEquals(1, notifications.get());
        InMemoryLedgerRepository afterRestart = InMemoryLedgerRepository.createForTest(persistence);
        assertEquals(1, afterRestart.getLedgers().size());
        assertEquals("生活账本", afterRestart.getCurrentLedger().getName());
        assertTrue(afterRestart.getTransactionsForCurrentLedger().isEmpty());
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

    private static final class MemoryPersistence implements LedgerPersistence {
        private String encoded;

        @Override
        public LedgerCodec.State load() {
            return LedgerCodec.decode(encoded);
        }

        @Override
        public void save(List<Ledger> ledgers, List<Transaction> transactions,
                String currentLedgerId) {
            encoded = LedgerCodec.encode(ledgers, transactions, currentLedgerId);
        }
    }
}

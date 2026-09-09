package com.example.pkmapp.data;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public final class InMemoryLedgerRepository {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final Comparator<Transaction> TRANSACTION_ORDER =
            Comparator.comparingLong(Transaction::getOccurredAtMillis).reversed()
                    .thenComparing(Transaction::getId, Comparator.reverseOrder());

    private static final InMemoryLedgerRepository INSTANCE = new InMemoryLedgerRepository();

    private final Map<String, Ledger> ledgers = new LinkedHashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final Set<LedgerDataListener> listeners = new CopyOnWriteArraySet<>();
    private String currentLedgerId;
    private LedgerPersistence persistence;

    private InMemoryLedgerRepository() {
        this(null);
    }

    InMemoryLedgerRepository(LedgerPersistence persistence) {
        Ledger defaultLedger = new Ledger(nextId(), "生活账本");
        ledgers.put(defaultLedger.getId(), defaultLedger);
        currentLedgerId = defaultLedger.getId();
        this.persistence = persistence;
        if (persistence != null) {
            restore(persistence.load());
            persist();
        }
    }

    public static InMemoryLedgerRepository getInstance() {
        return INSTANCE;
    }

    public static void initialize(Context context) {
        INSTANCE.attachPersistence(new SharedPreferencesLedgerPersistence(context));
    }

    static InMemoryLedgerRepository createForTest() {
        return new InMemoryLedgerRepository();
    }

    static InMemoryLedgerRepository createForTest(LedgerPersistence persistence) {
        return new InMemoryLedgerRepository(persistence);
    }

    private synchronized void attachPersistence(LedgerPersistence persistence) {
        if (this.persistence != null) {
            return;
        }
        this.persistence = Objects.requireNonNull(persistence, "账本存储不能为空");
        restore(persistence.load());
        persist();
    }

    public synchronized List<Ledger> getLedgers() {
        return Collections.unmodifiableList(new ArrayList<>(ledgers.values()));
    }

    public synchronized Ledger getCurrentLedger() {
        return ledgers.get(currentLedgerId);
    }

    public Ledger createLedger(String name) {
        Ledger ledger = new Ledger(nextId(), name);
        synchronized (this) {
            ledgers.put(ledger.getId(), ledger);
        }
        persist();
        notifyListeners();
        return ledger;
    }

    public void switchLedger(String ledgerId) {
        String selectedId = requireId(ledgerId);
        boolean changed;
        synchronized (this) {
            if (!ledgers.containsKey(selectedId)) {
                throw new IllegalArgumentException("账本不存在");
            }
            changed = !selectedId.equals(currentLedgerId);
            currentLedgerId = selectedId;
        }
        if (changed) {
            persist();
            notifyListeners();
        }
    }

    public Transaction addTransaction(TransactionType type, long amountInCents,
            String category, String note, long occurredAtMillis) {
        Transaction transaction;
        synchronized (this) {
            transaction = new Transaction(nextId(), currentLedgerId, type, amountInCents, category,
                    note, occurredAtMillis);
            transactions.add(transaction);
        }
        persist();
        notifyListeners();
        return transaction;
    }

    public boolean deleteTransaction(String transactionId) {
        String selectedId = requireId(transactionId);
        boolean deleted = false;
        synchronized (this) {
            for (int index = transactions.size() - 1; index >= 0; index--) {
                Transaction transaction = transactions.get(index);
                if (currentLedgerId.equals(transaction.getLedgerId())
                        && selectedId.equals(transaction.getId())) {
                    transactions.remove(index);
                    deleted = true;
                    break;
                }
            }
        }
        if (deleted) {
            persist();
            notifyListeners();
        }
        return deleted;
    }

    /** Restores the ledger repository to the same empty state as a first launch. */
    public void resetToInitialState() {
        synchronized (this) {
            ledgers.clear();
            transactions.clear();
            Ledger defaultLedger = new Ledger(nextId(), "生活账本");
            ledgers.put(defaultLedger.getId(), defaultLedger);
            currentLedgerId = defaultLedger.getId();
        }
        persist();
        notifyListeners();
    }

    public synchronized List<Transaction> getTransactionsForCurrentLedger() {
        List<Transaction> currentTransactions = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (currentLedgerId.equals(transaction.getLedgerId())) {
                currentTransactions.add(transaction);
            }
        }
        currentTransactions.sort(TRANSACTION_ORDER);
        return Collections.unmodifiableList(currentTransactions);
    }

    public synchronized MonthlyTotals getCurrentMonthTotals(long referenceTimeMillis) {
        if (referenceTimeMillis <= 0L) {
            throw new IllegalArgumentException("日期不能为空");
        }
        Calendar reference = utcCalendar(referenceTimeMillis);
        int year = reference.get(Calendar.YEAR);
        int month = reference.get(Calendar.MONTH);
        long income = 0L;
        long expense = 0L;
        for (Transaction transaction : transactions) {
            if (!currentLedgerId.equals(transaction.getLedgerId())) {
                continue;
            }
            Calendar date = utcCalendar(transaction.getOccurredAtMillis());
            if (date.get(Calendar.YEAR) != year || date.get(Calendar.MONTH) != month) {
                continue;
            }
            if (transaction.getType() == TransactionType.INCOME) {
                income += transaction.getAmountInCents();
            } else {
                expense += transaction.getAmountInCents();
            }
        }
        return new MonthlyTotals(income, expense);
    }

    public synchronized long getCurrentLedgerNetFlowInCents() {
        long netFlow = 0L;
        for (Transaction transaction : transactions) {
            if (!currentLedgerId.equals(transaction.getLedgerId())) {
                continue;
            }
            netFlow += transaction.getType() == TransactionType.INCOME
                    ? transaction.getAmountInCents() : -transaction.getAmountInCents();
        }
        return netFlow;
    }

    public void addListener(LedgerDataListener listener) {
        listeners.add(Objects.requireNonNull(listener, "监听器不能为空"));
    }

    public void removeListener(LedgerDataListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private synchronized void restore(LedgerCodec.State state) {
        if (state == null || state.getLedgers().isEmpty()) {
            return;
        }
        ledgers.clear();
        transactions.clear();
        for (Ledger ledger : state.getLedgers()) {
            ledgers.put(ledger.getId(), ledger);
        }
        transactions.addAll(state.getTransactions());
        if (ledgers.containsKey(state.getCurrentLedgerId())) {
            currentLedgerId = state.getCurrentLedgerId();
        } else {
            currentLedgerId = state.getLedgers().get(0).getId();
        }
    }

    private void persist() {
        LedgerPersistence storage;
        List<Ledger> ledgerSnapshot;
        List<Transaction> transactionSnapshot;
        String selectedLedgerId;
        synchronized (this) {
            storage = persistence;
            if (storage == null) {
                return;
            }
            ledgerSnapshot = new ArrayList<>(ledgers.values());
            transactionSnapshot = new ArrayList<>(transactions);
            selectedLedgerId = currentLedgerId;
        }
        storage.save(ledgerSnapshot, transactionSnapshot, selectedLedgerId);
    }

    private void notifyListeners() {
        for (LedgerDataListener listener : listeners) {
            listener.onLedgerDataChanged();
        }
    }

    private static String nextId() {
        return UUID.randomUUID().toString();
    }

    private static String requireId(String ledgerId) {
        String trimmed = Objects.requireNonNull(ledgerId, "账本编号不能为空").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("账本编号不能为空");
        }
        return trimmed;
    }

    private static Calendar utcCalendar(long millis) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.setTimeInMillis(millis);
        return calendar;
    }

    private static long noonUtc(int year, int month, int dayOfMonth) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.clear();
        calendar.set(year, month, dayOfMonth, 12, 0, 0);
        return calendar.getTimeInMillis();
    }
}

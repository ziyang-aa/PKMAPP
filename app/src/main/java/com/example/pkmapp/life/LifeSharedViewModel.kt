package com.example.pkmapp.life

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pkmapp.borrowing.BorrowingDirection
import com.example.pkmapp.borrowing.BorrowingRecord
import com.example.pkmapp.borrowing.BorrowingRecordStore
import com.example.pkmapp.borrowing.BorrowingTransactionSync
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.AppDatabase
import com.example.pkmapp.core.database.LocalAccountEntity
import com.example.pkmapp.core.datastore.AppPreferences
import com.example.pkmapp.core.datastore.AppPreferencesSnapshot
import com.example.pkmapp.data.AssetSnapshot
import com.example.pkmapp.data.AssetStore
import com.example.pkmapp.data.InMemoryLedgerRepository
import com.example.pkmapp.data.Ledger
import com.example.pkmapp.data.LedgerDataListener
import com.example.pkmapp.data.MonthlyTotals
import com.example.pkmapp.data.Transaction
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.profile.AvatarStore
import com.example.pkmapp.profile.CheckInProgress
import com.example.pkmapp.record.CategoryStore
import com.example.pkmapp.savings.SavingsGoal
import com.example.pkmapp.savings.SavingsGoalStore
import java.util.Calendar
import java.util.UUID
import java.util.function.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LifeSnapshot(
    val ledgers: List<Ledger>,
    val currentLedger: Ledger,
    val transactions: List<Transaction>,
    val transactionCountByLedger: Map<String, Int>,
    val currentLedgerNetFlowCents: Long,
    val currentMonthTotals: MonthlyTotals,
    val assetSnapshot: AssetSnapshot,
    val borrowingRecords: List<BorrowingRecord>,
    val savingsGoals: List<SavingsGoal>,
    val expenseCategories: List<String>,
    val incomeCategories: List<String>,
    val nickname: String,
    val checkedDates: Set<String>,
    val revision: Long = 0L
) {
    val totalCategoryCount: Int
        get() = expenseCategories.size + incomeCategories.size
}

interface LifeDataSource {
    val repository: InMemoryLedgerRepository
    fun getAssetSnapshot(ledgerId: String, netFlowCents: Long): AssetSnapshot
    fun saveStartingAssets(ledgerId: String, initialAssetsCents: Long, liabilitiesCents: Long)
    fun addLiability(ledgerId: String, amountCents: Long)
    fun deleteAssetsForLedger(ledgerId: String)
    fun clearAssets()

    fun getBorrowingRecordsForLedger(ledgerId: String): List<BorrowingRecord>
    fun addBorrowingRecord(record: BorrowingRecord)
    fun deleteBorrowingsForLedger(ledgerId: String)
    fun clearBorrowings()

    fun getSavingsGoals(): List<SavingsGoal>
    fun createSavingsGoal(name: String, targetCents: Long): SavingsGoal
    fun updateSavingsGoal(goalId: String, name: String, targetCents: Long): SavingsGoal
    fun depositSavingsGoal(goalId: String, amountCents: Long): SavingsGoal
    fun deleteSavingsGoal(goalId: String): Boolean
    fun clearSavingsGoals()

    fun getCategories(type: TransactionType): List<String>
    fun addCustomCategory(type: TransactionType, name: String): Boolean
    fun deleteCategory(type: TransactionType, name: String): Boolean
    fun clearCategories() {}

    fun getNickname(): String
    fun setNickname(nickname: String)
    fun getCheckedDates(): Set<String>
    fun saveCheckedDates(dates: Set<String>)
    fun clearProfileAndAvatar()
    fun nowMillis(): Long = System.currentTimeMillis()
}

internal class AndroidLifeDataSource(context: Context) : LifeDataSource {
    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences.getInstance(appContext)
    private val localAccountDao = AppDatabase.getInstance(appContext).localAccountDao()
    private val assetStore = AssetStore(appContext)
    private val borrowingStore = BorrowingRecordStore(appContext)
    private val savingsStore = SavingsGoalStore(appContext)
    private val categoryStore = CategoryStore(appContext)

    override val repository: InMemoryLedgerRepository = InMemoryLedgerRepository.getInstance().also {
        InMemoryLedgerRepository.initialize(appContext)
    }

    private fun activeAccount(): LocalAccountEntity? =
        localAccountDao.findByOwnerId(AccountScope.currentOwnerId(appContext))

    override fun getAssetSnapshot(ledgerId: String, netFlowCents: Long): AssetSnapshot =
        assetStore.getSnapshot(ledgerId, netFlowCents)

    override fun saveStartingAssets(ledgerId: String, initialAssetsCents: Long, liabilitiesCents: Long) =
        assetStore.saveStartingValues(ledgerId, initialAssetsCents, liabilitiesCents)

    override fun addLiability(ledgerId: String, amountCents: Long) =
        assetStore.addLiability(ledgerId, amountCents)

    override fun deleteAssetsForLedger(ledgerId: String) =
        assetStore.deleteStartingValues(ledgerId)

    override fun clearAssets() = assetStore.clear()

    override fun getBorrowingRecordsForLedger(ledgerId: String): List<BorrowingRecord> =
        borrowingStore.getRecordsForLedger(ledgerId)

    override fun addBorrowingRecord(record: BorrowingRecord) = borrowingStore.add(record)

    override fun deleteBorrowingsForLedger(ledgerId: String) =
        borrowingStore.deleteForLedger(ledgerId)

    override fun clearBorrowings() = borrowingStore.clear()

    override fun getSavingsGoals(): List<SavingsGoal> = savingsStore.getGoals()

    override fun createSavingsGoal(name: String, targetCents: Long): SavingsGoal =
        savingsStore.createGoal(name, targetCents)

    override fun updateSavingsGoal(goalId: String, name: String, targetCents: Long): SavingsGoal =
        savingsStore.updateGoal(goalId, name, targetCents)

    override fun depositSavingsGoal(goalId: String, amountCents: Long): SavingsGoal =
        savingsStore.deposit(goalId, amountCents)

    override fun deleteSavingsGoal(goalId: String): Boolean =
        savingsStore.deleteGoal(goalId)

    override fun clearSavingsGoals() = savingsStore.clear()

    override fun getCategories(type: TransactionType): List<String> =
        categoryStore.getCategories(type)

    override fun addCustomCategory(type: TransactionType, name: String): Boolean =
        categoryStore.addCustomCategory(type, name)

    override fun deleteCategory(type: TransactionType, name: String): Boolean =
        categoryStore.deleteCategory(type, name)

    override fun clearCategories() = categoryStore.clear()

    override fun getNickname(): String {
        val account = activeAccount()
        return account?.nickname?.takeIf { it.isNotBlank() } ?: AppPreferencesSnapshot.DEFAULT_NICKNAME
    }

    override fun setNickname(nickname: String) {
        val ownerId = AccountScope.currentOwnerId(appContext)
        localAccountDao.updateNickname(ownerId, nickname, System.currentTimeMillis())
        appPreferences.setNickname(nickname)
    }

    override fun getCheckedDates(): Set<String> {
        val account = activeAccount() ?: return HashSet(appPreferences.current.checkedDates)
        return try {
            val arr = org.json.JSONArray(account.checkedDatesJson.ifBlank { "[]" })
            val out = HashSet<String>(arr.length())
            for (i in 0 until arr.length()) {
                val date = arr.optString(i, "").trim()
                if (date.isNotEmpty()) out.add(date)
            }
            out
        } catch (_: Exception) {
            emptySet()
        }
    }

    override fun saveCheckedDates(dates: Set<String>) {
        val ownerId = AccountScope.currentOwnerId(appContext)
        val arr = org.json.JSONArray()
        for (date in dates.sorted()) {
            arr.put(date)
        }
        localAccountDao.updateCheckedDates(ownerId, arr.toString(), System.currentTimeMillis())
        appPreferences.setCheckedDates(dates)
    }

    override fun clearProfileAndAvatar() {
        val ownerId = AccountScope.currentOwnerId(appContext)
        val now = System.currentTimeMillis()
        localAccountDao.updateNickname(ownerId, AppPreferencesSnapshot.DEFAULT_NICKNAME, now)
        localAccountDao.updateCheckedDates(ownerId, "[]", now)
        appPreferences.clearProfileIdentity()
        AvatarStore.clear(appContext, ownerId)
    }
}

class LifeSharedViewModel(
    private val dataSource: LifeDataSource
) : ViewModel() {

    private var revisionCounter = 0L
    private val ledgerListener = LedgerDataListener { refresh() }
    private val _snapshot: MutableStateFlow<LifeSnapshot>

    init {
        dataSource.repository.addListener(ledgerListener)
        _snapshot = MutableStateFlow(buildSnapshot())
    }

    val snapshot: StateFlow<LifeSnapshot> = _snapshot.asStateFlow()

    val currentSnapshot: LifeSnapshot
        get() = _snapshot.value

    fun refresh() {
        _snapshot.value = buildSnapshot()
    }

    fun reloadForAccountSwitch() {
        dataSource.repository.reloadFromPersistence()
        refresh()
    }

    fun observe(owner: LifecycleOwner, observer: Consumer<LifeSnapshot>) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                snapshot.collect { observer.accept(it) }
            }
        }
    }

    fun switchLedger(ledgerId: String) {
        dataSource.repository.switchLedger(ledgerId)
    }

    fun createAndSwitchLedger(name: String): Ledger {
        val ledger = dataSource.repository.createLedger(name)
        dataSource.repository.switchLedger(ledger.id)
        return ledger
    }

    fun deleteLedger(ledgerId: String) {
        if (dataSource.repository.ledgers.size <= 1) {
            throw IllegalStateException("至少保留一本账本")
        }
        dataSource.deleteBorrowingsForLedger(ledgerId)
        dataSource.deleteAssetsForLedger(ledgerId)
        dataSource.repository.deleteLedger(ledgerId)
        refresh()
    }

    fun addTransaction(
        type: TransactionType,
        amountInCents: Long,
        category: String,
        note: String,
        occurredAtMillis: Long
    ): Transaction = dataSource.repository.addTransaction(type, amountInCents, category, note, occurredAtMillis)

    fun updateTransaction(
        transactionId: String,
        type: TransactionType,
        amountInCents: Long,
        category: String,
        note: String,
        occurredAtMillis: Long
    ): Transaction = dataSource.repository.updateTransaction(
        transactionId, type, amountInCents, category, note, occurredAtMillis
    )

    fun deleteTransaction(transactionId: String): Boolean =
        dataSource.repository.deleteTransaction(transactionId)

    fun addLiabilityForCurrentLedger(amountCents: Long) {
        dataSource.addLiability(dataSource.repository.currentLedger.id, amountCents)
        refresh()
    }

    fun saveStartingAssets(initialAssetsCents: Long, liabilitiesCents: Long) {
        dataSource.saveStartingAssets(
            dataSource.repository.currentLedger.id,
            initialAssetsCents,
            liabilitiesCents
        )
        refresh()
    }

    fun getCategories(type: TransactionType): List<String> = dataSource.getCategories(type)

    fun addCustomCategory(type: TransactionType, name: String): Boolean {
        val added = dataSource.addCustomCategory(type, name)
        if (added) refresh()
        return added
    }

    fun deleteCategory(type: TransactionType, name: String): Boolean {
        val deleted = dataSource.deleteCategory(type, name)
        if (deleted) refresh()
        return deleted
    }

    fun createSavingsGoal(name: String, targetCents: Long): SavingsGoal {
        val created = dataSource.createSavingsGoal(name, targetCents)
        refresh()
        return created
    }

    fun updateSavingsGoal(goalId: String, name: String, targetCents: Long): SavingsGoal {
        val updated = dataSource.updateSavingsGoal(goalId, name, targetCents)
        refresh()
        return updated
    }

    fun depositSavingsGoal(goalId: String, amountCents: Long, nowMillis: Long = dataSource.nowMillis()): SavingsGoal {
        val updated = dataSource.depositSavingsGoal(goalId, amountCents)
        dataSource.repository.addTransaction(
            TransactionType.INCOME,
            amountCents,
            "攒钱 · ${updated.name}",
            "为「${updated.name}」存入",
            nowMillis
        )
        refresh()
        return updated
    }

    fun deleteSavingsGoal(goalId: String): Boolean {
        val removed = dataSource.deleteSavingsGoal(goalId)
        if (removed) refresh()
        return removed
    }

    fun addBorrowingRecord(
        direction: BorrowingDirection,
        amountCents: Long,
        occurredAtMillis: Long,
        person: String,
        note: String,
        sourceTxId: String?
    ): BorrowingRecord {
        val record = BorrowingRecord(
            UUID.randomUUID().toString(),
            dataSource.repository.currentLedger.id,
            direction,
            amountCents,
            occurredAtMillis,
            person,
            note,
            sourceTxId
        )
        dataSource.addBorrowingRecord(record)
        if (BorrowingTransactionSync.shouldCreateMainTransaction(sourceTxId)) {
            dataSource.repository.addTransaction(
                BorrowingTransactionSync.transactionType(direction),
                amountCents,
                BorrowingTransactionSync.category(direction),
                BorrowingTransactionSync.note(direction, person, note),
                occurredAtMillis
            )
        }
        refresh()
        return record
    }

    fun updateNickname(nickname: String) {
        dataSource.setNickname(nickname)
        refresh()
    }

    fun checkInToday(todayKey: String): Boolean {
        val dates = HashSet(dataSource.getCheckedDates())
        val checked = CheckInProgress.checkInToday(todayKey, todayKey, dates)
        if (checked) {
            dataSource.saveCheckedDates(dates)
            refresh()
        }
        return checked
    }

    fun clearAllUserData() {
        dataSource.clearSavingsGoals()
        dataSource.clearBorrowings()
        dataSource.clearAssets()
        dataSource.clearCategories()
        dataSource.clearProfileAndAvatar()
        dataSource.repository.resetToInitialState()
        refresh()
    }

    fun monthTotalsFor(referenceMillis: Long): MonthlyTotals =
        dataSource.repository.getCurrentMonthTotals(referenceMillis)

    fun transactionsForMonth(year: Int, month: Int): List<Transaction> {
        val result = ArrayList<Transaction>()
        val cal = Calendar.getInstance()
        for (tx in _snapshot.value.transactions) {
            cal.timeInMillis = tx.occurredAtMillis
            if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                result.add(tx)
            }
        }
        return result
    }

    override fun onCleared() {
        dataSource.repository.removeListener(ledgerListener)
        super.onCleared()
    }

    private fun buildSnapshot(): LifeSnapshot {
        val repo = dataSource.repository
        val ledgers = repo.ledgers
        val currentLedger = repo.currentLedger
        val transactions = repo.transactionsForCurrentLedger
        val counts = LinkedHashMap<String, Int>()
        for (tx in repo.allTransactions) {
            counts[tx.ledgerId] = (counts[tx.ledgerId] ?: 0) + 1
        }
        val netFlow = repo.currentLedgerNetFlowInCents
        val now = maxOf(1L, dataSource.nowMillis())
        val monthTotals = repo.getCurrentMonthTotals(now)
        val assets = dataSource.getAssetSnapshot(currentLedger.id, netFlow)
        val borrowings = dataSource.getBorrowingRecordsForLedger(currentLedger.id)
        val savings = dataSource.getSavingsGoals()
        val expCats = dataSource.getCategories(TransactionType.EXPENSE)
        val incCats = dataSource.getCategories(TransactionType.INCOME)
        return LifeSnapshot(
            ledgers = ledgers,
            currentLedger = currentLedger,
            transactions = transactions,
            transactionCountByLedger = counts,
            currentLedgerNetFlowCents = netFlow,
            currentMonthTotals = monthTotals,
            assetSnapshot = assets,
            borrowingRecords = borrowings,
            savingsGoals = savings,
            expenseCategories = expCats,
            incomeCategories = incCats,
            nickname = dataSource.getNickname(),
            checkedDates = dataSource.getCheckedDates(),
            revision = ++revisionCounter
        )
    }

    companion object {
        @JvmStatic
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    LifeSharedViewModel(AndroidLifeDataSource(appContext))
                }
            }
        }

        @JvmStatic
        fun get(activity: FragmentActivity): LifeSharedViewModel =
            ViewModelProvider(activity, factory(activity))[LifeSharedViewModel::class.java]
    }
}

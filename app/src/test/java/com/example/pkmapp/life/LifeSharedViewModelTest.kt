package com.example.pkmapp.life

import com.example.pkmapp.borrowing.BorrowingDirection
import com.example.pkmapp.borrowing.BorrowingRecord
import com.example.pkmapp.data.AssetSnapshot
import com.example.pkmapp.data.InMemoryLedgerRepository
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.data.newTestLedgerRepository
import com.example.pkmapp.record.CategoryStore
import com.example.pkmapp.savings.SavingsGoal
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeSharedViewModelTest {

    private class FakeLifeDataSource(
        override val repository: InMemoryLedgerRepository = newTestLedgerRepository(),
        var fixedNowMillis: Long = 1_763_971_200_000L
    ) : LifeDataSource {
        private val initialAssets = HashMap<String, Long>()
        private val liabilities = HashMap<String, Long>()
        private val borrowings = ArrayList<BorrowingRecord>()
        private val goals = ArrayList<SavingsGoal>()
        private val expCategories = ArrayList(CategoryStore.defaultCategories(TransactionType.EXPENSE))
        private val incCategories = ArrayList(CategoryStore.defaultCategories(TransactionType.INCOME))
        private var currentNickname = AndroidLifeDataSource.DEFAULT_NICKNAME
        private val dates = LinkedHashSet<String>()
        var avatarClearedCount = 0

        override fun getAssetSnapshot(ledgerId: String, netFlowCents: Long): AssetSnapshot =
            AssetSnapshot(initialAssets[ledgerId] ?: 0L, liabilities[ledgerId] ?: 0L, netFlowCents)

        override fun saveStartingAssets(ledgerId: String, initialAssetsCents: Long, liabilitiesCents: Long) {
            initialAssets[ledgerId] = initialAssetsCents
            liabilities[ledgerId] = liabilitiesCents
        }

        override fun addLiability(ledgerId: String, amountCents: Long) {
            liabilities[ledgerId] = (liabilities[ledgerId] ?: 0L) + amountCents
        }

        override fun deleteAssetsForLedger(ledgerId: String) {
            initialAssets.remove(ledgerId)
            liabilities.remove(ledgerId)
        }

        override fun clearAssets() {
            initialAssets.clear()
            liabilities.clear()
        }

        override fun getBorrowingRecordsForLedger(ledgerId: String): List<BorrowingRecord> =
            borrowings.filter { it.ledgerId == ledgerId }.sortedByDescending { it.occurredAtMillis }

        override fun addBorrowingRecord(record: BorrowingRecord) {
            borrowings.removeAll { it.id == record.id }
            borrowings.add(record)
        }

        override fun deleteBorrowingsForLedger(ledgerId: String) {
            borrowings.removeAll { it.ledgerId == ledgerId }
        }

        override fun clearBorrowings() {
            borrowings.clear()
        }

        override fun getSavingsGoals(): List<SavingsGoal> = ArrayList(goals)

        override fun createSavingsGoal(name: String, targetCents: Long): SavingsGoal {
            val goal = SavingsGoal(UUID.randomUUID().toString(), name, targetCents, 0L, fixedNowMillis)
            goals.add(goal)
            return goal
        }

        override fun updateSavingsGoal(goalId: String, name: String, targetCents: Long): SavingsGoal {
            val idx = goals.indexOfFirst { it.id == goalId }
            val updated = goals[idx].updateDetails(name, targetCents)
            goals[idx] = updated
            return updated
        }

        override fun depositSavingsGoal(goalId: String, amountCents: Long): SavingsGoal {
            val idx = goals.indexOfFirst { it.id == goalId }
            val updated = goals[idx].deposit(amountCents)
            goals[idx] = updated
            return updated
        }

        override fun deleteSavingsGoal(goalId: String): Boolean =
            goals.removeIf { it.id == goalId }

        override fun clearSavingsGoals() {
            goals.clear()
        }

        override fun getCategories(type: TransactionType): List<String> =
            if (type == TransactionType.EXPENSE) ArrayList(expCategories) else ArrayList(incCategories)

        override fun addCustomCategory(type: TransactionType, name: String): Boolean {
            val list = if (type == TransactionType.EXPENSE) expCategories else incCategories
            val trimmed = name.trim()
            if (trimmed.isEmpty() || list.contains(trimmed)) return false
            list.add(trimmed)
            return true
        }

        override fun deleteCategory(type: TransactionType, name: String): Boolean {
            val list = if (type == TransactionType.EXPENSE) expCategories else incCategories
            return list.remove(name.trim())
        }

        override fun getNickname(): String = currentNickname

        override fun setNickname(nickname: String) {
            currentNickname = nickname
        }

        override fun getCheckedDates(): Set<String> = LinkedHashSet(dates)

        override fun saveCheckedDates(dates: Set<String>) {
            this.dates.clear()
            this.dates.addAll(dates)
        }

        override fun clearProfileAndAvatar() {
            currentNickname = AndroidLifeDataSource.DEFAULT_NICKNAME
            dates.clear()
            avatarClearedCount++
        }

        override fun nowMillis(): Long = fixedNowMillis
    }

    @Test
    fun addAndDeleteTransactions_synchronizesMonthTotalsAndAssetSnapshot() {
        val fake = FakeLifeDataSource()
        val vm = LifeSharedViewModel(fake)
        vm.saveStartingAssets(initialAssetsCents = 100_00L, liabilitiesCents = 20_00L)

        vm.addTransaction(TransactionType.EXPENSE, 15_50L, "餐饮", "午饭", fake.fixedNowMillis)
        vm.addTransaction(TransactionType.INCOME, 50_00L, "兼职", "助教", fake.fixedNowMillis)

        val snap = vm.snapshot.value
        assertEquals(2, snap.transactions.size)
        assertEquals(15_50L, snap.currentMonthTotals.expenseInCents)
        assertEquals(50_00L, snap.currentMonthTotals.incomeInCents)
        assertEquals(34_50L, snap.currentLedgerNetFlowCents)
        assertEquals(134_50L, snap.assetSnapshot.currentAssetsInCents)
        assertEquals(114_50L, snap.assetSnapshot.netWorthInCents)

        val expenseId = snap.transactions.first { it.type == TransactionType.EXPENSE }.id
        assertTrue(vm.deleteTransaction(expenseId))
        val afterDelete = vm.snapshot.value
        assertEquals(1, afterDelete.transactions.size)
        assertEquals(0L, afterDelete.currentMonthTotals.expenseInCents)
        assertEquals(150_00L, afterDelete.assetSnapshot.currentAssetsInCents)
    }

    @Test
    fun depositSavingsGoal_andAddBorrowingRecord_syncWithMainLedgerTransactions() {
        val fake = FakeLifeDataSource()
        val vm = LifeSharedViewModel(fake)

        val goal = vm.createSavingsGoal("新电脑", 5000_00L)
        val deposited = vm.depositSavingsGoal(goal.id, 800_00L, fake.fixedNowMillis)
        assertEquals(800_00L, deposited.savedCents)
        assertEquals(1, vm.snapshot.value.savingsGoals.size)
        assertEquals(800_00L, vm.snapshot.value.currentMonthTotals.incomeInCents)

        vm.addBorrowingRecord(
            direction = BorrowingDirection.LEND,
            amountCents = 200_00L,
            occurredAtMillis = fake.fixedNowMillis,
            person = "小林",
            note = "聚餐垫付",
            sourceTxId = null
        )
        val snap = vm.snapshot.value
        assertEquals(1, snap.borrowingRecords.size)
        assertEquals(2, snap.transactions.size)
        assertEquals(200_00L, snap.currentMonthTotals.expenseInCents)
    }

    @Test
    fun ledgerLifecycle_checkIn_andClearAllUserData_keepSnapshotConsistent() {
        val fake = FakeLifeDataSource()
        val vm = LifeSharedViewModel(fake)
        val defaultLedgerId = vm.snapshot.value.currentLedger.id

        val travel = vm.createAndSwitchLedger("旅行账本")
        assertEquals(travel.id, vm.snapshot.value.currentLedger.id)
        vm.saveStartingAssets(300_00L, 0L)
        vm.addBorrowingRecord(BorrowingDirection.BORROW, 50_00L, fake.fixedNowMillis, "阿泽", "", "tx-1")
        assertEquals(1, vm.snapshot.value.borrowingRecords.size)

        vm.deleteLedger(travel.id)
        assertEquals(defaultLedgerId, vm.snapshot.value.currentLedger.id)
        assertEquals(1, vm.snapshot.value.ledgers.size)
        assertEquals(0, vm.snapshot.value.borrowingRecords.size)

        assertTrue(vm.checkInToday("2026-11-24"))
        assertFalse(vm.checkInToday("2026-11-24"))
        vm.updateNickname("芽叶测试员")
        assertEquals("芽叶测试员", vm.snapshot.value.nickname)

        vm.clearAllUserData()
        val cleared = vm.snapshot.value
        assertEquals(AndroidLifeDataSource.DEFAULT_NICKNAME, cleared.nickname)
        assertTrue(cleared.checkedDates.isEmpty())
        assertEquals(1, fake.avatarClearedCount)
    }
}

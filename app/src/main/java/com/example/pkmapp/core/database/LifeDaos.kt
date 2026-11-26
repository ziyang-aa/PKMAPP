package com.example.pkmapp.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledgers WHERE ownerId = :ownerId AND deletedAt = 0 AND sortOrder >= 0 ORDER BY sortOrder ASC")
    fun getOrderedLedgers(ownerId: String = AccountScope.GUEST_OWNER_ID): List<LedgerEntity>

    @Query("SELECT * FROM ledgers WHERE ownerId = :ownerId AND deletedAt = 0 AND sortOrder >= 0 ORDER BY sortOrder ASC")
    fun observeLedgers(ownerId: String = AccountScope.GUEST_OWNER_ID): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM ledgers WHERE ownerId = :ownerId")
    fun getAllRowsIncludingPlaceholders(ownerId: String = AccountScope.GUEST_OWNER_ID): List<LedgerEntity>

    @Query("SELECT * FROM ledgers WHERE ownerId = :ownerId AND id = :ledgerId AND deletedAt = 0 LIMIT 1")
    fun getById(ledgerId: String, ownerId: String = AccountScope.GUEST_OWNER_ID): LedgerEntity?

    @Query("SELECT * FROM ledgers WHERE ownerId = :ownerId AND syncStatus = :syncStatus")
    fun getPendingLedgers(ownerId: String, syncStatus: Int = SyncStatus.PENDING_PUSH): List<LedgerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<LedgerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: LedgerEntity)

    @Query("DELETE FROM ledgers WHERE ownerId = :ownerId")
    fun deleteAllLedgers(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("DELETE FROM ledgers WHERE ownerId = :ownerId AND id = :ledgerId")
    fun deleteById(ledgerId: String, ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("UPDATE ledgers SET initialAssetsCents = 0, liabilitiesCents = 0, updatedAt = :now, syncStatus = 1 WHERE ownerId = :ownerId AND deletedAt = 0")
    fun clearAllAssetValues(ownerId: String = AccountScope.GUEST_OWNER_ID, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY sortOrder ASC")
    fun getAllTransactions(ownerId: String = AccountScope.GUEST_OWNER_ID): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY occurredAt DESC, id DESC")
    fun observeAllTransactions(ownerId: String = AccountScope.GUEST_OWNER_ID): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND syncStatus = :syncStatus")
    fun getPendingTransactions(ownerId: String, syncStatus: Int = SyncStatus.PENDING_PUSH): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId")
    fun getAllTransactionRowsIncludingDeleted(ownerId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransactions(entities: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE ownerId = :ownerId")
    fun deleteAllTransactions(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("SELECT COUNT(*) FROM ledgers WHERE ownerId = :ownerId AND deletedAt = 0 AND sortOrder >= 0")
    fun countActiveLedgers(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE ownerId = :ownerId AND deletedAt = 0")
    fun countActiveTransactions(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM ledgers WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPendingLedgers(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPendingTransactions(ownerId: String): Int

    @Query("UPDATE ledgers SET syncStatus = 0 WHERE ownerId = :ownerId AND id IN (:ids)")
    fun markLedgersSynced(ownerId: String, ids: List<String>)

    @Query("UPDATE transactions SET syncStatus = 0 WHERE ownerId = :ownerId AND id IN (:ids)")
    fun markTransactionsSynced(ownerId: String, ids: List<String>)

    @Transaction
    fun saveLedgerAndTransactionState(
        ledgerPairs: List<Pair<String, String>>,
        transactions: List<TransactionEntity>,
        currentLedgerId: String,
        ownerId: String = AccountScope.GUEST_OWNER_ID,
        monthlyBudgets: Map<String, Long> = emptyMap()
    ) {
        val now = System.currentTimeMillis()
        val existingLedgers = getAllRowsIncludingPlaceholders(ownerId).associateBy { it.id }
        val activeLedgerIds = ledgerPairs.map { it.first }.toHashSet()
        val updatedLedgers = ArrayList<LedgerEntity>(ledgerPairs.size + existingLedgers.size)

        ledgerPairs.forEachIndexed { index, (id, name) ->
            val old = existingLedgers[id]
            val budgetCents = (monthlyBudgets[id] ?: old?.monthlyBudgetCents ?: 0L).coerceAtLeast(0L)
            val changed = old == null ||
                old.name != name ||
                old.monthlyBudgetCents != budgetCents ||
                old.isCurrent != (id == currentLedgerId) ||
                old.isDefault != (index == 0) ||
                old.sortOrder != index ||
                old.deletedAt != 0L
            updatedLedgers.add(
                LedgerEntity(
                    id = id,
                    name = name,
                    initialAssetsCents = old?.initialAssetsCents ?: 0L,
                    liabilitiesCents = old?.liabilitiesCents ?: 0L,
                    createdAt = old?.createdAt ?: now,
                    isDefault = index == 0,
                    isCurrent = id == currentLedgerId,
                    sortOrder = index,
                    ownerId = ownerId,
                    updatedAt = if (changed) now else old!!.updatedAt,
                    deletedAt = 0L,
                    syncStatus = if (changed) SyncStatus.PENDING_PUSH else old!!.syncStatus,
                    clientRequestId = old?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                        ?: AccountScope.newRequestId(),
                    monthlyBudgetCents = budgetCents
                )
            )
        }
        for (old in existingLedgers.values) {
            if (!activeLedgerIds.contains(old.id) && old.sortOrder >= 0 && old.deletedAt == 0L) {
                updatedLedgers.add(
                    old.copy(
                        deletedAt = now,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_PUSH
                    )
                )
            }
        }
        deleteAllLedgers(ownerId)
        if (updatedLedgers.isNotEmpty()) {
            upsertAll(updatedLedgers)
        }

        val existingTx = getAllTransactionRowsIncludingDeleted(ownerId).associateBy { it.id }
        val activeTxIds = transactions.map { it.id }.toHashSet()
        val updatedTx = ArrayList<TransactionEntity>(transactions.size + existingTx.size)
        for (tx in transactions) {
            val old = existingTx[tx.id]
            val changed = old == null ||
                old.ledgerId != tx.ledgerId ||
                old.type != tx.type ||
                old.category != tx.category ||
                old.amountCents != tx.amountCents ||
                old.occurredAt != tx.occurredAt ||
                old.note != tx.note ||
                old.deletedAt != 0L
            updatedTx.add(
                tx.copy(
                    ownerId = ownerId,
                    createdAt = old?.createdAt ?: tx.createdAt,
                    updatedAt = if (changed) now else old!!.updatedAt,
                    deletedAt = 0L,
                    syncStatus = if (changed) SyncStatus.PENDING_PUSH else old!!.syncStatus,
                    clientRequestId = old?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                        ?: tx.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        for (old in existingTx.values) {
            if (!activeTxIds.contains(old.id) && old.deletedAt == 0L) {
                updatedTx.add(
                    old.copy(
                        deletedAt = now,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_PUSH
                    )
                )
            }
        }
        deleteAllTransactions(ownerId)
        if (updatedTx.isNotEmpty()) {
            insertTransactions(updatedTx)
        }
    }

    @Transaction
    fun saveStartingValues(
        ledgerId: String,
        initialAssetsCents: Long,
        liabilitiesCents: Long,
        ownerId: String = AccountScope.GUEST_OWNER_ID
    ) {
        val now = System.currentTimeMillis()
        val existing = getAllRowsIncludingPlaceholders(ownerId).firstOrNull { it.id == ledgerId }
        if (existing != null) {
            upsert(
                existing.copy(
                    initialAssetsCents = initialAssetsCents,
                    liabilitiesCents = liabilitiesCents,
                    deletedAt = 0L,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_PUSH,
                    clientRequestId = existing.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                )
            )
        } else {
            upsert(
                LedgerEntity(
                    id = ledgerId,
                    name = ledgerId,
                    initialAssetsCents = initialAssetsCents,
                    liabilitiesCents = liabilitiesCents,
                    createdAt = now,
                    isDefault = false,
                    isCurrent = false,
                    sortOrder = -1,
                    ownerId = ownerId,
                    updatedAt = now,
                    deletedAt = 0L,
                    syncStatus = SyncStatus.PENDING_PUSH,
                    clientRequestId = AccountScope.newRequestId()
                )
            )
        }
    }

    @Transaction
    fun addLiability(
        ledgerId: String,
        amountCents: Long,
        ownerId: String = AccountScope.GUEST_OWNER_ID
    ) {
        val existing = getById(ledgerId, ownerId)
        val next = (existing?.liabilitiesCents ?: 0L) + amountCents
        saveStartingValues(ledgerId, existing?.initialAssetsCents ?: 0L, next, ownerId)
    }

    @Transaction
    fun resetAssetValuesForLedger(
        ledgerId: String,
        ownerId: String = AccountScope.GUEST_OWNER_ID
    ) {
        val existing = getAllRowsIncludingPlaceholders(ownerId).firstOrNull { it.id == ledgerId } ?: return
        if (existing.sortOrder < 0) {
            deleteById(ledgerId, ownerId)
        } else {
            upsert(
                existing.copy(
                    initialAssetsCents = 0L,
                    liabilitiesCents = 0L,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING_PUSH
                )
            )
        }
    }
}

@Dao
interface BorrowingDao {
    @Query("SELECT * FROM borrowing_records WHERE ownerId = :ownerId AND deletedAt = 0")
    fun getAll(ownerId: String = AccountScope.GUEST_OWNER_ID): List<BorrowingRecordEntity>

    @Query("SELECT * FROM borrowing_records WHERE ownerId = :ownerId")
    fun getAllIncludingDeleted(ownerId: String): List<BorrowingRecordEntity>

    @Query("SELECT * FROM borrowing_records WHERE ownerId = :ownerId AND ledgerId = :ledgerId AND deletedAt = 0 ORDER BY occurredAt DESC")
    fun getForLedger(ledgerId: String, ownerId: String = AccountScope.GUEST_OWNER_ID): List<BorrowingRecordEntity>

    @Query("SELECT * FROM borrowing_records WHERE ownerId = :ownerId AND syncStatus = 1")
    fun getPending(ownerId: String): List<BorrowingRecordEntity>

    @Query("SELECT COUNT(*) FROM borrowing_records WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPending(ownerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: BorrowingRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<BorrowingRecordEntity>)

    @Query("UPDATE borrowing_records SET deletedAt = :now, updatedAt = :now, syncStatus = 1 WHERE ownerId = :ownerId AND ledgerId = :ledgerId AND deletedAt = 0")
    fun softDeleteForLedger(ledgerId: String, ownerId: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM borrowing_records WHERE ownerId = :ownerId")
    fun clear(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("UPDATE borrowing_records SET syncStatus = 0 WHERE ownerId = :ownerId AND id IN (:ids)")
    fun markSynced(ownerId: String, ids: List<String>)

    @Transaction
    fun deleteForLedger(ledgerId: String, ownerId: String = AccountScope.GUEST_OWNER_ID) {
        softDeleteForLedger(ledgerId, ownerId)
    }

    @Transaction
    fun replaceAll(entities: List<BorrowingRecordEntity>, ownerId: String = AccountScope.GUEST_OWNER_ID) {
        val now = System.currentTimeMillis()
        val existing = getAllIncludingDeleted(ownerId).associateBy { it.id }
        val activeIds = entities.map { it.id }.toHashSet()
        val updated = ArrayList<BorrowingRecordEntity>(entities.size + existing.size)
        for (entity in entities) {
            val old = existing[entity.id]
            updated.add(
                entity.copy(
                    ownerId = ownerId,
                    updatedAt = now,
                    deletedAt = 0L,
                    syncStatus = SyncStatus.PENDING_PUSH,
                    clientRequestId = old?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                        ?: entity.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        for (old in existing.values) {
            if (!activeIds.contains(old.id) && old.deletedAt == 0L) {
                updated.add(old.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING_PUSH))
            }
        }
        clear(ownerId)
        if (updated.isNotEmpty()) {
            insertAll(updated)
        }
    }
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY sortOrder ASC, createdAtMillis ASC")
    fun getAll(ownerId: String = AccountScope.GUEST_OWNER_ID): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE ownerId = :ownerId")
    fun getAllIncludingDeleted(ownerId: String): List<SavingsGoalEntity>

    @Query("SELECT COUNT(*) FROM savings_goals WHERE ownerId = :ownerId AND deletedAt = 0")
    fun count(ownerId: String = AccountScope.GUEST_OWNER_ID): Int

    @Query("SELECT * FROM savings_goals WHERE ownerId = :ownerId AND syncStatus = 1")
    fun getPending(ownerId: String): List<SavingsGoalEntity>

    @Query("SELECT COUNT(*) FROM savings_goals WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPending(ownerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<SavingsGoalEntity>)

    @Query("DELETE FROM savings_goals WHERE ownerId = :ownerId")
    fun clear(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("UPDATE savings_goals SET syncStatus = 0 WHERE ownerId = :ownerId AND id IN (:ids)")
    fun markSynced(ownerId: String, ids: List<String>)

    @Transaction
    fun replaceAll(entities: List<SavingsGoalEntity>, ownerId: String = AccountScope.GUEST_OWNER_ID) {
        val now = System.currentTimeMillis()
        val existing = getAllIncludingDeleted(ownerId).associateBy { it.id }
        val activeIds = entities.map { it.id }.toHashSet()
        val updated = ArrayList<SavingsGoalEntity>(entities.size + existing.size)
        for (entity in entities) {
            val old = existing[entity.id]
            val changed = old == null ||
                old.title != entity.title ||
                old.targetCents != entity.targetCents ||
                old.savedCents != entity.savedCents ||
                old.sortOrder != entity.sortOrder ||
                old.deletedAt != 0L
            updated.add(
                entity.copy(
                    ownerId = ownerId,
                    updatedAt = if (changed) now else old!!.updatedAt,
                    deletedAt = 0L,
                    syncStatus = if (changed) SyncStatus.PENDING_PUSH else old!!.syncStatus,
                    clientRequestId = old?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                        ?: entity.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        for (old in existing.values) {
            if (!activeIds.contains(old.id) && old.deletedAt == 0L) {
                updated.add(old.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING_PUSH))
            }
        }
        clear(ownerId)
        if (updated.isNotEmpty()) {
            insertAll(updated)
        }
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM custom_categories WHERE ownerId = :ownerId AND deletedAt = 0 AND type = :type ORDER BY sortOrder ASC, id ASC")
    fun getByType(type: String, ownerId: String = AccountScope.GUEST_OWNER_ID): List<CustomCategoryEntity>

    @Query("SELECT * FROM custom_categories WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY sortOrder ASC, id ASC")
    fun getAll(ownerId: String = AccountScope.GUEST_OWNER_ID): List<CustomCategoryEntity>

    @Query("SELECT * FROM custom_categories WHERE ownerId = :ownerId AND type = :type AND name = :name LIMIT 1")
    fun find(type: String, name: String, ownerId: String = AccountScope.GUEST_OWNER_ID): CustomCategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM custom_categories WHERE ownerId = :ownerId AND type = :type")
    fun maxSortOrder(type: String, ownerId: String = AccountScope.GUEST_OWNER_ID): Int

    @Query("SELECT * FROM custom_categories WHERE ownerId = :ownerId AND syncStatus = 1")
    fun getPending(ownerId: String): List<CustomCategoryEntity>

    @Query("SELECT COUNT(*) FROM custom_categories WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPending(ownerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: CustomCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<CustomCategoryEntity>)

    @Query("DELETE FROM custom_categories WHERE ownerId = :ownerId AND type = :type AND name = :name")
    fun deleteByTypeAndName(type: String, name: String, ownerId: String = AccountScope.GUEST_OWNER_ID): Int

    @Query("DELETE FROM custom_categories WHERE ownerId = :ownerId")
    fun clear(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("UPDATE custom_categories SET syncStatus = 0 WHERE ownerId = :ownerId AND id IN (:ids)")
    fun markSynced(ownerId: String, ids: List<Long>)
}

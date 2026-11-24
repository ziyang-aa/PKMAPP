package com.example.pkmapp.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledgers WHERE sortOrder >= 0 ORDER BY sortOrder ASC")
    fun getOrderedLedgers(): List<LedgerEntity>

    @Query("SELECT * FROM ledgers WHERE sortOrder >= 0 ORDER BY sortOrder ASC")
    fun observeLedgers(): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM ledgers")
    fun getAllRowsIncludingPlaceholders(): List<LedgerEntity>

    @Query("SELECT * FROM ledgers WHERE id = :ledgerId LIMIT 1")
    fun getById(ledgerId: String): LedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<LedgerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: LedgerEntity)

    @Query("DELETE FROM ledgers")
    fun deleteAllLedgers()

    @Query("DELETE FROM ledgers WHERE id = :ledgerId")
    fun deleteById(ledgerId: String)

    @Query("UPDATE ledgers SET initialAssetsCents = 0, liabilitiesCents = 0")
    fun clearAllAssetValues()

    @Query("SELECT * FROM transactions ORDER BY sortOrder ASC")
    fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransactions(entities: List<TransactionEntity>)

    @Query("DELETE FROM transactions")
    fun deleteAllTransactions()

    @Transaction
    fun saveLedgerAndTransactionState(
        ledgerPairs: List<Pair<String, String>>,
        transactions: List<TransactionEntity>,
        currentLedgerId: String
    ) {
        val existingById = getAllRowsIncludingPlaceholders().associateBy { it.id }
        val now = System.currentTimeMillis()
        val updatedLedgers = ledgerPairs.mapIndexed { index, (id, name) ->
            val old = existingById[id]
            LedgerEntity(
                id = id,
                name = name,
                initialAssetsCents = old?.initialAssetsCents ?: 0L,
                liabilitiesCents = old?.liabilitiesCents ?: 0L,
                createdAt = old?.createdAt ?: now,
                isDefault = index == 0,
                isCurrent = id == currentLedgerId,
                sortOrder = index
            )
        }
        deleteAllLedgers()
        if (updatedLedgers.isNotEmpty()) {
            upsertAll(updatedLedgers)
        }
        deleteAllTransactions()
        if (transactions.isNotEmpty()) {
            insertTransactions(transactions)
        }
    }

    @Transaction
    fun saveStartingValues(ledgerId: String, initialAssetsCents: Long, liabilitiesCents: Long) {
        val existing = getById(ledgerId)
        if (existing != null) {
            upsert(
                existing.copy(
                    initialAssetsCents = initialAssetsCents,
                    liabilitiesCents = liabilitiesCents
                )
            )
        } else {
            upsert(
                LedgerEntity(
                    id = ledgerId,
                    name = ledgerId,
                    initialAssetsCents = initialAssetsCents,
                    liabilitiesCents = liabilitiesCents,
                    createdAt = System.currentTimeMillis(),
                    isDefault = false,
                    isCurrent = false,
                    sortOrder = -1
                )
            )
        }
    }

    @Transaction
    fun addLiability(ledgerId: String, amountCents: Long) {
        val existing = getById(ledgerId)
        val next = (existing?.liabilitiesCents ?: 0L) + amountCents
        saveStartingValues(ledgerId, existing?.initialAssetsCents ?: 0L, next)
    }

    @Transaction
    fun resetAssetValuesForLedger(ledgerId: String) {
        val existing = getById(ledgerId) ?: return
        if (existing.sortOrder < 0) {
            deleteById(ledgerId)
        } else {
            upsert(existing.copy(initialAssetsCents = 0L, liabilitiesCents = 0L))
        }
    }
}

@Dao
interface BorrowingDao {
    @Query("SELECT * FROM borrowing_records")
    fun getAll(): List<BorrowingRecordEntity>

    @Query("SELECT * FROM borrowing_records WHERE ledgerId = :ledgerId ORDER BY occurredAt DESC")
    fun getForLedger(ledgerId: String): List<BorrowingRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: BorrowingRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<BorrowingRecordEntity>)

    @Query("DELETE FROM borrowing_records WHERE ledgerId = :ledgerId")
    fun deleteForLedger(ledgerId: String)

    @Query("DELETE FROM borrowing_records")
    fun clear()

    @Transaction
    fun replaceAll(entities: List<BorrowingRecordEntity>) {
        clear()
        if (entities.isNotEmpty()) {
            insertAll(entities)
        }
    }
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY sortOrder ASC, createdAtMillis ASC")
    fun getAll(): List<SavingsGoalEntity>

    @Query("SELECT COUNT(*) FROM savings_goals")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<SavingsGoalEntity>)

    @Query("DELETE FROM savings_goals")
    fun clear()

    @Transaction
    fun replaceAll(entities: List<SavingsGoalEntity>) {
        clear()
        if (entities.isNotEmpty()) {
            insertAll(entities)
        }
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM custom_categories WHERE type = :type ORDER BY sortOrder ASC, id ASC")
    fun getByType(type: String): List<CustomCategoryEntity>

    @Query("SELECT * FROM custom_categories ORDER BY sortOrder ASC, id ASC")
    fun getAll(): List<CustomCategoryEntity>

    @Query("SELECT * FROM custom_categories WHERE type = :type AND name = :name LIMIT 1")
    fun find(type: String, name: String): CustomCategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM custom_categories WHERE type = :type")
    fun maxSortOrder(type: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: CustomCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<CustomCategoryEntity>)

    @Query("DELETE FROM custom_categories WHERE type = :type AND name = :name")
    fun deleteByTypeAndName(type: String, name: String): Int

    @Query("DELETE FROM custom_categories")
    fun clear()
}

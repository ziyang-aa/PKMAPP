package com.example.pkmapp.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ledgers")
data class LedgerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val initialAssetsCents: Long,
    val liabilitiesCents: Long,
    val createdAt: Long,
    val isDefault: Boolean,
    val isCurrent: Boolean,
    val sortOrder: Int
)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["ledgerId", "occurredAt"])]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val type: String,
    val category: String,
    val amountCents: Long,
    val occurredAt: Long,
    val note: String,
    val createdAt: Long,
    val sortOrder: Int
)

@Entity(
    tableName = "borrowing_records",
    indices = [Index(value = ["ledgerId", "occurredAt"])]
)
data class BorrowingRecordEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val direction: String,
    val counterparty: String,
    val amountCents: Long,
    val occurredAt: Long,
    val note: String,
    val sourceTransactionId: String?,
    val settled: Boolean
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val targetCents: Long,
    val savedCents: Long,
    val deadline: String,
    val createdAtMillis: Long,
    val sortOrder: Int
)

@Entity(
    tableName = "custom_categories",
    indices = [Index(value = ["type", "name"], unique = true)]
)
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val name: String,
    val isDeletedDefault: Boolean,
    val sortOrder: Int
)

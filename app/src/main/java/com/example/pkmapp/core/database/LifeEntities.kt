package com.example.pkmapp.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ledgers",
    primaryKeys = ["ownerId", "id"],
    indices = [Index(value = ["ownerId", "sortOrder"])]
)
data class LedgerEntity(
    val id: String,
    val name: String,
    val initialAssetsCents: Long,
    val liabilitiesCents: Long,
    val createdAt: Long,
    val isDefault: Boolean,
    val isCurrent: Boolean,
    val sortOrder: Int,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val updatedAt: Long = 0L,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(
    tableName = "transactions",
    primaryKeys = ["ownerId", "id"],
    indices = [
        Index(value = ["ledgerId", "occurredAt"]),
        Index(value = ["ownerId", "syncStatus"])
    ]
)
data class TransactionEntity(
    val id: String,
    val ledgerId: String,
    val type: String,
    val category: String,
    val amountCents: Long,
    val occurredAt: Long,
    val note: String,
    val createdAt: Long,
    val sortOrder: Int,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val updatedAt: Long = 0L,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(
    tableName = "borrowing_records",
    primaryKeys = ["ownerId", "id"],
    indices = [
        Index(value = ["ledgerId", "occurredAt"]),
        Index(value = ["ownerId", "syncStatus"])
    ]
)
data class BorrowingRecordEntity(
    val id: String,
    val ledgerId: String,
    val direction: String,
    val counterparty: String,
    val amountCents: Long,
    val occurredAt: Long,
    val note: String,
    val sourceTransactionId: String?,
    val settled: Boolean,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val updatedAt: Long = 0L,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(
    tableName = "savings_goals",
    primaryKeys = ["ownerId", "id"],
    indices = [Index(value = ["ownerId", "sortOrder"])]
)
data class SavingsGoalEntity(
    val id: String,
    val title: String,
    val targetCents: Long,
    val savedCents: Long,
    val deadline: String,
    val createdAtMillis: Long,
    val sortOrder: Int,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val updatedAt: Long = 0L,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(
    tableName = "custom_categories",
    indices = [Index(value = ["ownerId", "type", "name"], unique = true)]
)
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val name: String,
    val isDeletedDefault: Boolean,
    val sortOrder: Int,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val updatedAt: Long = 0L,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

package com.example.pkmapp.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_accounts",
    indices = [Index(value = ["identifier"], unique = true)]
)
data class LocalAccountEntity(
    @PrimaryKey val ownerId: String,
    val identifier: String,
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val nickname: String = "森林守护员",
    val avatarPath: String = "",
    val checkedDatesJson: String = "[]",
    val lastSyncCursor: Long = 0L,
    val isGuest: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "feedback_records",
    indices = [
        Index(value = ["ownerId", "createdAt"]),
        Index(value = ["ownerId", "syncStatus"])
    ]
)
data class FeedbackRecordEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val category: String,
    val content: String,
    val contact: String = "",
    val appVersion: String = "4.0.0",
    val status: String = "local_saved",
    val reply: String = "",
    val clientRequestId: String = "",
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

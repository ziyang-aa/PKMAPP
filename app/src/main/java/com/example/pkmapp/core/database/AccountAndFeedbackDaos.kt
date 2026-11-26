package com.example.pkmapp.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalAccountDao {
    @Query("SELECT * FROM local_accounts ORDER BY isGuest DESC, createdAt ASC")
    fun getAll(): List<LocalAccountEntity>

    @Query("SELECT * FROM local_accounts WHERE ownerId = :ownerId LIMIT 1")
    fun findByOwnerId(ownerId: String): LocalAccountEntity?

    @Query("SELECT * FROM local_accounts WHERE identifier = :identifier LIMIT 1")
    fun findByIdentifier(identifier: String): LocalAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(account: LocalAccountEntity)

    @Query("UPDATE local_accounts SET nickname = :nickname, updatedAt = :updatedAt WHERE ownerId = :ownerId")
    fun updateNickname(ownerId: String, nickname: String, updatedAt: Long)

    @Query("UPDATE local_accounts SET checkedDatesJson = :checkedDatesJson, updatedAt = :updatedAt WHERE ownerId = :ownerId")
    fun updateCheckedDates(ownerId: String, checkedDatesJson: String, updatedAt: Long)

    @Query("UPDATE local_accounts SET lastSyncCursor = :cursor, updatedAt = :updatedAt WHERE ownerId = :ownerId")
    fun updateSyncCursor(ownerId: String, cursor: Long, updatedAt: Long)

    @Query("DELETE FROM local_accounts WHERE ownerId = :ownerId AND isGuest = 0")
    fun deleteNonGuestByOwnerId(ownerId: String)
}

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback_records WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun getByOwner(ownerId: String): List<FeedbackRecordEntity>

    @Query("SELECT * FROM feedback_records WHERE ownerId = :ownerId AND syncStatus = :syncStatus ORDER BY createdAt ASC")
    fun getPendingByOwner(ownerId: String, syncStatus: Int = SyncStatus.PENDING_PUSH): List<FeedbackRecordEntity>

    @Query("SELECT COUNT(*) FROM feedback_records WHERE ownerId = :ownerId AND syncStatus = :syncStatus")
    fun countPendingByOwner(ownerId: String, syncStatus: Int = SyncStatus.PENDING_PUSH): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: FeedbackRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<FeedbackRecordEntity>)

    @Query("UPDATE feedback_records SET syncStatus = :syncStatus, status = :status, updatedAt = :updatedAt WHERE ownerId = :ownerId AND id IN (:ids)")
    fun markSynced(ownerId: String, ids: List<String>, status: String, syncStatus: Int, updatedAt: Long)

    @Query("UPDATE feedback_records SET ownerId = :targetOwnerId, syncStatus = 1, updatedAt = :updatedAt WHERE ownerId = :sourceOwnerId")
    fun reassignOwner(sourceOwnerId: String, targetOwnerId: String, updatedAt: Long)

    @Query("DELETE FROM feedback_records WHERE ownerId = :ownerId")
    fun deleteByOwner(ownerId: String)
}

package com.example.pkmapp.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class StudyDatabaseSnapshot(
    val gardenState: StudyGardenStateEntity?,
    val plans: List<StudyPlanEntity>,
    val subjects: List<StudySubjectEntity>,
    val records: List<StudyRecordEntity>,
    val tasks: List<StudyTaskEntity>,
    val reviews: List<StudyReviewEntity>
)

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_garden_state WHERE ownerId = :ownerId LIMIT 1")
    fun getGardenState(ownerId: String = AccountScope.GUEST_OWNER_ID): StudyGardenStateEntity?

    @Query("SELECT * FROM study_plans WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY sortOrder ASC")
    fun getAllPlans(ownerId: String = AccountScope.GUEST_OWNER_ID): List<StudyPlanEntity>

    @Query("SELECT * FROM study_subjects WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY planId ASC, sortOrder ASC")
    fun getAllSubjects(ownerId: String = AccountScope.GUEST_OWNER_ID): List<StudySubjectEntity>

    @Query("SELECT * FROM study_records WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY sortOrder ASC")
    fun getAllRecords(ownerId: String = AccountScope.GUEST_OWNER_ID): List<StudyRecordEntity>

    @Query("SELECT * FROM study_tasks WHERE ownerId = :ownerId AND deletedAt = 0")
    fun getAllTasks(ownerId: String = AccountScope.GUEST_OWNER_ID): List<StudyTaskEntity>

    @Query("SELECT * FROM study_reviews WHERE ownerId = :ownerId AND deletedAt = 0")
    fun getAllReviews(ownerId: String = AccountScope.GUEST_OWNER_ID): List<StudyReviewEntity>

    @Query("SELECT * FROM study_plans WHERE ownerId = :ownerId")
    fun getAllPlansIncludingDeleted(ownerId: String): List<StudyPlanEntity>

    @Query("SELECT * FROM study_subjects WHERE ownerId = :ownerId")
    fun getAllSubjectsIncludingDeleted(ownerId: String): List<StudySubjectEntity>

    @Query("SELECT * FROM study_records WHERE ownerId = :ownerId")
    fun getAllRecordsIncludingDeleted(ownerId: String): List<StudyRecordEntity>

    @Query("SELECT * FROM study_tasks WHERE ownerId = :ownerId")
    fun getAllTasksIncludingDeleted(ownerId: String): List<StudyTaskEntity>

    @Query("SELECT * FROM study_reviews WHERE ownerId = :ownerId")
    fun getAllReviewsIncludingDeleted(ownerId: String): List<StudyReviewEntity>

    @Query("SELECT * FROM study_plans WHERE ownerId = :ownerId AND syncStatus = 1")
    fun getPendingPlans(ownerId: String): List<StudyPlanEntity>

    @Query("SELECT * FROM study_subjects WHERE ownerId = :ownerId AND syncStatus = 1")
    fun getPendingSubjects(ownerId: String): List<StudySubjectEntity>

    @Query("SELECT * FROM study_records WHERE ownerId = :ownerId AND syncStatus = 1")
    fun getPendingRecords(ownerId: String): List<StudyRecordEntity>

    @Query("SELECT * FROM study_tasks WHERE ownerId = :ownerId AND syncStatus = 1")
    fun getPendingTasks(ownerId: String): List<StudyTaskEntity>

    @Query("SELECT * FROM study_reviews WHERE ownerId = :ownerId AND syncStatus = 1")
    fun getPendingReviews(ownerId: String): List<StudyReviewEntity>

    @Query("SELECT * FROM study_garden_state WHERE ownerId = :ownerId AND syncStatus = 1 LIMIT 1")
    fun getPendingGardenState(ownerId: String): StudyGardenStateEntity?

    @Query("SELECT COUNT(*) FROM study_plans WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPendingPlans(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM study_subjects WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPendingSubjects(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM study_records WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPendingRecords(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM study_tasks WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPendingTasks(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM study_reviews WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPendingReviews(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM study_garden_state WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPendingGardenState(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM study_records WHERE ownerId = :ownerId AND deletedAt = 0")
    fun countActiveRecords(ownerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertGardenState(entity: StudyGardenStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlans(entities: List<StudyPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSubjects(entities: List<StudySubjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecords(entities: List<StudyRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTasks(entities: List<StudyTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReviews(entities: List<StudyReviewEntity>)

    @Query("DELETE FROM study_plans WHERE ownerId = :ownerId")
    fun deleteAllPlans(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("DELETE FROM study_subjects WHERE ownerId = :ownerId")
    fun deleteAllSubjects(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("DELETE FROM study_records WHERE ownerId = :ownerId")
    fun deleteAllRecords(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("DELETE FROM study_tasks WHERE ownerId = :ownerId")
    fun deleteAllTasks(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("DELETE FROM study_reviews WHERE ownerId = :ownerId")
    fun deleteAllReviews(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("DELETE FROM study_garden_state WHERE ownerId = :ownerId")
    fun deleteGardenState(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("UPDATE study_plans SET syncStatus = 0 WHERE ownerId = :ownerId")
    fun markAllPlansSynced(ownerId: String)

    @Query("UPDATE study_plans SET syncStatus = 0 WHERE ownerId = :ownerId AND id = :id AND updatedAt = :expectedUpdatedAt")
    fun markPlanSyncedIfUnchanged(ownerId: String, id: String, expectedUpdatedAt: Long)

    @Query("UPDATE study_subjects SET syncStatus = 0 WHERE ownerId = :ownerId AND planId = :planId AND id = :id AND updatedAt = :expectedUpdatedAt")
    fun markSubjectSyncedIfUnchanged(ownerId: String, planId: String, id: String, expectedUpdatedAt: Long)

    @Query("UPDATE study_records SET syncStatus = 0 WHERE ownerId = :ownerId")
    fun markAllRecordsSynced(ownerId: String)

    @Query("UPDATE study_records SET syncStatus = 0 WHERE ownerId = :ownerId AND id = :id AND updatedAt = :expectedUpdatedAt")
    fun markRecordSyncedIfUnchanged(ownerId: String, id: String, expectedUpdatedAt: Long)

    @Query("UPDATE study_tasks SET syncStatus = 0 WHERE ownerId = :ownerId")
    fun markAllTasksSynced(ownerId: String)

    @Query("UPDATE study_tasks SET syncStatus = 0 WHERE ownerId = :ownerId AND id = :id AND updatedAt = :expectedUpdatedAt")
    fun markTaskSyncedIfUnchanged(ownerId: String, id: String, expectedUpdatedAt: Long)

    @Query("UPDATE study_reviews SET syncStatus = 0 WHERE ownerId = :ownerId AND recordId = :recordId AND updatedAt = :expectedUpdatedAt")
    fun markReviewSyncedIfUnchanged(ownerId: String, recordId: String, expectedUpdatedAt: Long)

    @Query("UPDATE study_garden_state SET syncStatus = 0 WHERE ownerId = :ownerId AND updatedAt = :expectedUpdatedAt")
    fun markGardenStateSyncedIfUnchanged(ownerId: String, expectedUpdatedAt: Long)

    @Transaction
    fun loadSnapshot(ownerId: String = AccountScope.GUEST_OWNER_ID): StudyDatabaseSnapshot = StudyDatabaseSnapshot(
        gardenState = getGardenState(ownerId),
        plans = getAllPlans(ownerId),
        subjects = getAllSubjects(ownerId),
        records = getAllRecords(ownerId),
        tasks = getAllTasks(ownerId),
        reviews = getAllReviews(ownerId)
    )

    @Transaction
    fun replaceSnapshot(
        snapshot: StudyDatabaseSnapshot,
        ownerId: String = AccountScope.GUEST_OWNER_ID
    ) {
        val now = System.currentTimeMillis()
        val oldPlans = getAllPlansIncludingDeleted(ownerId).associateBy { it.id }
        val activePlanIds = snapshot.plans.map { it.id }.toHashSet()
        val nextPlans = ArrayList<StudyPlanEntity>(snapshot.plans.size + oldPlans.size)
        for (plan in snapshot.plans) {
            val prev = oldPlans[plan.id]
            nextPlans.add(
                plan.copy(
                    ownerId = ownerId,
                    updatedAt = now,
                    deletedAt = 0L,
                    syncStatus = SyncStatus.PENDING_PUSH,
                    clientRequestId = prev?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                        ?: plan.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        for (prev in oldPlans.values) {
            if (!activePlanIds.contains(prev.id) && prev.deletedAt == 0L) {
                nextPlans.add(prev.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING_PUSH))
            }
        }

        val oldRecords = getAllRecordsIncludingDeleted(ownerId).associateBy { it.id }
        val activeRecordIds = snapshot.records.map { it.id }.toHashSet()
        val nextRecords = ArrayList<StudyRecordEntity>(snapshot.records.size + oldRecords.size)
        for (rec in snapshot.records) {
            val prev = oldRecords[rec.id]
            nextRecords.add(
                rec.copy(
                    ownerId = ownerId,
                    updatedAt = now,
                    deletedAt = 0L,
                    syncStatus = SyncStatus.PENDING_PUSH,
                    clientRequestId = prev?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                        ?: rec.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        for (prev in oldRecords.values) {
            if (!activeRecordIds.contains(prev.id) && prev.deletedAt == 0L) {
                nextRecords.add(prev.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING_PUSH))
            }
        }

        val oldTasks = getAllTasksIncludingDeleted(ownerId).associateBy { it.id }
        val activeTaskIds = snapshot.tasks.map { it.id }.toHashSet()
        val nextTasks = ArrayList<StudyTaskEntity>(snapshot.tasks.size + oldTasks.size)
        for (task in snapshot.tasks) {
            val prev = oldTasks[task.id]
            nextTasks.add(
                task.copy(
                    ownerId = ownerId,
                    deletedAt = 0L,
                    syncStatus = SyncStatus.PENDING_PUSH,
                    clientRequestId = prev?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                        ?: task.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        for (prev in oldTasks.values) {
            if (!activeTaskIds.contains(prev.id) && prev.deletedAt == 0L) {
                nextTasks.add(prev.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING_PUSH))
            }
        }

        val oldReviews = getAllReviewsIncludingDeleted(ownerId).associateBy { it.recordId }
        val activeReviewIds = snapshot.reviews.map { it.recordId }.toHashSet()
        val nextReviews = ArrayList<StudyReviewEntity>(snapshot.reviews.size + oldReviews.size)
        for (rev in snapshot.reviews) {
            val prev = oldReviews[rev.recordId]
            nextReviews.add(
                rev.copy(
                    ownerId = ownerId,
                    deletedAt = 0L,
                    syncStatus = SyncStatus.PENDING_PUSH,
                    clientRequestId = prev?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                        ?: rev.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        for (prev in oldReviews.values) {
            if (!activeReviewIds.contains(prev.recordId) && prev.deletedAt == 0L) {
                nextReviews.add(prev.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING_PUSH))
            }
        }

        deleteAllReviews(ownerId)
        deleteAllTasks(ownerId)
        deleteAllRecords(ownerId)
        deleteAllSubjects(ownerId)
        deleteAllPlans(ownerId)
        deleteGardenState(ownerId)

        if (nextPlans.isNotEmpty()) {
            insertPlans(nextPlans)
        }
        if (snapshot.subjects.isNotEmpty()) {
            insertSubjects(
                snapshot.subjects.map {
                    it.copy(
                        ownerId = ownerId,
                        updatedAt = now,
                        deletedAt = 0L,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = it.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                    )
                }
            )
        }
        if (nextRecords.isNotEmpty()) {
            insertRecords(nextRecords)
        }
        if (nextTasks.isNotEmpty()) {
            insertTasks(nextTasks)
        }
        if (nextReviews.isNotEmpty()) {
            insertReviews(nextReviews)
        }
        if (snapshot.gardenState != null) {
            upsertGardenState(
                snapshot.gardenState.copy(
                    ownerId = ownerId,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_PUSH,
                    clientRequestId = snapshot.gardenState.clientRequestId.ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
    }

    @Transaction
    fun deleteAllForOwner(ownerId: String) {
        deleteAllReviews(ownerId)
        deleteAllTasks(ownerId)
        deleteAllRecords(ownerId)
        deleteAllSubjects(ownerId)
        deleteAllPlans(ownerId)
        deleteGardenState(ownerId)
    }
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAll(ownerId: String = AccountScope.GUEST_OWNER_ID): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE ownerId = :ownerId")
    fun getAllIncludingDeleted(ownerId: String): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE ownerId = :ownerId AND syncStatus = 1")
    fun getPending(ownerId: String): List<TodoItemEntity>

    @Query("SELECT COUNT(*) FROM todo_items WHERE ownerId = :ownerId AND syncStatus = 1")
    fun countPending(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM todo_items WHERE ownerId = :ownerId AND deletedAt = 0")
    fun countActive(ownerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<TodoItemEntity>)

    @Query("DELETE FROM todo_items WHERE ownerId = :ownerId")
    fun clear(ownerId: String = AccountScope.GUEST_OWNER_ID)

    @Query("UPDATE todo_items SET syncStatus = 0 WHERE ownerId = :ownerId AND id IN (:ids)")
    fun markSynced(ownerId: String, ids: List<String>)

    @Query("UPDATE todo_items SET syncStatus = 0 WHERE ownerId = :ownerId AND id = :id AND updatedAt = :expectedUpdatedAt")
    fun markSyncedIfUnchanged(ownerId: String, id: String, expectedUpdatedAt: Long)

    @Transaction
    fun replaceAll(entities: List<TodoItemEntity>, ownerId: String = AccountScope.GUEST_OWNER_ID) {
        val now = System.currentTimeMillis()
        val existing = getAllIncludingDeleted(ownerId).associateBy { it.id }
        val activeIds = entities.map { it.id }.toHashSet()
        val updated = ArrayList<TodoItemEntity>(entities.size + existing.size)
        for (entity in entities) {
            val old = existing[entity.id]
            val changed = old == null ||
                old.title != entity.title ||
                old.note != entity.note ||
                old.dueDate != entity.dueDate ||
                old.dueTime != entity.dueTime ||
                old.remind != entity.remind ||
                old.remindBeforeMinutes != entity.remindBeforeMinutes ||
                old.completedAt != entity.completedAt ||
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

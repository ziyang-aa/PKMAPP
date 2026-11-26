package com.example.pkmapp.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_plans",
    primaryKeys = ["ownerId", "id"],
    indices = [Index(value = ["ownerId", "sortOrder"])]
)
data class StudyPlanEntity(
    val id: String,
    val title: String,
    val kind: String,
    val due: String,
    val partner: String,
    val previousPlanId: String,
    val createdAt: Long,
    val archivedAt: Long,
    val selectedSubjectId: String,
    val sessionGoal: Int,
    val dailyGoal: Int,
    val remindDeadlines: Boolean,
    val remindTime: String,
    val isCurrent: Boolean,
    val sortOrder: Int,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val updatedAt: Long = 0L,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(
    tableName = "study_subjects",
    primaryKeys = ["ownerId", "planId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["ownerId", "id"],
            childColumns = ["ownerId", "planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ownerId", "planId"])]
)
data class StudySubjectEntity(
    val id: String,
    val planId: String,
    val title: String,
    val hint: String,
    val due: String,
    val assessment: String,
    val color: Int,
    val sortOrder: Int,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val updatedAt: Long = 0L,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(
    tableName = "study_records",
    primaryKeys = ["ownerId", "id"],
    indices = [
        Index(value = ["planId", "completedAt"]),
        Index(value = ["ownerId", "syncStatus"])
    ]
)
data class StudyRecordEntity(
    val id: String,
    val planId: String,
    val subjectId: String,
    val subjectTitleSnapshot: String,
    val subjectHintSnapshot: String,
    val subjectColorSnapshot: Int,
    val subjectDueSnapshot: String,
    val subjectAssessmentSnapshot: String,
    val durationMillis: Long,
    val completedAt: Long,
    val note: String,
    val manual: Boolean,
    val rewardMillis: Long,
    val editedAt: Long,
    val adjusted: Boolean,
    val taskId: String,
    val sortOrder: Int,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val updatedAt: Long = 0L,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(
    tableName = "study_tasks",
    primaryKeys = ["ownerId", "id"],
    indices = [
        Index(value = ["planId", "doneAt"]),
        Index(value = ["ownerId", "syncStatus"])
    ]
)
data class StudyTaskEntity(
    val id: String,
    val planId: String,
    val subjectId: String,
    val title: String,
    val due: String,
    val dueTime: String,
    val remind: Boolean,
    val remindLeadMinutes: Int,
    val createdAt: Long,
    val doneAt: Long,
    val updatedAt: Long,
    val isNext: Boolean,
    val priority: Long,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(
    tableName = "study_reviews",
    primaryKeys = ["ownerId", "recordId"],
    indices = [Index(value = ["ownerId", "syncStatus"])]
)
data class StudyReviewEntity(
    val recordId: String,
    val prompt: String,
    val day: String,
    val completedAt: Long,
    val updatedAt: Long,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(tableName = "study_garden_state")
data class StudyGardenStateEntity(
    @PrimaryKey val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val currentPlanId: String,
    val water: Int,
    val carryMillis: Long,
    val seeds: Int,
    val planted: Boolean,
    val growth: Int,
    val trees: Int,
    val rewardedDaysJson: String,
    val activeSessionId: String?,
    val activePlanId: String?,
    val activeSubjectJson: String?,
    val activeGoal: Int,
    val activeTaskId: String,
    val activeAccumulated: Long,
    val activeWall: Long,
    val activeElapsed: Long,
    val activeBoot: Int,
    val activeRunning: Boolean,
    val updatedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

@Entity(
    tableName = "todo_items",
    primaryKeys = ["ownerId", "id"],
    indices = [
        Index(value = ["completedAt", "dueDate"]),
        Index(value = ["ownerId", "syncStatus"])
    ]
)
data class TodoItemEntity(
    val id: String,
    val title: String,
    val note: String,
    val dueDate: String,
    val dueTime: String,
    val remind: Boolean,
    val remindBeforeMinutes: Int,
    val createdAt: Long,
    val completedAt: Long,
    val sortOrder: Int,
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val updatedAt: Long = 0L,
    val deletedAt: Long = 0L,
    val syncStatus: Int = SyncStatus.PENDING_PUSH,
    val clientRequestId: String = ""
)

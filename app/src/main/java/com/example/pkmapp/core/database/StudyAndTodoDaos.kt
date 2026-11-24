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
    @Query("SELECT * FROM study_garden_state WHERE singletonId = 1 LIMIT 1")
    fun getGardenState(): StudyGardenStateEntity?

    @Query("SELECT * FROM study_plans ORDER BY sortOrder ASC")
    fun getAllPlans(): List<StudyPlanEntity>

    @Query("SELECT * FROM study_subjects ORDER BY planId ASC, sortOrder ASC")
    fun getAllSubjects(): List<StudySubjectEntity>

    @Query("SELECT * FROM study_records ORDER BY sortOrder ASC")
    fun getAllRecords(): List<StudyRecordEntity>

    @Query("SELECT * FROM study_tasks")
    fun getAllTasks(): List<StudyTaskEntity>

    @Query("SELECT * FROM study_reviews")
    fun getAllReviews(): List<StudyReviewEntity>

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

    @Query("DELETE FROM study_plans")
    fun deleteAllPlans()

    @Query("DELETE FROM study_subjects")
    fun deleteAllSubjects()

    @Query("DELETE FROM study_records")
    fun deleteAllRecords()

    @Query("DELETE FROM study_tasks")
    fun deleteAllTasks()

    @Query("DELETE FROM study_reviews")
    fun deleteAllReviews()

    @Query("DELETE FROM study_garden_state")
    fun deleteGardenState()

    @Transaction
    fun loadSnapshot(): StudyDatabaseSnapshot = StudyDatabaseSnapshot(
        gardenState = getGardenState(),
        plans = getAllPlans(),
        subjects = getAllSubjects(),
        records = getAllRecords(),
        tasks = getAllTasks(),
        reviews = getAllReviews()
    )

    @Transaction
    fun replaceSnapshot(snapshot: StudyDatabaseSnapshot) {
        deleteAllReviews()
        deleteAllTasks()
        deleteAllRecords()
        deleteAllSubjects()
        deleteAllPlans()
        deleteGardenState()

        if (snapshot.plans.isNotEmpty()) {
            insertPlans(snapshot.plans)
        }
        if (snapshot.subjects.isNotEmpty()) {
            insertSubjects(snapshot.subjects)
        }
        if (snapshot.records.isNotEmpty()) {
            insertRecords(snapshot.records)
        }
        if (snapshot.tasks.isNotEmpty()) {
            insertTasks(snapshot.tasks)
        }
        if (snapshot.reviews.isNotEmpty()) {
            insertReviews(snapshot.reviews)
        }
        if (snapshot.gardenState != null) {
            upsertGardenState(snapshot.gardenState)
        }
    }
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items ORDER BY sortOrder ASC, createdAt ASC")
    fun getAll(): List<TodoItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<TodoItemEntity>)

    @Query("DELETE FROM todo_items")
    fun clear()

    @Transaction
    fun replaceAll(entities: List<TodoItemEntity>) {
        clear()
        if (entities.isNotEmpty()) {
            insertAll(entities)
        }
    }
}

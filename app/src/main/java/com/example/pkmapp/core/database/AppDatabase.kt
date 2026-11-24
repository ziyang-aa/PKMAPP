package com.example.pkmapp.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LedgerEntity::class,
        TransactionEntity::class,
        BorrowingRecordEntity::class,
        SavingsGoalEntity::class,
        CustomCategoryEntity::class,
        StudyPlanEntity::class,
        StudySubjectEntity::class,
        StudyRecordEntity::class,
        StudyTaskEntity::class,
        StudyReviewEntity::class,
        StudyGardenStateEntity::class,
        TodoItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun borrowingDao(): BorrowingDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun categoryDao(): CategoryDao
    abstract fun studyDao(): StudyDao
    abstract fun todoDao(): TodoDao

    companion object {
        const val DATABASE_NAME = "pkm_app.db"

        @Volatile
        private var instance: AppDatabase? = null

        @JvmStatic
        fun getRawInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }

        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            PkmStorageBootstrap.ensureInitialized(context)
            return getRawInstance(context)
        }
    }
}

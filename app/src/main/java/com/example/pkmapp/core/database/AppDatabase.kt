package com.example.pkmapp.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        TodoItemEntity::class,
        LocalAccountEntity::class,
        FeedbackRecordEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun borrowingDao(): BorrowingDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun categoryDao(): CategoryDao
    abstract fun studyDao(): StudyDao
    abstract fun todoDao(): TodoDao
    abstract fun localAccountDao(): LocalAccountDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        const val DATABASE_NAME = "pkm_app.db"

        @JvmField
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val guest = AccountScope.GUEST_OWNER_ID
                val now = System.currentTimeMillis()

                // 1. ledgers: composite PK (ownerId, id) + sync metadata
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ledgers_v2` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `initialAssetsCents` INTEGER NOT NULL,
                        `liabilitiesCents` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `isCurrent` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `ledgers_v2` (
                        `id`, `name`, `initialAssetsCents`, `liabilitiesCents`, `createdAt`,
                        `isDefault`, `isCurrent`, `sortOrder`, `ownerId`, `updatedAt`,
                        `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `name`, `initialAssetsCents`, `liabilitiesCents`, `createdAt`,
                           `isDefault`, `isCurrent`, `sortOrder`, '$guest', $now,
                           0, ${SyncStatus.PENDING_PUSH}, 'mig_l_' || `id`
                    FROM `ledgers`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `ledgers`")
                db.execSQL("ALTER TABLE `ledgers_v2` RENAME TO `ledgers`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledgers_ownerId_sortOrder` ON `ledgers` (`ownerId`, `sortOrder`)")

                // 2. transactions
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transactions_v2` (
                        `id` TEXT NOT NULL,
                        `ledgerId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `transactions_v2` (
                        `id`, `ledgerId`, `type`, `category`, `amountCents`, `occurredAt`,
                        `note`, `createdAt`, `sortOrder`, `ownerId`, `updatedAt`,
                        `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `ledgerId`, `type`, `category`, `amountCents`, `occurredAt`,
                           `note`, `createdAt`, `sortOrder`, '$guest', $now,
                           0, ${SyncStatus.PENDING_PUSH}, 'mig_tx_' || `id`
                    FROM `transactions`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_v2` RENAME TO `transactions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ledgerId_occurredAt` ON `transactions` (`ledgerId`, `occurredAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ownerId_syncStatus` ON `transactions` (`ownerId`, `syncStatus`)")

                // 3. borrowing_records
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `borrowing_records_v2` (
                        `id` TEXT NOT NULL,
                        `ledgerId` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `counterparty` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `sourceTransactionId` TEXT,
                        `settled` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `borrowing_records_v2` (
                        `id`, `ledgerId`, `direction`, `counterparty`, `amountCents`, `occurredAt`,
                        `note`, `sourceTransactionId`, `settled`, `ownerId`, `updatedAt`,
                        `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `ledgerId`, `direction`, `counterparty`, `amountCents`, `occurredAt`,
                           `note`, `sourceTransactionId`, `settled`, '$guest', $now,
                           0, ${SyncStatus.PENDING_PUSH}, 'mig_br_' || `id`
                    FROM `borrowing_records`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `borrowing_records`")
                db.execSQL("ALTER TABLE `borrowing_records_v2` RENAME TO `borrowing_records`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_borrowing_records_ledgerId_occurredAt` ON `borrowing_records` (`ledgerId`, `occurredAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_borrowing_records_ownerId_syncStatus` ON `borrowing_records` (`ownerId`, `syncStatus`)")

                // 4. savings_goals
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `savings_goals_v2` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `targetCents` INTEGER NOT NULL,
                        `savedCents` INTEGER NOT NULL,
                        `deadline` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `savings_goals_v2` (
                        `id`, `title`, `targetCents`, `savedCents`, `deadline`, `createdAtMillis`,
                        `sortOrder`, `ownerId`, `updatedAt`, `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `title`, `targetCents`, `savedCents`, `deadline`, `createdAtMillis`,
                           `sortOrder`, '$guest', $now, 0, ${SyncStatus.PENDING_PUSH}, 'mig_sg_' || `id`
                    FROM `savings_goals`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `savings_goals`")
                db.execSQL("ALTER TABLE `savings_goals_v2` RENAME TO `savings_goals`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_ownerId_sortOrder` ON `savings_goals` (`ownerId`, `sortOrder`)")

                // 5. custom_categories (unique index upgraded to ownerId + type + name)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_categories_v2` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isDeletedDefault` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `custom_categories_v2` (
                        `id`, `type`, `name`, `isDeletedDefault`, `sortOrder`,
                        `ownerId`, `updatedAt`, `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `type`, `name`, `isDeletedDefault`, `sortOrder`,
                           '$guest', $now, 0, ${SyncStatus.PENDING_PUSH}, 'mig_cat_' || `id`
                    FROM `custom_categories`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `custom_categories`")
                db.execSQL("ALTER TABLE `custom_categories_v2` RENAME TO `custom_categories`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_categories_ownerId_type_name` ON `custom_categories` (`ownerId`, `type`, `name`)")

                // 6. study_plans & study_subjects (must migrate subjects after plans because of FK)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_plans_v2` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `due` TEXT NOT NULL,
                        `partner` TEXT NOT NULL,
                        `previousPlanId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `archivedAt` INTEGER NOT NULL,
                        `selectedSubjectId` TEXT NOT NULL,
                        `sessionGoal` INTEGER NOT NULL,
                        `dailyGoal` INTEGER NOT NULL,
                        `remindDeadlines` INTEGER NOT NULL,
                        `remindTime` TEXT NOT NULL,
                        `isCurrent` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `study_plans_v2` (
                        `id`, `title`, `kind`, `due`, `partner`, `previousPlanId`, `createdAt`,
                        `archivedAt`, `selectedSubjectId`, `sessionGoal`, `dailyGoal`,
                        `remindDeadlines`, `remindTime`, `isCurrent`, `sortOrder`,
                        `ownerId`, `updatedAt`, `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `title`, `kind`, `due`, `partner`, `previousPlanId`, `createdAt`,
                           `archivedAt`, `selectedSubjectId`, `sessionGoal`, `dailyGoal`,
                           `remindDeadlines`, `remindTime`, `isCurrent`, `sortOrder`,
                           '$guest', $now, 0, ${SyncStatus.PENDING_PUSH}, 'mig_sp_' || `id`
                    FROM `study_plans`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_subjects_v2` (
                        `id` TEXT NOT NULL,
                        `planId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `hint` TEXT NOT NULL,
                        `due` TEXT NOT NULL,
                        `assessment` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `planId`, `id`),
                        FOREIGN KEY(`ownerId`, `planId`) REFERENCES `study_plans`(`ownerId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `study_subjects_v2` (
                        `id`, `planId`, `title`, `hint`, `due`, `assessment`, `color`, `sortOrder`,
                        `ownerId`, `updatedAt`, `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `planId`, `title`, `hint`, `due`, `assessment`, `color`, `sortOrder`,
                           '$guest', $now, 0, ${SyncStatus.PENDING_PUSH}, 'mig_sub_' || `planId` || '_' || `id`
                    FROM `study_subjects`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `study_subjects`")
                db.execSQL("DROP TABLE `study_plans`")
                db.execSQL("ALTER TABLE `study_plans_v2` RENAME TO `study_plans`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plans_ownerId_sortOrder` ON `study_plans` (`ownerId`, `sortOrder`)")
                db.execSQL("ALTER TABLE `study_subjects_v2` RENAME TO `study_subjects`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_subjects_ownerId_planId` ON `study_subjects` (`ownerId`, `planId`)")

                // 7. study_records
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_records_v2` (
                        `id` TEXT NOT NULL,
                        `planId` TEXT NOT NULL,
                        `subjectId` TEXT NOT NULL,
                        `subjectTitleSnapshot` TEXT NOT NULL,
                        `subjectHintSnapshot` TEXT NOT NULL,
                        `subjectColorSnapshot` INTEGER NOT NULL,
                        `subjectDueSnapshot` TEXT NOT NULL,
                        `subjectAssessmentSnapshot` TEXT NOT NULL,
                        `durationMillis` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `manual` INTEGER NOT NULL,
                        `rewardMillis` INTEGER NOT NULL,
                        `editedAt` INTEGER NOT NULL,
                        `adjusted` INTEGER NOT NULL,
                        `taskId` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `study_records_v2` (
                        `id`, `planId`, `subjectId`, `subjectTitleSnapshot`, `subjectHintSnapshot`,
                        `subjectColorSnapshot`, `subjectDueSnapshot`, `subjectAssessmentSnapshot`,
                        `durationMillis`, `completedAt`, `note`, `manual`, `rewardMillis`,
                        `editedAt`, `adjusted`, `taskId`, `sortOrder`,
                        `ownerId`, `updatedAt`, `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `planId`, `subjectId`, `subjectTitleSnapshot`, `subjectHintSnapshot`,
                           `subjectColorSnapshot`, `subjectDueSnapshot`, `subjectAssessmentSnapshot`,
                           `durationMillis`, `completedAt`, `note`, `manual`, `rewardMillis`,
                           `editedAt`, `adjusted`, `taskId`, `sortOrder`,
                           '$guest', $now, 0, ${SyncStatus.PENDING_PUSH}, 'mig_sr_' || `id`
                    FROM `study_records`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `study_records`")
                db.execSQL("ALTER TABLE `study_records_v2` RENAME TO `study_records`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_records_planId_completedAt` ON `study_records` (`planId`, `completedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_records_ownerId_syncStatus` ON `study_records` (`ownerId`, `syncStatus`)")

                // 8. study_tasks (already has updatedAt in v1)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_tasks_v2` (
                        `id` TEXT NOT NULL,
                        `planId` TEXT NOT NULL,
                        `subjectId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `due` TEXT NOT NULL,
                        `dueTime` TEXT NOT NULL,
                        `remind` INTEGER NOT NULL,
                        `remindLeadMinutes` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `doneAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `isNext` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `study_tasks_v2` (
                        `id`, `planId`, `subjectId`, `title`, `due`, `dueTime`, `remind`,
                        `remindLeadMinutes`, `createdAt`, `doneAt`, `updatedAt`, `isNext`, `priority`,
                        `ownerId`, `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `planId`, `subjectId`, `title`, `due`, `dueTime`, `remind`,
                           `remindLeadMinutes`, `createdAt`, `doneAt`, `updatedAt`, `isNext`, `priority`,
                           '$guest', 0, ${SyncStatus.PENDING_PUSH}, 'mig_st_' || `id`
                    FROM `study_tasks`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `study_tasks`")
                db.execSQL("ALTER TABLE `study_tasks_v2` RENAME TO `study_tasks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_tasks_planId_doneAt` ON `study_tasks` (`planId`, `doneAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_tasks_ownerId_syncStatus` ON `study_tasks` (`ownerId`, `syncStatus`)")

                // 9. study_reviews (already has updatedAt in v1)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_reviews_v2` (
                        `recordId` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `day` TEXT NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `recordId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `study_reviews_v2` (
                        `recordId`, `prompt`, `day`, `completedAt`, `updatedAt`,
                        `ownerId`, `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `recordId`, `prompt`, `day`, `completedAt`, `updatedAt`,
                           '$guest', 0, ${SyncStatus.PENDING_PUSH}, 'mig_srv_' || `recordId`
                    FROM `study_reviews`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `study_reviews`")
                db.execSQL("ALTER TABLE `study_reviews_v2` RENAME TO `study_reviews`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_reviews_ownerId_syncStatus` ON `study_reviews` (`ownerId`, `syncStatus`)")

                // 10. study_garden_state (singletonId=1 -> ownerId PK)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_garden_state_v2` (
                        `ownerId` TEXT NOT NULL,
                        `currentPlanId` TEXT NOT NULL,
                        `water` INTEGER NOT NULL,
                        `carryMillis` INTEGER NOT NULL,
                        `seeds` INTEGER NOT NULL,
                        `planted` INTEGER NOT NULL,
                        `growth` INTEGER NOT NULL,
                        `trees` INTEGER NOT NULL,
                        `rewardedDaysJson` TEXT NOT NULL,
                        `activeSessionId` TEXT,
                        `activePlanId` TEXT,
                        `activeSubjectJson` TEXT,
                        `activeGoal` INTEGER NOT NULL,
                        `activeTaskId` TEXT NOT NULL,
                        `activeAccumulated` INTEGER NOT NULL,
                        `activeWall` INTEGER NOT NULL,
                        `activeElapsed` INTEGER NOT NULL,
                        `activeBoot` INTEGER NOT NULL,
                        `activeRunning` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `study_garden_state_v2` (
                        `ownerId`, `currentPlanId`, `water`, `carryMillis`, `seeds`, `planted`,
                        `growth`, `trees`, `rewardedDaysJson`, `activeSessionId`, `activePlanId`,
                        `activeSubjectJson`, `activeGoal`, `activeTaskId`, `activeAccumulated`,
                        `activeWall`, `activeElapsed`, `activeBoot`, `activeRunning`,
                        `updatedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT '$guest', `currentPlanId`, `water`, `carryMillis`, `seeds`, `planted`,
                           `growth`, `trees`, `rewardedDaysJson`, `activeSessionId`, `activePlanId`,
                           `activeSubjectJson`, `activeGoal`, `activeTaskId`, `activeAccumulated`,
                           `activeWall`, `activeElapsed`, `activeBoot`, `activeRunning`,
                           $now, ${SyncStatus.PENDING_PUSH}, 'mig_garden_guest'
                    FROM `study_garden_state`
                    LIMIT 1
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `study_garden_state`")
                db.execSQL("ALTER TABLE `study_garden_state_v2` RENAME TO `study_garden_state`")

                // 11. todo_items
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `todo_items_v2` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `dueDate` TEXT NOT NULL,
                        `dueTime` TEXT NOT NULL,
                        `remind` INTEGER NOT NULL,
                        `remindBeforeMinutes` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        PRIMARY KEY(`ownerId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `todo_items_v2` (
                        `id`, `title`, `note`, `dueDate`, `dueTime`, `remind`,
                        `remindBeforeMinutes`, `createdAt`, `completedAt`, `sortOrder`,
                        `ownerId`, `updatedAt`, `deletedAt`, `syncStatus`, `clientRequestId`
                    )
                    SELECT `id`, `title`, `note`, `dueDate`, `dueTime`, `remind`,
                           `remindBeforeMinutes`, `createdAt`, `completedAt`, `sortOrder`,
                           '$guest', $now, 0, ${SyncStatus.PENDING_PUSH}, 'mig_todo_' || `id`
                    FROM `todo_items`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `todo_items`")
                db.execSQL("ALTER TABLE `todo_items_v2` RENAME TO `todo_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_completedAt_dueDate` ON `todo_items` (`completedAt`, `dueDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_ownerId_syncStatus` ON `todo_items` (`ownerId`, `syncStatus`)")

                // 12. local_accounts & seed default guest
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_accounts` (
                        `ownerId` TEXT NOT NULL,
                        `identifier` TEXT NOT NULL,
                        `passwordHash` TEXT NOT NULL,
                        `passwordSalt` TEXT NOT NULL,
                        `nickname` TEXT NOT NULL,
                        `avatarPath` TEXT NOT NULL,
                        `checkedDatesJson` TEXT NOT NULL,
                        `lastSyncCursor` INTEGER NOT NULL,
                        `isGuest` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ownerId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_local_accounts_identifier` ON `local_accounts` (`identifier`)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `local_accounts` (
                        `ownerId`, `identifier`, `passwordHash`, `passwordSalt`, `nickname`,
                        `avatarPath`, `checkedDatesJson`, `lastSyncCursor`, `isGuest`, `createdAt`, `updatedAt`
                    ) VALUES (
                        '$guest', '$guest', '', '', '森林守护员',
                        '', '[]', 0, 1, $now, $now
                    )
                    """.trimIndent()
                )

                // 13. feedback_records
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `feedback_records` (
                        `id` TEXT NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `contact` TEXT NOT NULL,
                        `appVersion` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `reply` TEXT NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        `syncStatus` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_feedback_records_ownerId_createdAt` ON `feedback_records` (`ownerId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_feedback_records_ownerId_syncStatus` ON `feedback_records` (`ownerId`, `syncStatus`)")
            }
        }

        @JvmField
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `ledgers` ADD COLUMN `monthlyBudgetCents` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

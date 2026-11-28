package com.example.pkmapp.core.database

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pkmapp.core.account.AuthRepository
import com.example.pkmapp.core.account.AuthResult
import com.example.pkmapp.core.datastore.AppPreferences
import com.example.pkmapp.core.feedback.FeedbackRepository
import com.example.pkmapp.core.security.CryptoSessionStore
import com.example.pkmapp.core.security.SessionKeyCipher
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.study.StudyNotebook
import com.example.pkmapp.study.StudyNotebookSqlBridge
import com.example.pkmapp.study.StudySubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationAndIsolationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun migrate1To2_preservesAllUserRecordsUnderGuestScope() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v1_v2.db"
        context.deleteDatabase(dbName)

        // Create real SQLite v1 schema matching app/schemas/.../1.json directly
        val factory = FrameworkSQLiteOpenHelperFactory()
        val v1Helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `ledgers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `initialAssetsCents` INTEGER NOT NULL, `liabilitiesCents` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `isCurrent` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `ledgerId` TEXT NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `occurredAt` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ledgerId_occurredAt` ON `transactions` (`ledgerId`, `occurredAt`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `borrowing_records` (`id` TEXT NOT NULL, `ledgerId` TEXT NOT NULL, `direction` TEXT NOT NULL, `counterparty` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `occurredAt` INTEGER NOT NULL, `note` TEXT NOT NULL, `sourceTransactionId` TEXT, `settled` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_borrowing_records_ledgerId_occurredAt` ON `borrowing_records` (`ledgerId`, `occurredAt`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `savings_goals` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `targetCents` INTEGER NOT NULL, `savedCents` INTEGER NOT NULL, `deadline` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `custom_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `isDeletedDefault` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_categories_type_name` ON `custom_categories` (`type`, `name`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `study_plans` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `due` TEXT NOT NULL, `partner` TEXT NOT NULL, `previousPlanId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `archivedAt` INTEGER NOT NULL, `selectedSubjectId` TEXT NOT NULL, `sessionGoal` INTEGER NOT NULL, `dailyGoal` INTEGER NOT NULL, `remindDeadlines` INTEGER NOT NULL, `remindTime` TEXT NOT NULL, `isCurrent` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `study_subjects` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `title` TEXT NOT NULL, `hint` TEXT NOT NULL, `due` TEXT NOT NULL, `assessment` TEXT NOT NULL, `color` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`planId`, `id`), FOREIGN KEY(`planId`) REFERENCES `study_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_subjects_planId` ON `study_subjects` (`planId`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `study_records` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `subjectTitleSnapshot` TEXT NOT NULL, `subjectHintSnapshot` TEXT NOT NULL, `subjectColorSnapshot` INTEGER NOT NULL, `subjectDueSnapshot` TEXT NOT NULL, `subjectAssessmentSnapshot` TEXT NOT NULL, `durationMillis` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `note` TEXT NOT NULL, `manual` INTEGER NOT NULL, `rewardMillis` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `adjusted` INTEGER NOT NULL, `taskId` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_records_planId_completedAt` ON `study_records` (`planId`, `completedAt`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `study_tasks` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `title` TEXT NOT NULL, `due` TEXT NOT NULL, `dueTime` TEXT NOT NULL, `remind` INTEGER NOT NULL, `remindLeadMinutes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `doneAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isNext` INTEGER NOT NULL, `priority` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_tasks_planId_doneAt` ON `study_tasks` (`planId`, `doneAt`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `study_reviews` (`recordId` TEXT NOT NULL, `prompt` TEXT NOT NULL, `day` TEXT NOT NULL, `completedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`recordId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `study_garden_state` (`singletonId` INTEGER NOT NULL, `currentPlanId` TEXT NOT NULL, `water` INTEGER NOT NULL, `carryMillis` INTEGER NOT NULL, `seeds` INTEGER NOT NULL, `planted` INTEGER NOT NULL, `growth` INTEGER NOT NULL, `trees` INTEGER NOT NULL, `rewardedDaysJson` TEXT NOT NULL, `activeSessionId` TEXT, `activePlanId` TEXT, `activeSubjectJson` TEXT, `activeGoal` INTEGER NOT NULL, `activeTaskId` TEXT NOT NULL, `activeAccumulated` INTEGER NOT NULL, `activeWall` INTEGER NOT NULL, `activeElapsed` INTEGER NOT NULL, `activeBoot` INTEGER NOT NULL, `activeRunning` INTEGER NOT NULL, PRIMARY KEY(`singletonId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `todo_items` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `note` TEXT NOT NULL, `dueDate` TEXT NOT NULL, `dueTime` TEXT NOT NULL, `remind` INTEGER NOT NULL, `remindBeforeMinutes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_completedAt_dueDate` ON `todo_items` (`completedAt`, `dueDate`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8438cb83e18930b49a077b4ae9e39840')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        v1Helper.writableDatabase.apply {
            execSQL("INSERT INTO ledgers VALUES ('l1', '日常账本', 100000, 20000, 1700000000000, 1, 1, 0)")
            execSQL("INSERT INTO transactions VALUES ('tx1', 'l1', 'EXPENSE', '餐饮', 3500, 1700001000000, '午饭', 1700001000000, 0)")
            execSQL("INSERT INTO borrowing_records VALUES ('br1', 'l1', 'LEND', '小林', 5000, 1700002000000, '奶茶', NULL, 0)")
            execSQL("INSERT INTO savings_goals VALUES ('sg1', '旅行基金', 500000, 120000, '', 1700000000000, 0)")
            execSQL("INSERT INTO custom_categories (type, name, isDeletedDefault, sortOrder) VALUES ('EXPENSE', '手冲咖啡', 0, 0)")
            execSQL("INSERT INTO study_plans VALUES ('legacy-408', '408 考研', 'GRADUATE', '', 'SQUIRREL', '', 0, 0, 'ds', 45, 180, 0, '18:00', 1, 0)")
            execSQL("INSERT INTO study_subjects VALUES ('ds', 'legacy-408', '数据结构', '', '', '', -123456, 0)")
            execSQL("INSERT INTO study_records VALUES ('sr1', 'legacy-408', 'ds', '数据结构', '', -123456, '', '', 1800000, 1700003000000, '刷题', 0, 1800000, 0, 0, '', 0)")
            execSQL("INSERT INTO study_tasks VALUES ('st1', 'legacy-408', 'ds', '链表习题', '2026-11-26', '18:00', 0, 30, 1700000000000, 0, 1700000000000, 1, 1)")
            execSQL("INSERT INTO study_reviews VALUES ('sr1', '复盘二叉树', '2026-11-26', 1700004000000, 1700004000000)")
            execSQL("INSERT INTO study_garden_state VALUES (1, 'legacy-408', 3, 600000, 1, 1, 2, 1, '[]', NULL, NULL, NULL, 25, '', 0, 0, 0, -1, 0)")
            execSQL("INSERT INTO todo_items VALUES ('td1', '交实验报告', '', '2026-11-26', '', 0, 30, 1700000000000, 0, 0)")
        }
        v1Helper.close()

        // Open with Room v4 + MIGRATION_1_2 + MIGRATION_2_3 + MIGRATION_3_4: runs lossless 1->2->3->4 migration!
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val guest = AccountScope.GUEST_OWNER_ID
            assertEquals(1, roomDb.ledgerDao().getOrderedLedgers(guest).size)
            assertEquals(100000L, roomDb.ledgerDao().getById("l1", guest)?.initialAssetsCents)
            assertEquals(0L, roomDb.ledgerDao().getById("l1", guest)?.monthlyBudgetCents)
            assertEquals(1, roomDb.ledgerDao().getAllTransactions(guest).size)
            assertEquals(1, roomDb.borrowingDao().getAll(guest).size)
            assertEquals(1, roomDb.savingsGoalDao().getAll(guest).size)
            assertEquals(1, roomDb.categoryDao().getByType(TransactionType.EXPENSE.name, guest).size)
            assertEquals(0, roomDb.recurringRuleDao().getAllActive(guest).size)
            val studySnap = roomDb.studyDao().loadSnapshot(guest)
            assertEquals(1, studySnap.plans.size)
            assertEquals(1, studySnap.subjects.size)
            assertEquals(1, studySnap.records.size)
            assertEquals(1, studySnap.tasks.size)
            assertEquals(1, studySnap.reviews.size)
            assertEquals(3, studySnap.gardenState?.water)
            assertEquals(1, roomDb.todoDao().getAll(guest).size)
            assertNotNull(roomDb.localAccountDao().findByOwnerId(guest))
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun authIsolationAndGuestMerge_keepsAccountsIsolatedAndPreservesGuestData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("isolated_auth.preferences_pb") }
        )
        val prefs = AppPreferences(dataStore, scope)
        val sessionStore = CryptoSessionStore(
            prefs,
            object : SessionKeyCipher {
                override fun encrypt(plaintext: String): String = "enc:$plaintext"
                override fun decrypt(ciphertext: String): String? = ciphertext.removePrefix("enc:")
            }
        )
        try {
            val authRepo = AuthRepository(context, database = db, preferences = prefs, sessionStore = sessionStore)
            authRepo.ensureGuestAccount()
            val guest = AccountScope.GUEST_OWNER_ID

            db.ledgerDao().saveLedgerAndTransactionState(
                ledgerPairs = listOf("guest_l1" to "游客日常"),
                transactions = listOf(
                    TransactionEntity(
                        id = "guest_tx_1",
                        ledgerId = "guest_l1",
                        type = TransactionType.EXPENSE.name,
                        category = "餐饮",
                        amountCents = 2400L,
                        occurredAt = 1700000000000L,
                        note = "游客午饭",
                        createdAt = 1700000000000L,
                        sortOrder = 0,
                        ownerId = guest
                    )
                ),
                currentLedgerId = "guest_l1",
                ownerId = guest
            )

            // Seed real study record so guest study state is non-empty and mergeable
            val guestNotebook = StudyNotebook()
            guestNotebook.select(StudySubject.DATA_STRUCTURES)
            guestNotebook.start(1700000000000L, 1000L, 1)
            guestNotebook.finish(1700001800000L, 1801000L, 1, "数据结构一轮")
            db.studyDao().replaceSnapshot(StudyNotebookSqlBridge.toSnapshot(guestNotebook, guest), guest)

            // Seed guest recurring rule to verify v4 guest merge & account isolation
            db.recurringRuleDao().upsert(
                RecurringRuleEntity(
                    id = "guest_rec_1",
                    ledgerId = "guest_l1",
                    title = "游客房租",
                    type = TransactionType.EXPENSE.name,
                    category = "居住",
                    amountCents = 200000L,
                    frequency = "MONTHLY",
                    dayOfMonth = 1,
                    dayOfWeek = 1,
                    monthOfYear = 1,
                    startDateKey = "2026-11-01",
                    lastGeneratedDateKey = "",
                    autoPost = true,
                    enabled = true,
                    note = "",
                    ownerId = guest,
                    updatedAt = 1700000000000L,
                    clientRequestId = "req_guest_rec_1"
                )
            )

            // Register Account A from guest -> detects guest merge candidate
            val regA = authRepo.register("13800138001", "Pass12345", "小芽A", reloadRuntimeStores = false)
            assertTrue(regA is AuthResult.Success)
            val successA = regA as AuthResult.Success
            assertNotNull(successA.guestMergeSummary)
            assertEquals(1, successA.guestMergeSummary!!.transactionCount)
            assertEquals(1, successA.guestMergeSummary!!.studyRecordCount)

            // Merge guest data into Account A
            authRepo.mergeGuestDataTo(successA.account.ownerId, reloadRuntimeStores = false)
            assertEquals(1, db.ledgerDao().getAllTransactions(successA.account.ownerId).size)
            assertEquals(0, db.ledgerDao().getAllTransactions(guest).size)
            assertEquals(1, db.recurringRuleDao().getAllActive(successA.account.ownerId).size)
            assertEquals(0, db.recurringRuleDao().getAllActive(guest).size)

            // Submit feedback under Account A
            val fbRepo = FeedbackRepository(context, database = db, sessionStore = sessionStore)
            val fbRes = fbRepo.submit(FeedbackRepository.Category.SUGGESTION, "希望增加深色纸张模式", "test@forest.dev")
            assertTrue(fbRes is FeedbackRepository.SubmitResult.Saved)

            // Register Account B with same default "legacy-408" study plan -> zero PK collision!
            val regB = authRepo.register("userb@forest.dev", "Pass12345", "松栗B", reloadRuntimeStores = false)
            assertTrue(regB is AuthResult.Success)
            val accB = (regB as AuthResult.Success).account
            db.studyDao().replaceSnapshot(StudyNotebookSqlBridge.toSnapshot(StudyNotebook(), accB.ownerId), accB.ownerId)

            assertEquals(1, db.studyDao().getAllPlans(successA.account.ownerId).size)
            assertEquals(1, db.studyDao().getAllPlans(accB.ownerId).size)
            assertEquals(0, db.ledgerDao().getAllTransactions(accB.ownerId).size)
            assertEquals(1, db.ledgerDao().getAllTransactions(successA.account.ownerId).size)
            assertEquals(1, db.feedbackDao().getByOwner(successA.account.ownerId).size)
            assertEquals(0, db.feedbackDao().getByOwner(accB.ownerId).size)
        } finally {
            scope.cancel()
            db.close()
        }
    }
}

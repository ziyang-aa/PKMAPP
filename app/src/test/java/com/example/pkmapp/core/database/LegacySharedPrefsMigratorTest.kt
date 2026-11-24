package com.example.pkmapp.core.database

import com.example.pkmapp.borrowing.BorrowingDirection
import com.example.pkmapp.borrowing.BorrowingRecord
import com.example.pkmapp.borrowing.BorrowingRecordCodec
import com.example.pkmapp.data.Ledger
import com.example.pkmapp.data.LedgerCodec
import com.example.pkmapp.data.Transaction
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.study.StudyNotebook
import com.example.pkmapp.study.StudyNotebookSqlBridge
import com.example.pkmapp.study.StudyTestFixtures
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacySharedPrefsMigratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeMigrationEnv(
        private val rawData: LegacyRawPreferencesData,
        private val backupDir: File,
        private val fixedNow: Long = 1_763_980_000_000L
    ) : LegacyMigrationEnvironment {
        var done = false
        var commitCount = 0
        var lastPayload: MigratedPayload? = null
        var recordedBeforePlans: String? = null
        var recordedUpgradedStudy: String? = null
        var recordedUnreadableStudy: String? = null

        override fun isMigrationDone(): Boolean = done

        override fun markMigrationDone() {
            done = true
        }

        override fun readLegacyPreferences(): LegacyRawPreferencesData = rawData

        override fun backupDirectory(): File = backupDir

        override fun onStudyLegacySideEffects(
            legacyBeforePlansJson: String?,
            upgradedNotebookJson: String?,
            unreadableRawJson: String?,
            nowMillis: Long
        ) {
            recordedBeforePlans = legacyBeforePlansJson
            recordedUpgradedStudy = upgradedNotebookJson
            recordedUnreadableStudy = unreadableRawJson
        }

        override fun commitPayloadAtomically(payload: MigratedPayload) {
            commitCount++
            lastPayload = payload
        }

        override fun nowMillis(): Long = fixedNow
    }

    @Test
    fun migrateIfNeeded_migratesAllTablesColdBacksUpAndIsIdempotent() {
        val lifeLedger = Ledger("ledger-life", "生活账本")
        val travelLedger = Ledger("ledger-travel", "北京旅行")
        val tx1 = Transaction("tx-1", "ledger-life", TransactionType.EXPENSE, 28_50L, "餐饮", "牛肉面", 1_763_800_000_000L)
        val tx2 = Transaction("tx-2", "ledger-travel", TransactionType.INCOME, 2000_00L, "生活费", "旅行预算", 1_763_850_000_000L)
        val ledgerJson = LedgerCodec.encode(
            listOf(lifeLedger, travelLedger),
            listOf(tx1, tx2),
            "ledger-travel"
        )

        val borrowingJson = BorrowingRecordCodec.encode(
            listOf(
                BorrowingRecord("br-1", "ledger-life", BorrowingDirection.LEND, 150_00L, 1_763_810_000_000L, "小林", "电影票", "tx-1"),
                BorrowingRecord("br-2", "ledger-travel", BorrowingDirection.BORROW, 80_00L, 1_763_820_000_000L, "阿木", "地铁卡", null)
            )
        )

        val savingsJson = JSONArray()
            .put(JSONObject().put("id", "default-autumn-trip").put("name", "旧演示").put("targetCents", 1000L).put("savedCents", 0L).put("createdAtMillis", 100L))
            .put(JSONObject().put("id", "goal-1").put("name", "降噪耳机").put("targetCents", 1899_00L).put("savedCents", 650_00L).put("createdAtMillis", 1_763_700_000_000L))
            .toString()

        val studyJson = StudyTestFixtures.buildRichNotebookJson()

        val todoJson = JSONObject()
            .put("remindEnabled", false)
            .put("defaultRemindTime", "20:15")
            .put(
                "items",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "todo-overdue")
                            .put("title", "交实验报告")
                            .put("note", "PDF 格式")
                            .put("day", "2026-11-20")
                            .put("dueTime", "22:00")
                            .put("remind", true)
                            .put("remindLead", 60)
                            .put("created", 1_763_600_000_000L)
                            .put("completed", 0L)
                    )
                    .put(
                        JSONObject()
                            .put("id", "todo-done")
                            .put("title", "买笔记本")
                            .put("note", "")
                            .put("day", "2026-11-22")
                            .put("dueTime", "")
                            .put("remind", false)
                            .put("remindLead", 30)
                            .put("created", 1_763_610_000_000L)
                            .put("completed", 1_763_620_000_000L)
                    )
            )
            .toString()

        val raw = LegacyRawPreferencesData(
            ledgerStateJson = ledgerJson,
            assetEntries = mapOf(
                "initial_assets_ledger-life" to 5000_00L,
                "liabilities_ledger-life" to 300_00L,
                "initial_assets_ledger-travel" to 1200_00L
            ),
            borrowingRecordsJson = borrowingJson,
            savingsGoalsJson = savingsJson,
            categoryEntries = mapOf(
                "custom_expense" to JSONArray().put("猫粮").put("胶片").toString(),
                "deleted_expense" to JSONArray().put("美妆理发").toString(),
                "custom_income" to JSONArray().put("稿费").toString()
            ),
            studyNotebookJson = studyJson,
            todoDataJson = todoJson,
            nickname = "松果守望者",
            checkedDates = setOf("2026-11-22", "2026-11-23"),
            rememberLastCottage = true,
            lastCottage = "STUDY",
            countdownMode = true,
            keepScreenOn = true,
            askedNotifications = true
        )

        val backupDir = File(tempFolder.root, LegacySharedPrefsMigrator.BACKUP_DIR_NAME)
        val env = FakeMigrationEnv(raw, backupDir)

        assertTrue(LegacySharedPrefsMigrator.migrateIfNeeded(env))
        assertTrue(env.done)
        assertEquals(1, env.commitCount)

        // Verify cold backup JSON exists and preserves original raw payloads
        val backupFile = File(backupDir, LegacySharedPrefsMigrator.BACKUP_FILE_NAME)
        assertTrue(backupFile.isFile)
        val backupRoot = JSONObject(backupFile.readText(Charsets.UTF_8))
        assertEquals(ledgerJson, backupRoot.getString("ledger_data_state"))
        assertEquals(studyJson, backupRoot.getString("forest_study_v1_notebook"))

        // Verify migrated payload parity across all 12 tables + DataStore
        val payload = env.lastPayload!!
        assertEquals(2, payload.ledgers.size)
        val lifeEntity = payload.ledgers.first { it.id == "ledger-life" }
        val travelEntity = payload.ledgers.first { it.id == "ledger-travel" }
        assertEquals(5000_00L, lifeEntity.initialAssetsCents)
        assertEquals(300_00L, lifeEntity.liabilitiesCents)
        assertFalse(lifeEntity.isCurrent)
        assertEquals(1200_00L, travelEntity.initialAssetsCents)
        assertTrue(travelEntity.isCurrent)

        assertEquals(2, payload.transactions.size)
        assertEquals(28_50L, payload.transactions[0].amountCents)
        assertEquals(2000_00L, payload.transactions[1].amountCents)

        assertEquals(2, payload.borrowingRecords.size)
        assertEquals("小林", payload.borrowingRecords[0].counterparty)
        assertEquals("tx-1", payload.borrowingRecords[0].sourceTransactionId)

        assertEquals(1, payload.savingsGoals.size)
        assertEquals("降噪耳机", payload.savingsGoals[0].title)
        assertEquals(1899_00L, payload.savingsGoals[0].targetCents)
        assertEquals(650_00L, payload.savingsGoals[0].savedCents)

        assertEquals(4, payload.customCategories.size)
        assertTrue(payload.customCategories.any { it.type == "EXPENSE" && it.name == "猫粮" && !it.isDeletedDefault })
        assertTrue(payload.customCategories.any { it.type == "EXPENSE" && it.name == "美妆理发" && it.isDeletedDefault })

        val studySnap = payload.studySnapshot
        assertNotNull(studySnap)
        assertEquals(2, studySnap!!.plans.size)
        assertEquals(14, studySnap.subjects.size)
        assertEquals(1, studySnap.records.size)
        assertEquals(1, studySnap.reviews.size)
        assertEquals(2, studySnap.tasks.size)
        assertTrue(studySnap.gardenState!!.activeRunning)
        val roundTripped = StudyNotebookSqlBridge.fromSnapshot(studySnap)!!
        assertEquals(studyJson, roundTripped.toJson())

        assertEquals(2, payload.todoItems.size)
        assertEquals("交实验报告", payload.todoItems[0].title)
        assertEquals(1_763_620_000_000L, payload.todoItems[1].completedAt)

        assertEquals("松果守望者", payload.preferences.nickname)
        assertEquals(setOf("2026-11-22", "2026-11-23"), payload.preferences.checkedDates)
        assertTrue(payload.preferences.rememberLastCottage)
        assertEquals("STUDY", payload.preferences.lastCottage)
        assertTrue(payload.preferences.countdownMode)
        assertTrue(payload.preferences.keepScreenOn)
        assertTrue(payload.preferences.askedNotifications)
        assertFalse(payload.preferences.todoRemindEnabled)
        assertEquals("20:15", payload.preferences.todoDefaultRemindTime)

        // Verify strict idempotency on subsequent invocations
        assertFalse(LegacySharedPrefsMigrator.migrateIfNeeded(env))
        assertEquals(1, env.commitCount)
    }

    @Test
    fun migrateIfNeeded_upgradesLegacyV2StudyFormatAndCapturesBeforePlansSnapshot() {
        val legacyV2 = JSONObject()
            .put("version", 2)
            .put("selected", "DATA_STRUCTURES")
            .put("sessionGoal", 45)
            .put("dailyGoal", 180)
            .put("water", 4)
            .put("seeds", 0)
            .put("trees", 2)
            .put("planted", true)
            .put("growth", 47)
            .put(
                "records",
                JSONArray().put(
                    JSONObject()
                        .put("id", "rec-v2")
                        .put("subject", "DATA_STRUCTURES")
                        .put("duration", 90_000L)
                        .put("completedAt", 1_763_800_000_000L)
                        .put("note", "旧版学习记录")
                )
            )
            .put("rewardedDays", JSONArray())
            .toString()

        val env = FakeMigrationEnv(
            rawData = LegacyRawPreferencesData(studyNotebookJson = legacyV2),
            backupDir = File(tempFolder.root, "backup_v2")
        )
        assertTrue(LegacySharedPrefsMigrator.migrateIfNeeded(env))
        assertEquals(legacyV2, env.recordedBeforePlans)
        assertNull(env.recordedUnreadableStudy)

        val restored = StudyNotebookSqlBridge.fromSnapshot(env.lastPayload!!.studySnapshot!!)!!
        assertEquals(47, restored.growth())
        assertEquals(2, restored.trees())
        assertEquals(1, restored.records().size)
        assertEquals("旧版学习记录", restored.records()[0].note)
        assertEquals(StudyNotebook.fromJson(legacyV2).toJson(), restored.toJson())
    }
}

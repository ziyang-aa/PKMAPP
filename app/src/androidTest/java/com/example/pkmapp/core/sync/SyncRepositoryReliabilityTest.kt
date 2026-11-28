package com.example.pkmapp.core.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pkmapp.core.database.AppDatabase
import com.example.pkmapp.core.database.FeedbackRecordEntity
import com.example.pkmapp.core.database.LedgerEntity
import com.example.pkmapp.core.database.LocalAccountEntity
import com.example.pkmapp.core.database.RecurringRuleEntity
import com.example.pkmapp.core.database.StudyGardenStateEntity
import com.example.pkmapp.core.database.SyncStatus
import com.example.pkmapp.core.database.TransactionEntity
import com.example.pkmapp.core.datastore.AppPreferences
import com.example.pkmapp.core.security.CryptoSessionStore
import com.example.pkmapp.core.security.SessionKeyCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncRepositoryReliabilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestEnv(
        val context: Context,
        val db: AppDatabase,
        val sessionStore: CryptoSessionStore,
        val scope: CoroutineScope,
        val ownerId: String = "u_sync_reliability"
    ) {
        fun close() {
            scope.cancel()
            db.close()
        }
    }

    private fun createEnv(name: String): TestEnv {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("$name.preferences_pb")
        }
        val prefs = AppPreferences(dataStore, scope)
        val plainCipher = object : SessionKeyCipher {
            override fun encrypt(plaintext: String): String = plaintext
            override fun decrypt(ciphertext: String): String = ciphertext
        }
        val sessionStore = CryptoSessionStore(prefs, plainCipher)
        val ownerId = "u_sync_reliability"
        db.localAccountDao().upsert(
            LocalAccountEntity(
                ownerId = ownerId,
                identifier = "13800138000",
                nickname = "云同步测试",
                lastSyncCursor = 0L,
                isGuest = false,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        sessionStore.saveSession(ownerId, "valid_jwt_token", "valid_refresh_token")
        sessionStore.saveServerBaseUrl("http://127.0.0.1:8080")
        return TestEnv(context, db, sessionStore, scope, ownerId)
    }

    @Test
    fun pushNetworkFailure_keepsDirtyStatusAndCursorUntouchedThenRetriesCleanly() {
        val env = createEnv("push_fail")
        try {
            env.db.ledgerDao().upsert(
                LedgerEntity(
                    id = "l1",
                    name = "日常账本",
                    initialAssetsCents = 0L,
                    liabilitiesCents = 0L,
                    createdAt = 1000L,
                    isDefault = true,
                    isCurrent = true,
                    sortOrder = 0,
                    ownerId = env.ownerId,
                    updatedAt = 1000L,
                    syncStatus = SyncStatus.PENDING_PUSH,
                    clientRequestId = "req_l1"
                )
            )
            env.db.ledgerDao().insertTransactions(
                listOf(
                    TransactionEntity(
                        id = "tx_1",
                        ledgerId = "l1",
                        type = "EXPENSE",
                        category = "餐饮",
                        amountCents = 2800L,
                        occurredAt = 1000L,
                        note = "午饭",
                        createdAt = 1000L,
                        sortOrder = 0,
                        ownerId = env.ownerId,
                        updatedAt = 1000L,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = "req_tx_1"
                    )
                )
            )

            var networkOnline = false
            val pushedPayloads = ArrayList<JSONObject>()
            val transport = object : SyncTransport {
                override fun pushSyncBatch(
                    baseUrl: String,
                    accessToken: String,
                    payload: JSONObject
                ): SyncApiClient.SyncPushResponse? {
                    if (!networkOnline) return null
                    pushedPayloads.add(payload)
                    return SyncApiClient.SyncPushResponse(ok = true, serverSeq = 10L)
                }

                override fun pullSyncDelta(baseUrl: String, accessToken: String, sinceCursor: Long): JSONObject? {
                    if (!networkOnline) return null
                    return JSONObject().put("nextCursor", 10L)
                }

                override fun submitFeedback(
                    baseUrl: String,
                    accessToken: String,
                    category: String,
                    content: String,
                    contact: String,
                    appVersion: String,
                    clientRequestId: String
                ): Boolean = networkOnline
            }

            val repo = SyncRepository(env.context, env.db, env.sessionStore, transport, reloadRuntimeStores = false)
            val failed = repo.syncNow()
            assertTrue(failed is SyncOutcome.Failed)
            assertEquals(2, repo.pendingCount(env.ownerId))
            assertEquals(0L, env.db.localAccountDao().findByOwnerId(env.ownerId)!!.lastSyncCursor)

            networkOnline = true
            val ok = repo.syncNow()
            assertTrue(ok is SyncOutcome.Synced)
            assertEquals(0, repo.pendingCount(env.ownerId))
            assertEquals(1, pushedPayloads.size)
            assertEquals(1, pushedPayloads[0].getJSONArray("transactions").length())
            assertEquals(10L, env.db.localAccountDao().findByOwnerId(env.ownerId)!!.lastSyncCursor)
        } finally {
            env.close()
        }
    }

    @Test
    fun pushSucceedsButPullFails_doesNotAdvanceCursorAndRetriesWithoutMissingOtherDeviceTx() {
        val env = createEnv("pull_fail")
        try {
            env.db.ledgerDao().insertTransactions(
                listOf(
                    TransactionEntity(
                        id = "tx_local",
                        ledgerId = "l1",
                        type = "EXPENSE",
                        category = "交通",
                        amountCents = 500L,
                        occurredAt = 1000L,
                        note = "地铁",
                        createdAt = 1000L,
                        sortOrder = 0,
                        ownerId = env.ownerId,
                        updatedAt = 1000L,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = "req_local"
                    )
                )
            )

            var allowPull = false
            val seenPullCursors = ArrayList<Long>()
            val transport = object : SyncTransport {
                override fun pushSyncBatch(
                    baseUrl: String,
                    accessToken: String,
                    payload: JSONObject
                ): SyncApiClient.SyncPushResponse = SyncApiClient.SyncPushResponse(ok = true, serverSeq = 20L)

                override fun pullSyncDelta(baseUrl: String, accessToken: String, sinceCursor: Long): JSONObject? {
                    seenPullCursors.add(sinceCursor)
                    if (!allowPull) return null
                    // Server returns another device's tx at seq=15 (which would have been skipped if cursor jumped to 20!)
                    return JSONObject()
                        .put("nextCursor", 20L)
                        .put(
                            "transactions",
                            JSONArray().put(
                                JSONObject()
                                    .put("id", "tx_other_device")
                                    .put("ledgerId", "l1")
                                    .put("type", "EXPENSE")
                                    .put("category", "餐饮")
                                    .put("amountCents", 4200L)
                                    .put("occurredAt", 1500L)
                                    .put("note", "另一台手机记的晚餐")
                                    .put("sortOrder", 1)
                                    .put("updatedAt", 1500L)
                                    .put("deletedAt", 0L)
                                    .put("clientRequestId", "req_other")
                            )
                        )
                }

                override fun submitFeedback(
                    baseUrl: String,
                    accessToken: String,
                    category: String,
                    content: String,
                    contact: String,
                    appVersion: String,
                    clientRequestId: String
                ): Boolean = true
            }

            val repo = SyncRepository(env.context, env.db, env.sessionStore, transport, reloadRuntimeStores = false)

            // Attempt 1: push succeeds, pull fails due to flaky network
            val first = repo.syncNow()
            assertTrue(first is SyncOutcome.Failed)
            assertEquals(0L, env.db.localAccountDao().findByOwnerId(env.ownerId)!!.lastSyncCursor)
            assertEquals(0, repo.pendingCount(env.ownerId))

            // Attempt 2: network recovers -> pulls from cursor 0 and recovers other device's tx!
            allowPull = true
            val second = repo.syncNow()
            assertTrue(second is SyncOutcome.Synced)
            assertEquals(listOf(0L, 0L), seenPullCursors)
            assertEquals(20L, env.db.localAccountDao().findByOwnerId(env.ownerId)!!.lastSyncCursor)
            val allTx = env.db.ledgerDao().getAllTransactions(env.ownerId).associateBy { it.id }
            assertEquals(2, allTx.size)
            assertEquals(4200L, allTx["tx_other_device"]!!.amountCents)
            assertEquals("另一台手机记的晚餐", allTx["tx_other_device"]!!.note)
        } finally {
            env.close()
        }
    }

    @Test
    fun concurrentLocalEditDuringPushRoundTrip_remainsPendingAndIsNeverLost() {
        val env = createEnv("concurrent_edit")
        try {
            val original = TransactionEntity(
                id = "tx_race",
                ledgerId = "l1",
                type = "EXPENSE",
                category = "餐饮",
                amountCents = 2800L,
                occurredAt = 1000L,
                note = "原始金额",
                createdAt = 1000L,
                sortOrder = 0,
                ownerId = env.ownerId,
                updatedAt = 1000L,
                syncStatus = SyncStatus.PENDING_PUSH,
                clientRequestId = "req_race_1"
            )
            env.db.ledgerDao().insertTransactions(listOf(original))

            val pushedAmounts = ArrayList<Long>()
            val transport = object : SyncTransport {
                override fun pushSyncBatch(
                    baseUrl: String,
                    accessToken: String,
                    payload: JSONObject
                ): SyncApiClient.SyncPushResponse {
                    val tx = payload.getJSONArray("transactions").getJSONObject(0)
                    val amount = tx.getLong("amountCents")
                    pushedAmounts.add(amount)
                    // Simulate user editing the SAME transaction locally while the first HTTP request is in flight!
                    if (pushedAmounts.size == 1) {
                        env.db.ledgerDao().insertTransactions(
                            listOf(
                                original.copy(
                                    amountCents = 3800L,
                                    note = "并发修改后的金额",
                                    updatedAt = 2000L,
                                    syncStatus = SyncStatus.PENDING_PUSH,
                                    clientRequestId = "req_race_2"
                                )
                            )
                        )
                    }
                    return SyncApiClient.SyncPushResponse(ok = true, serverSeq = pushedAmounts.size.toLong() * 10L)
                }

                override fun pullSyncDelta(baseUrl: String, accessToken: String, sinceCursor: Long): JSONObject {
                    // Server echoes back the older version (updatedAt=1000, amount=2800) on the first pull
                    return JSONObject()
                        .put("nextCursor", pushedAmounts.size.toLong() * 10L)
                        .put(
                            "transactions",
                            JSONArray().put(
                                JSONObject()
                                    .put("id", "tx_race")
                                    .put("ledgerId", "l1")
                                    .put("type", "EXPENSE")
                                    .put("category", "餐饮")
                                    .put("amountCents", pushedAmounts.last())
                                    .put("occurredAt", 1000L)
                                    .put("note", if (pushedAmounts.last() == 2800L) "原始金额" else "并发修改后的金额")
                                    .put("sortOrder", 0)
                                    .put("updatedAt", if (pushedAmounts.last() == 2800L) 1000L else 2000L)
                                    .put("deletedAt", 0L)
                                    .put("clientRequestId", "req_race")
                            )
                        )
                }

                override fun submitFeedback(
                    baseUrl: String,
                    accessToken: String,
                    category: String,
                    content: String,
                    contact: String,
                    appVersion: String,
                    clientRequestId: String
                ): Boolean = true
            }

            val repo = SyncRepository(env.context, env.db, env.sessionStore, transport, reloadRuntimeStores = false)

            // Sync 1: pushes 2800; local was edited to 3800 mid-flight -> must STAY PENDING_PUSH and KEEP 3800!
            assertTrue(repo.syncNow() is SyncOutcome.Synced)
            val afterFirst = env.db.ledgerDao().getAllTransactions(env.ownerId).single()
            assertEquals(3800L, afterFirst.amountCents)
            assertEquals("并发修改后的金额", afterFirst.note)
            assertEquals(SyncStatus.PENDING_PUSH, afterFirst.syncStatus)
            assertEquals(1, repo.pendingCount(env.ownerId))

            // Sync 2: automatically uploads the 3800 edit and clears pendingCount to 0!
            assertTrue(repo.syncNow() is SyncOutcome.Synced)
            assertEquals(listOf(2800L, 3800L), pushedAmounts)
            assertEquals(0, repo.pendingCount(env.ownerId))
            assertEquals(SyncStatus.SYNCED, env.db.ledgerDao().getAllTransactions(env.ownerId).single().syncStatus)
        } finally {
            env.close()
        }
    }

    @Test
    fun freshDeviceFullRestore_restoresAll13TablesAndPreservesActiveTimerSession() {
        val env = createEnv("full_restore")
        try {
            // Pre-seed an active local focus timer session to verify pull does NOT wipe running timer state
            env.db.studyDao().upsertGardenState(
                StudyGardenStateEntity(
                    ownerId = env.ownerId,
                    currentPlanId = "",
                    water = 0,
                    carryMillis = 0L,
                    seeds = 1,
                    planted = false,
                    growth = 0,
                    trees = 0,
                    rewardedDaysJson = "[]",
                    activeSessionId = "live_session_keep",
                    activePlanId = "plan_408",
                    activeSubjectJson = null,
                    activeGoal = 45,
                    activeTaskId = "task_1",
                    activeAccumulated = 60_000L,
                    activeWall = 123456L,
                    activeElapsed = 123456L,
                    activeBoot = 1,
                    activeRunning = true,
                    updatedAt = 500L,
                    syncStatus = SyncStatus.SYNCED
                )
            )

            val fullDelta = JSONObject()
                .put("nextCursor", 99L)
                .put(
                    "ledgers",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "l_main")
                            .put("name", "云端日常账本")
                            .put("initialAssetsCents", 100_000L)
                            .put("liabilitiesCents", 5_000L)
                            .put("monthlyBudgetCents", 300_000L)
                            .put("isDefault", true)
                            .put("isCurrent", true)
                            .put("sortOrder", 0)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "transactions",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "tx_cloud_1")
                            .put("ledgerId", "l_main")
                            .put("type", "EXPENSE")
                            .put("category", "餐饮")
                            .put("amountCents", 3600L)
                            .put("occurredAt", 1900L)
                            .put("note", "云端恢复的抹茶拿铁")
                            .put("sortOrder", 0)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "borrowings",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "br_1")
                            .put("ledgerId", "l_main")
                            .put("direction", "LEND")
                            .put("counterparty", "小林")
                            .put("amountCents", 12000L)
                            .put("occurredAt", 1800L)
                            .put("note", "电影票")
                            .put("sourceTransactionId", JSONObject.NULL)
                            .put("settled", true)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "savingsGoals",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "sg_1")
                            .put("title", "京都旅行基金")
                            .put("targetCents", 800_000L)
                            .put("savedCents", 250_000L)
                            .put("deadline", "2027-05-01")
                            .put("createdAtMillis", 1500L)
                            .put("sortOrder", 0)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "customCategories",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "EXPENSE")
                            .put("name", "手冲咖啡")
                            .put("isDeletedDefault", false)
                            .put("sortOrder", 3)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "studyPlans",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "plan_408")
                            .put("title", "408 冲刺计划")
                            .put("kind", "GRADUATE")
                            .put("due", "2026-12-20")
                            .put("partner", "SQUIRREL")
                            .put("previousPlanId", "plan_old")
                            .put("createdAt", 1000L)
                            .put("archivedAt", 0L)
                            .put("selectedSubjectId", "sub_ds")
                            .put("sessionGoal", 45)
                            .put("dailyGoal", 180)
                            .put("remindDeadlines", true)
                            .put("remindTime", "20:00")
                            .put("isCurrent", true)
                            .put("sortOrder", 0)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "studySubjects",
                    JSONArray().put(
                        JSONObject()
                            .put("planId", "plan_408")
                            .put("id", "sub_ds")
                            .put("title", "数据结构")
                            .put("hint", "二叉树遍历")
                            .put("due", "2026-12-10")
                            .put("assessment", "真题")
                            .put("color", -123456)
                            .put("sortOrder", 0)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "studyRecords",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "sr_1")
                            .put("planId", "plan_408")
                            .put("subjectId", "sub_ds")
                            .put("subjectTitleSnapshot", "数据结构")
                            .put("subjectHintSnapshot", "二叉树遍历")
                            .put("subjectColorSnapshot", -123456)
                            .put("subjectDueSnapshot", "2026-12-10")
                            .put("subjectAssessmentSnapshot", "真题")
                            .put("durationMillis", 2_700_000L)
                            .put("completedAt", 1950L)
                            .put("note", "完成树与图章节")
                            .put("manual", false)
                            .put("rewardMillis", 2_700_000L)
                            .put("editedAt", 0L)
                            .put("adjusted", false)
                            .put("taskId", "task_1")
                            .put("sortOrder", 0)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "studyTasks",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "task_1")
                            .put("planId", "plan_408")
                            .put("subjectId", "sub_ds")
                            .put("title", "二刷红黑树")
                            .put("due", "2026-12-01")
                            .put("dueTime", "19:30")
                            .put("remind", true)
                            .put("remindLeadMinutes", 15)
                            .put("createdAt", 1200L)
                            .put("doneAt", 0L)
                            .put("isNext", true)
                            .put("priority", 2L)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "studyReviews",
                    JSONArray().put(
                        JSONObject()
                            .put("recordId", "sr_1")
                            .put("prompt", "下次从红黑树旋转开始")
                            .put("day", "2026-11-27")
                            .put("completedAt", 1960L)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "studyGardenState",
                    JSONObject()
                        .put("currentPlanId", "plan_408")
                        .put("water", 7)
                        .put("carryMillis", 300_000L)
                        .put("seeds", 2)
                        .put("planted", true)
                        .put("growth", 3)
                        .put("trees", 4)
                        .put("rewardedDaysJson", "[\"2026-11-27\"]")
                        .put("updatedAt", 2000L)
                )
                .put(
                    "todos",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "td_1")
                            .put("title", "买考研准考证打印纸")
                            .put("note", "A4 80g")
                            .put("dueDate", "2026-11-28")
                            .put("dueTime", "14:00")
                            .put("remind", true)
                            .put("remindBeforeMinutes", 30)
                            .put("createdAt", 1300L)
                            .put("completedAt", 0L)
                            .put("sortOrder", 0)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )
                .put(
                    "recurringRules",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "rec_1")
                            .put("ledgerId", "l_main")
                            .put("title", "每月房租")
                            .put("type", "EXPENSE")
                            .put("category", "居住")
                            .put("amountCents", 250_000L)
                            .put("frequency", "MONTHLY")
                            .put("dayOfMonth", 1)
                            .put("dayOfWeek", 1)
                            .put("monthOfYear", 1)
                            .put("startDateKey", "2026-11-01")
                            .put("lastGeneratedDateKey", "2026-11-01")
                            .put("autoPost", true)
                            .put("enabled", true)
                            .put("note", "押一付一")
                            .put("sortOrder", 0)
                            .put("updatedAt", 2000L)
                            .put("deletedAt", 0L)
                    )
                )

            val transport = object : SyncTransport {
                override fun pushSyncBatch(
                    baseUrl: String,
                    accessToken: String,
                    payload: JSONObject
                ): SyncApiClient.SyncPushResponse = SyncApiClient.SyncPushResponse(ok = true, serverSeq = 99L)

                override fun pullSyncDelta(baseUrl: String, accessToken: String, sinceCursor: Long): JSONObject = fullDelta

                override fun submitFeedback(
                    baseUrl: String,
                    accessToken: String,
                    category: String,
                    content: String,
                    contact: String,
                    appVersion: String,
                    clientRequestId: String
                ): Boolean = true
            }

            val repo = SyncRepository(env.context, env.db, env.sessionStore, transport, reloadRuntimeStores = false)
            val outcome = repo.syncNow()
            assertTrue(outcome is SyncOutcome.Synced)
            assertEquals(99L, (outcome as SyncOutcome.Synced).serverCursor)
            assertEquals(0, repo.pendingCount(env.ownerId))

            assertEquals("云端日常账本", env.db.ledgerDao().getOrderedLedgers(env.ownerId).single().name)
            assertEquals(300_000L, env.db.ledgerDao().getOrderedLedgers(env.ownerId).single().monthlyBudgetCents)
            assertEquals("云端恢复的抹茶拿铁", env.db.ledgerDao().getAllTransactions(env.ownerId).single().note)
            assertTrue(env.db.borrowingDao().getAll(env.ownerId).single().settled)
            assertEquals("2027-05-01", env.db.savingsGoalDao().getAll(env.ownerId).single().deadline)
            assertEquals("手冲咖啡", env.db.categoryDao().getAll(env.ownerId).single().name)
            assertEquals("plan_old", env.db.studyDao().getAllPlans(env.ownerId).single().previousPlanId)
            assertEquals("数据结构", env.db.studyDao().getAllSubjects(env.ownerId).single().title)
            assertEquals(-123456, env.db.studyDao().getAllRecords(env.ownerId).single().subjectColorSnapshot)
            assertEquals("二刷红黑树", env.db.studyDao().getAllTasks(env.ownerId).single().title)
            assertEquals("下次从红黑树旋转开始", env.db.studyDao().getAllReviews(env.ownerId).single().prompt)
            val garden = env.db.studyDao().getGardenState(env.ownerId)!!
            assertEquals(4, garden.trees)
            assertEquals("live_session_keep", garden.activeSessionId)
            assertTrue(garden.activeRunning)
            assertEquals("买考研准考证打印纸", env.db.todoDao().getAll(env.ownerId).single().title)
            assertEquals("每月房租", env.db.recurringRuleDao().getAllActive(env.ownerId).single().title)
        } finally {
            env.close()
        }
    }

    @Test
    fun feedbackUploadPartialFailure_retriesOnlyFailedFeedback() {
        val env = createEnv("fb_partial")
        try {
            env.db.feedbackDao().upsertAll(
                listOf(
                    FeedbackRecordEntity(
                        id = "fb_1",
                        ownerId = env.ownerId,
                        category = "suggestion",
                        content = "第一条建议",
                        clientRequestId = "req_fb_1",
                        syncStatus = SyncStatus.PENDING_PUSH,
                        createdAt = 1000L,
                        updatedAt = 1000L
                    ),
                    FeedbackRecordEntity(
                        id = "fb_2",
                        ownerId = env.ownerId,
                        category = "bug",
                        content = "第二条反馈",
                        clientRequestId = "req_fb_2",
                        syncStatus = SyncStatus.PENDING_PUSH,
                        createdAt = 1100L,
                        updatedAt = 1100L
                    )
                )
            )

            var failSecond = true
            val transport = object : SyncTransport {
                override fun pushSyncBatch(
                    baseUrl: String,
                    accessToken: String,
                    payload: JSONObject
                ): SyncApiClient.SyncPushResponse = SyncApiClient.SyncPushResponse(ok = true, serverSeq = 1L)

                override fun pullSyncDelta(baseUrl: String, accessToken: String, sinceCursor: Long): JSONObject =
                    JSONObject().put("nextCursor", 1L)

                override fun submitFeedback(
                    baseUrl: String,
                    accessToken: String,
                    category: String,
                    content: String,
                    contact: String,
                    appVersion: String,
                    clientRequestId: String
                ): Boolean = !(failSecond && clientRequestId == "req_fb_2")
            }

            val repo = SyncRepository(env.context, env.db, env.sessionStore, transport, reloadRuntimeStores = false)
            val first = repo.syncNow()
            assertTrue(first is SyncOutcome.Failed)
            val remaining = env.db.feedbackDao().getPendingByOwner(env.ownerId)
            assertEquals(1, remaining.size)
            assertEquals("fb_2", remaining[0].id)

            failSecond = false
            val second = repo.syncNow()
            assertTrue(second is SyncOutcome.Synced)
            assertFalse(env.db.feedbackDao().getPendingByOwner(env.ownerId).isNotEmpty())
        } finally {
            env.close()
        }
    }

    @Test
    fun accessTokenExpired401_autoRefreshesRotatesTokensAndRetriesSuccessfully() {
        val env = createEnv("refresh_401_ok")
        try {
            env.db.ledgerDao().insertTransactions(
                listOf(
                    TransactionEntity(
                        id = "tx_401_ok",
                        ledgerId = "l1",
                        type = "EXPENSE",
                        category = "餐饮",
                        amountCents = 1990L,
                        occurredAt = 1000L,
                        note = "续期测试拿铁",
                        createdAt = 1000L,
                        sortOrder = 0,
                        ownerId = env.ownerId,
                        updatedAt = 1000L,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = "req_401_ok"
                    )
                )
            )
            env.sessionStore.saveSession(env.ownerId, "expired_access_token", "valid_refresh_token_1")

            var refreshCalls = 0
            val seenPushTokens = ArrayList<String>()
            val seenPullTokens = ArrayList<String>()

            val transport = object : SyncTransport {
                override fun pushSyncBatch(
                    baseUrl: String,
                    accessToken: String,
                    payload: JSONObject
                ): SyncApiClient.SyncPushResponse {
                    seenPushTokens.add(accessToken)
                    if (accessToken == "expired_access_token") {
                        return SyncApiClient.SyncPushResponse(
                            ok = false,
                            errorMessage = "登录状态已过期",
                            httpStatus = 401
                        )
                    }
                    return SyncApiClient.SyncPushResponse(ok = true, serverSeq = 55L, httpStatus = 200)
                }

                override fun pullSyncDelta(baseUrl: String, accessToken: String, sinceCursor: Long): JSONObject? = null

                override fun pullSyncDeltaWithStatus(
                    baseUrl: String,
                    accessToken: String,
                    sinceCursor: Long
                ): SyncApiClient.SyncPullResponse {
                    seenPullTokens.add(accessToken)
                    return if (accessToken == "fresh_access_token_2") {
                        SyncApiClient.SyncPullResponse(
                            ok = true,
                            data = JSONObject().put("nextCursor", 55L),
                            httpStatus = 200
                        )
                    } else {
                        SyncApiClient.SyncPullResponse(ok = false, errorMessage = "Unauthorized", httpStatus = 401)
                    }
                }

                override fun submitFeedback(
                    baseUrl: String,
                    accessToken: String,
                    category: String,
                    content: String,
                    contact: String,
                    appVersion: String,
                    clientRequestId: String
                ): Boolean = true

                override fun refreshSession(
                    baseUrl: String,
                    refreshToken: String
                ): SyncApiClient.AuthApiResponse {
                    refreshCalls++
                    assertEquals("valid_refresh_token_1", refreshToken)
                    return SyncApiClient.AuthApiResponse(
                        ok = true,
                        userId = env.ownerId,
                        nickname = "云同步测试",
                        accessToken = "fresh_access_token_2",
                        refreshToken = "rotated_refresh_token_2",
                        httpStatus = 200
                    )
                }
            }

            val repo = SyncRepository(env.context, env.db, env.sessionStore, transport, reloadRuntimeStores = false)
            val outcome = repo.syncNow()

            assertTrue(outcome is SyncOutcome.Synced)
            assertEquals(55L, (outcome as SyncOutcome.Synced).serverCursor)
            assertEquals(1, refreshCalls)
            assertEquals(listOf("expired_access_token", "fresh_access_token_2"), seenPushTokens)
            assertEquals(listOf("fresh_access_token_2"), seenPullTokens)
            assertEquals(0, repo.pendingCount(env.ownerId))
            val saved = env.sessionStore.currentSession()
            assertEquals("fresh_access_token_2", saved.accessToken)
            assertEquals("rotated_refresh_token_2", saved.refreshToken)
        } finally {
            env.close()
        }
    }

    @Test
    fun refreshTokenExpired401_preservesAllLocalPendingDataAndPromptsRelogin() {
        val env = createEnv("refresh_401_expired")
        try {
            env.db.ledgerDao().insertTransactions(
                listOf(
                    TransactionEntity(
                        id = "tx_401_keep",
                        ledgerId = "l1",
                        type = "EXPENSE",
                        category = "图书",
                        amountCents = 6800L,
                        occurredAt = 1200L,
                        note = "本地未同步保护记录",
                        createdAt = 1200L,
                        sortOrder = 0,
                        ownerId = env.ownerId,
                        updatedAt = 1200L,
                        syncStatus = SyncStatus.PENDING_PUSH,
                        clientRequestId = "req_401_keep"
                    )
                )
            )
            env.sessionStore.saveSession(env.ownerId, "expired_access_token", "revoked_refresh_token")

            var refreshCalls = 0
            val transport = object : SyncTransport {
                override fun pushSyncBatch(
                    baseUrl: String,
                    accessToken: String,
                    payload: JSONObject
                ): SyncApiClient.SyncPushResponse = SyncApiClient.SyncPushResponse(
                    ok = false,
                    errorMessage = "登录已过期",
                    httpStatus = 401
                )

                override fun pullSyncDelta(baseUrl: String, accessToken: String, sinceCursor: Long): JSONObject? = null

                override fun submitFeedback(
                    baseUrl: String,
                    accessToken: String,
                    category: String,
                    content: String,
                    contact: String,
                    appVersion: String,
                    clientRequestId: String
                ): Boolean = false

                override fun refreshSession(
                    baseUrl: String,
                    refreshToken: String
                ): SyncApiClient.AuthApiResponse {
                    refreshCalls++
                    return SyncApiClient.AuthApiResponse(
                        ok = false,
                        errorMessage = "登录已过期，请重新登录",
                        httpStatus = 401
                    )
                }
            }

            val repo = SyncRepository(env.context, env.db, env.sessionStore, transport, reloadRuntimeStores = false)
            val outcome = repo.syncNow()

            assertTrue(outcome is SyncOutcome.Failed)
            val failed = outcome as SyncOutcome.Failed
            assertEquals(1, refreshCalls)
            assertEquals(1, failed.pendingCount)
            assertTrue(failed.message.contains("重新登录"))
            assertEquals(1, repo.pendingCount(env.ownerId))
            val kept = env.db.ledgerDao().getAllTransactions(env.ownerId).single()
            assertEquals("tx_401_keep", kept.id)
            assertEquals(SyncStatus.PENDING_PUSH, kept.syncStatus)
            assertEquals(env.ownerId, env.sessionStore.currentSession().ownerId)
        } finally {
            env.close()
        }
    }
}

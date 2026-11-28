package com.example.pkmapp.core.sync

import android.content.Context
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.AppDatabase
import com.example.pkmapp.core.database.BorrowingRecordEntity
import com.example.pkmapp.core.database.CustomCategoryEntity
import com.example.pkmapp.core.database.LedgerEntity
import com.example.pkmapp.core.database.RecurringRuleEntity
import com.example.pkmapp.core.database.SavingsGoalEntity
import com.example.pkmapp.core.database.StudyGardenStateEntity
import com.example.pkmapp.core.database.StudyPlanEntity
import com.example.pkmapp.core.database.StudyRecordEntity
import com.example.pkmapp.core.database.StudyReviewEntity
import com.example.pkmapp.core.database.StudySubjectEntity
import com.example.pkmapp.core.database.StudyTaskEntity
import com.example.pkmapp.core.database.SyncStatus
import com.example.pkmapp.core.database.TodoItemEntity
import com.example.pkmapp.core.database.TransactionEntity
import com.example.pkmapp.core.security.CryptoSessionStore
import com.example.pkmapp.data.InMemoryLedgerRepository
import com.example.pkmapp.recurring.RecurringTransactionEngine
import com.example.pkmapp.study.StudyStore
import com.example.pkmapp.todo.TodoStore
import org.json.JSONArray
import org.json.JSONObject

sealed class SyncOutcome {
    data class Synced(val pushedCount: Int, val serverCursor: Long) : SyncOutcome()
    data class LocalOnly(val pendingCount: Int, val reason: String) : SyncOutcome()
    data class Failed(val pendingCount: Int, val message: String) : SyncOutcome()
}

interface SyncTransport {
    fun pushSyncBatch(
        baseUrl: String,
        accessToken: String,
        payload: JSONObject
    ): SyncApiClient.SyncPushResponse?

    fun pullSyncDelta(
        baseUrl: String,
        accessToken: String,
        sinceCursor: Long
    ): JSONObject?

    fun pullSyncDeltaWithStatus(
        baseUrl: String,
        accessToken: String,
        sinceCursor: Long
    ): SyncApiClient.SyncPullResponse? {
        val data = pullSyncDelta(baseUrl, accessToken, sinceCursor) ?: return null
        return SyncApiClient.SyncPullResponse(ok = true, data = data, httpStatus = 200)
    }

    fun submitFeedback(
        baseUrl: String,
        accessToken: String,
        category: String,
        content: String,
        contact: String,
        appVersion: String,
        clientRequestId: String
    ): Boolean

    fun submitFeedbackWithStatus(
        baseUrl: String,
        accessToken: String,
        category: String,
        content: String,
        contact: String,
        appVersion: String,
        clientRequestId: String
    ): SyncApiClient.FeedbackSubmitResponse? {
        val ok = submitFeedback(
            baseUrl = baseUrl,
            accessToken = accessToken,
            category = category,
            content = content,
            contact = contact,
            appVersion = appVersion,
            clientRequestId = clientRequestId
        )
        return SyncApiClient.FeedbackSubmitResponse(ok = ok, httpStatus = if (ok) 200 else 500)
    }

    fun refreshSession(
        baseUrl: String,
        refreshToken: String
    ): SyncApiClient.AuthApiResponse? = null
}

object DefaultSyncTransport : SyncTransport {
    override fun pushSyncBatch(
        baseUrl: String,
        accessToken: String,
        payload: JSONObject
    ): SyncApiClient.SyncPushResponse? = SyncApiClient.pushSyncBatch(baseUrl, accessToken, payload)

    override fun pullSyncDelta(
        baseUrl: String,
        accessToken: String,
        sinceCursor: Long
    ): JSONObject? = SyncApiClient.pullSyncDelta(baseUrl, accessToken, sinceCursor)

    override fun pullSyncDeltaWithStatus(
        baseUrl: String,
        accessToken: String,
        sinceCursor: Long
    ): SyncApiClient.SyncPullResponse? = SyncApiClient.pullSyncDeltaWithStatus(baseUrl, accessToken, sinceCursor)

    override fun submitFeedback(
        baseUrl: String,
        accessToken: String,
        category: String,
        content: String,
        contact: String,
        appVersion: String,
        clientRequestId: String
    ): Boolean = SyncApiClient.submitFeedback(
        baseUrl = baseUrl,
        accessToken = accessToken,
        category = category,
        content = content,
        contact = contact,
        appVersion = appVersion,
        clientRequestId = clientRequestId
    )

    override fun submitFeedbackWithStatus(
        baseUrl: String,
        accessToken: String,
        category: String,
        content: String,
        contact: String,
        appVersion: String,
        clientRequestId: String
    ): SyncApiClient.FeedbackSubmitResponse? = SyncApiClient.submitFeedbackWithStatus(
        baseUrl = baseUrl,
        accessToken = accessToken,
        category = category,
        content = content,
        contact = contact,
        appVersion = appVersion,
        clientRequestId = clientRequestId
    )

    override fun refreshSession(
        baseUrl: String,
        refreshToken: String
    ): SyncApiClient.AuthApiResponse? = SyncApiClient.refresh(baseUrl, refreshToken)
}

class SyncRepository @JvmOverloads constructor(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context.applicationContext),
    private val sessionStore: CryptoSessionStore = CryptoSessionStore.get(context.applicationContext),
    private val transport: SyncTransport = DefaultSyncTransport,
    private val reloadRuntimeStores: Boolean = true
) {
    private val appContext = context.applicationContext

    fun pendingCount(ownerId: String = AccountScope.currentOwnerId(appContext)): Int {
        return database.ledgerDao().countPendingLedgers(ownerId) +
            database.ledgerDao().countPendingTransactions(ownerId) +
            database.borrowingDao().countPending(ownerId) +
            database.savingsGoalDao().countPending(ownerId) +
            database.categoryDao().countPending(ownerId) +
            database.studyDao().countPendingPlans(ownerId) +
            database.studyDao().countPendingSubjects(ownerId) +
            database.studyDao().countPendingRecords(ownerId) +
            database.studyDao().countPendingTasks(ownerId) +
            database.studyDao().countPendingReviews(ownerId) +
            database.studyDao().countPendingGardenState(ownerId) +
            database.todoDao().countPending(ownerId) +
            database.feedbackDao().countPendingByOwner(ownerId) +
            database.recurringRuleDao().countPending(ownerId)
    }

    fun syncNow(): SyncOutcome = synchronized(SYNC_LOCK) {
        val session = sessionStore.currentSession()
        val initialPending = pendingCount(session.ownerId)
        if (session.isGuest) {
            return@synchronized SyncOutcome.LocalOnly(initialPending, "当前为本机游客模式，数据安全保存在本地 Room")
        }
        if (session.serverBaseUrl.isBlank()) {
            return@synchronized SyncOutcome.LocalOnly(initialPending, "未配置自建后端地址，已暂存 $initialPending 条待同步变更")
        }
        if (session.accessToken.isBlank() || session.accessToken.startsWith("offline_session_")) {
            return@synchronized SyncOutcome.LocalOnly(initialPending, "当前为离线登录会话，联网并重新登录后自动同步")
        }

        val ownerId = session.ownerId
        var activeAccessToken = session.accessToken
        var activeRefreshToken = session.refreshToken
        var refreshedOnce = false

        fun tryRefreshToken(): RefreshOutcome {
            if (refreshedOnce || activeRefreshToken.isBlank()) {
                return RefreshOutcome.Expired(SESSION_EXPIRED_MESSAGE)
            }
            refreshedOnce = true
            val refreshed = transport.refreshSession(session.serverBaseUrl, activeRefreshToken)
                ?: return RefreshOutcome.NetworkError
            if (refreshed.ok && refreshed.accessToken.isNotBlank()) {
                val nextRefresh = refreshed.refreshToken.ifBlank { activeRefreshToken }
                sessionStore.saveSession(ownerId, refreshed.accessToken, nextRefresh)
                activeAccessToken = refreshed.accessToken
                activeRefreshToken = nextRefresh
                return RefreshOutcome.Refreshed(activeAccessToken, activeRefreshToken)
            }
            if (refreshed.httpStatus >= 500) {
                return RefreshOutcome.NetworkError
            }
            return RefreshOutcome.Expired(SESSION_EXPIRED_MESSAGE)
        }

        val ledgerDao = database.ledgerDao()
        val borrowingDao = database.borrowingDao()
        val savingsDao = database.savingsGoalDao()
        val categoryDao = database.categoryDao()
        val studyDao = database.studyDao()
        val todoDao = database.todoDao()
        val feedbackDao = database.feedbackDao()
        val recurringDao = database.recurringRuleDao()

        val dirtyLedgers = ledgerDao.getPendingLedgers(ownerId)
        val dirtyTx = ledgerDao.getPendingTransactions(ownerId)
        val dirtyBorrowings = borrowingDao.getPending(ownerId)
        val dirtyGoals = savingsDao.getPending(ownerId)
        val dirtyCats = categoryDao.getPending(ownerId)
        val dirtyPlans = studyDao.getPendingPlans(ownerId)
        val dirtySubjects = studyDao.getPendingSubjects(ownerId)
        val dirtyRecords = studyDao.getPendingRecords(ownerId)
        val dirtyTasks = studyDao.getPendingTasks(ownerId)
        val dirtyReviews = studyDao.getPendingReviews(ownerId)
        val dirtyGarden = studyDao.getPendingGardenState(ownerId)
        val dirtyTodos = todoDao.getPending(ownerId)
        val dirtyRecurring = recurringDao.getPending(ownerId)
        val dirtyFeedbacks = feedbackDao.getPendingByOwner(ownerId)

        val batchCount = dirtyLedgers.size + dirtyTx.size + dirtyBorrowings.size +
            dirtyGoals.size + dirtyCats.size + dirtyPlans.size + dirtySubjects.size +
            dirtyRecords.size + dirtyTasks.size + dirtyReviews.size +
            (if (dirtyGarden != null) 1 else 0) + dirtyTodos.size + dirtyRecurring.size

        var pushedTotal = 0
        if (batchCount > 0) {
            val payload = buildPushPayload(
                dirtyLedgers = dirtyLedgers,
                dirtyTx = dirtyTx,
                dirtyBorrowings = dirtyBorrowings,
                dirtyGoals = dirtyGoals,
                dirtyCats = dirtyCats,
                dirtyPlans = dirtyPlans,
                dirtySubjects = dirtySubjects,
                dirtyRecords = dirtyRecords,
                dirtyTasks = dirtyTasks,
                dirtyReviews = dirtyReviews,
                dirtyGarden = dirtyGarden,
                dirtyTodos = dirtyTodos,
                dirtyRecurring = dirtyRecurring
            )
            var pushRes = transport.pushSyncBatch(session.serverBaseUrl, activeAccessToken, payload)
            if (pushRes?.unauthorized == true) {
                when (val refreshed = tryRefreshToken()) {
                    is RefreshOutcome.Refreshed -> {
                        pushRes = transport.pushSyncBatch(session.serverBaseUrl, activeAccessToken, payload)
                    }
                    is RefreshOutcome.NetworkError -> {
                        return@synchronized SyncOutcome.Failed(
                            pendingCount(ownerId),
                            "无法连接同步服务器，变更仍安全保存在本地"
                        )
                    }
                    is RefreshOutcome.Expired -> {
                        return@synchronized SyncOutcome.Failed(pendingCount(ownerId), refreshed.message)
                    }
                }
            }
            if (pushRes == null) {
                return@synchronized SyncOutcome.Failed(
                    pendingCount(ownerId),
                    "无法连接同步服务器，变更仍安全保存在本地"
                )
            }
            if (pushRes.unauthorized) {
                return@synchronized SyncOutcome.Failed(pendingCount(ownerId), SESSION_EXPIRED_MESSAGE)
            }
            if (!pushRes.ok) {
                return@synchronized SyncOutcome.Failed(
                    pendingCount(ownerId),
                    pushRes.errorMessage.ifEmpty { "云端同步失败" }
                )
            }

            // Mark synced ONLY when updatedAt matches the exact snapshot that was pushed,
            // so concurrent edits made during the HTTP round-trip remain PENDING_PUSH.
            // Crucially, do NOT advance lastSyncCursor here; let pull(since = initialCursor)
            // advance it atomically so changes pushed by other devices in between are never skipped.
            database.runInTransaction {
                dirtyLedgers.forEach { ledgerDao.markLedgerSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
                dirtyTx.forEach { ledgerDao.markTransactionSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
                dirtyBorrowings.forEach { borrowingDao.markSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
                dirtyGoals.forEach { savingsDao.markSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
                dirtyCats.forEach { categoryDao.markSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
                dirtyPlans.forEach { studyDao.markPlanSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
                dirtySubjects.forEach { studyDao.markSubjectSyncedIfUnchanged(ownerId, it.planId, it.id, it.updatedAt) }
                dirtyRecords.forEach { studyDao.markRecordSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
                dirtyTasks.forEach { studyDao.markTaskSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
                dirtyReviews.forEach { studyDao.markReviewSyncedIfUnchanged(ownerId, it.recordId, it.updatedAt) }
                if (dirtyGarden != null) {
                    studyDao.markGardenStateSyncedIfUnchanged(ownerId, dirtyGarden.updatedAt)
                }
                dirtyTodos.forEach { todoDao.markSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
                dirtyRecurring.forEach { recurringDao.markSyncedIfUnchanged(ownerId, it.id, it.updatedAt) }
            }
            pushedTotal += batchCount
        }

        var feedbackFailed = false
        for (fb in dirtyFeedbacks) {
            val reqId = fb.clientRequestId.ifEmpty { AccountScope.newRequestId() }
            var fbRes = transport.submitFeedbackWithStatus(
                baseUrl = session.serverBaseUrl,
                accessToken = activeAccessToken,
                category = fb.category,
                content = fb.content,
                contact = fb.contact,
                appVersion = fb.appVersion,
                clientRequestId = reqId
            )
            if (fbRes?.unauthorized == true) {
                when (val refreshed = tryRefreshToken()) {
                    is RefreshOutcome.Refreshed -> {
                        fbRes = transport.submitFeedbackWithStatus(
                            baseUrl = session.serverBaseUrl,
                            accessToken = activeAccessToken,
                            category = fb.category,
                            content = fb.content,
                            contact = fb.contact,
                            appVersion = fb.appVersion,
                            clientRequestId = reqId
                        )
                    }
                    is RefreshOutcome.NetworkError -> {
                        feedbackFailed = true
                        continue
                    }
                    is RefreshOutcome.Expired -> {
                        return@synchronized SyncOutcome.Failed(pendingCount(ownerId), refreshed.message)
                    }
                }
            }
            if (fbRes?.unauthorized == true) {
                return@synchronized SyncOutcome.Failed(pendingCount(ownerId), SESSION_EXPIRED_MESSAGE)
            }
            if (fbRes?.ok == true) {
                feedbackDao.markSyncedIfUnchanged(
                    ownerId = ownerId,
                    id = fb.id,
                    expectedUpdatedAt = fb.updatedAt,
                    status = "pending",
                    syncStatus = SyncStatus.SYNCED,
                    newUpdatedAt = System.currentTimeMillis()
                )
                pushedTotal++
            } else {
                feedbackFailed = true
            }
        }

        val initialCursor = database.localAccountDao().findByOwnerId(ownerId)?.lastSyncCursor ?: 0L
        var pullRes = transport.pullSyncDeltaWithStatus(session.serverBaseUrl, activeAccessToken, initialCursor)
        if (pullRes?.unauthorized == true) {
            when (val refreshed = tryRefreshToken()) {
                is RefreshOutcome.Refreshed -> {
                    pullRes = transport.pullSyncDeltaWithStatus(session.serverBaseUrl, activeAccessToken, initialCursor)
                }
                is RefreshOutcome.NetworkError -> {
                    return@synchronized SyncOutcome.Failed(
                        pendingCount(ownerId),
                        "拉取云端更新失败，稍后将自动重试"
                    )
                }
                is RefreshOutcome.Expired -> {
                    return@synchronized SyncOutcome.Failed(pendingCount(ownerId), refreshed.message)
                }
            }
        }
        if (pullRes == null) {
            return@synchronized SyncOutcome.Failed(
                pendingCount(ownerId),
                "拉取云端更新失败，稍后将自动重试"
            )
        }
        if (pullRes.unauthorized) {
            return@synchronized SyncOutcome.Failed(pendingCount(ownerId), SESSION_EXPIRED_MESSAGE)
        }
        val delta = if (pullRes.ok) pullRes.data else null
        if (delta == null) {
            return@synchronized SyncOutcome.Failed(
                pendingCount(ownerId),
                pullRes.errorMessage.ifEmpty { "拉取云端更新失败，稍后将自动重试" }
            )
        }

        var appliedLedgerOrTx = false
        var appliedStudy = false
        var appliedTodo = false
        var appliedRecurring = false
        var finalCursor = initialCursor

        database.runInTransaction {
            val ledgersChanged = applyPulledLedgersLww(ownerId, delta.optJSONArray("ledgers"))
            val txChanged = applyPulledTransactionsLww(ownerId, delta.optJSONArray("transactions"))
            applyPulledBorrowingsLww(ownerId, delta.optJSONArray("borrowings"))
            applyPulledSavingsGoalsLww(ownerId, delta.optJSONArray("savingsGoals"))
            applyPulledCustomCategoriesLww(ownerId, delta.optJSONArray("customCategories"))

            val plansChanged = applyPulledStudyPlansLww(ownerId, delta.optJSONArray("studyPlans"))
            val subjectsChanged = applyPulledStudySubjectsLww(ownerId, delta.optJSONArray("studySubjects"))
            val recordsChanged = applyPulledStudyRecordsLww(ownerId, delta.optJSONArray("studyRecords"))
            val tasksChanged = applyPulledStudyTasksLww(ownerId, delta.optJSONArray("studyTasks"))
            val reviewsChanged = applyPulledStudyReviewsLww(ownerId, delta.optJSONArray("studyReviews"))
            val gardenChanged = applyPulledStudyGardenStateLww(ownerId, delta.optJSONObject("studyGardenState"))
            val todosChanged = applyPulledTodosLww(ownerId, delta.optJSONArray("todos"))
            val recurringChanged = applyPulledRecurringRulesLww(ownerId, delta.optJSONArray("recurringRules"))

            appliedLedgerOrTx = ledgersChanged || txChanged
            appliedStudy = plansChanged || subjectsChanged || recordsChanged || tasksChanged || reviewsChanged || gardenChanged
            appliedTodo = todosChanged
            appliedRecurring = recurringChanged

            val nextCursor = maxOf(initialCursor, delta.optLong("nextCursor", initialCursor))
            if (nextCursor > initialCursor) {
                database.localAccountDao().updateSyncCursor(ownerId, nextCursor, System.currentTimeMillis())
            }
            finalCursor = nextCursor
        }

        if (reloadRuntimeStores && AccountScope.currentOwnerId(appContext) == ownerId) {
            if (appliedLedgerOrTx) {
                InMemoryLedgerRepository.getInstance().reloadFromPersistence()
            }
            if (appliedStudy) {
                StudyStore.get(appContext).reloadForCurrentAccount()
            }
            if (appliedTodo) {
                TodoStore.get(appContext).reloadForCurrentAccount()
            }
            if (appliedRecurring) {
                RecurringTransactionEngine.processDueRules(appContext)
            }
        }

        if (feedbackFailed) {
            return@synchronized SyncOutcome.Failed(
                pendingCount(ownerId),
                "账单已同步，部分意见反馈上传失败将自动重试"
            )
        }
        return@synchronized SyncOutcome.Synced(pushedTotal, finalCursor)
    }

    private fun buildPushPayload(
        dirtyLedgers: List<LedgerEntity>,
        dirtyTx: List<TransactionEntity>,
        dirtyBorrowings: List<BorrowingRecordEntity>,
        dirtyGoals: List<SavingsGoalEntity>,
        dirtyCats: List<CustomCategoryEntity>,
        dirtyPlans: List<StudyPlanEntity>,
        dirtySubjects: List<StudySubjectEntity>,
        dirtyRecords: List<StudyRecordEntity>,
        dirtyTasks: List<StudyTaskEntity>,
        dirtyReviews: List<StudyReviewEntity>,
        dirtyGarden: StudyGardenStateEntity?,
        dirtyTodos: List<TodoItemEntity>,
        dirtyRecurring: List<RecurringRuleEntity>
    ): JSONObject = JSONObject().apply {
        put("ledgers", JSONArray().apply {
            dirtyLedgers.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("initialAssetsCents", it.initialAssetsCents)
                        .put("liabilitiesCents", it.liabilitiesCents)
                        .put("monthlyBudgetCents", it.monthlyBudgetCents)
                        .put("isDefault", it.isDefault)
                        .put("isCurrent", it.isCurrent)
                        .put("sortOrder", it.sortOrder)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("transactions", JSONArray().apply {
            dirtyTx.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("ledgerId", it.ledgerId)
                        .put("type", it.type)
                        .put("category", it.category)
                        .put("amountCents", it.amountCents)
                        .put("occurredAt", it.occurredAt)
                        .put("note", it.note)
                        .put("sortOrder", it.sortOrder)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("borrowings", JSONArray().apply {
            dirtyBorrowings.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("ledgerId", it.ledgerId)
                        .put("direction", it.direction)
                        .put("counterparty", it.counterparty)
                        .put("amountCents", it.amountCents)
                        .put("occurredAt", it.occurredAt)
                        .put("note", it.note)
                        .put("sourceTransactionId", it.sourceTransactionId ?: JSONObject.NULL)
                        .put("settled", it.settled)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("savingsGoals", JSONArray().apply {
            dirtyGoals.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("targetCents", it.targetCents)
                        .put("savedCents", it.savedCents)
                        .put("deadline", it.deadline)
                        .put("createdAtMillis", it.createdAtMillis)
                        .put("sortOrder", it.sortOrder)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("customCategories", JSONArray().apply {
            dirtyCats.forEach {
                put(
                    JSONObject()
                        .put("type", it.type)
                        .put("name", it.name)
                        .put("isDeletedDefault", it.isDeletedDefault)
                        .put("sortOrder", it.sortOrder)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("studyPlans", JSONArray().apply {
            dirtyPlans.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("kind", it.kind)
                        .put("due", it.due)
                        .put("partner", it.partner)
                        .put("previousPlanId", it.previousPlanId)
                        .put("createdAt", it.createdAt)
                        .put("archivedAt", it.archivedAt)
                        .put("selectedSubjectId", it.selectedSubjectId)
                        .put("sessionGoal", it.sessionGoal)
                        .put("dailyGoal", it.dailyGoal)
                        .put("remindDeadlines", it.remindDeadlines)
                        .put("remindTime", it.remindTime)
                        .put("isCurrent", it.isCurrent)
                        .put("sortOrder", it.sortOrder)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("studySubjects", JSONArray().apply {
            dirtySubjects.forEach {
                put(
                    JSONObject()
                        .put("planId", it.planId)
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("hint", it.hint)
                        .put("due", it.due)
                        .put("assessment", it.assessment)
                        .put("color", it.color)
                        .put("sortOrder", it.sortOrder)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("studyRecords", JSONArray().apply {
            dirtyRecords.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("planId", it.planId)
                        .put("subjectId", it.subjectId)
                        .put("subjectTitleSnapshot", it.subjectTitleSnapshot)
                        .put("subjectHintSnapshot", it.subjectHintSnapshot)
                        .put("subjectColorSnapshot", it.subjectColorSnapshot)
                        .put("subjectDueSnapshot", it.subjectDueSnapshot)
                        .put("subjectAssessmentSnapshot", it.subjectAssessmentSnapshot)
                        .put("durationMillis", it.durationMillis)
                        .put("completedAt", it.completedAt)
                        .put("note", it.note)
                        .put("manual", it.manual)
                        .put("rewardMillis", it.rewardMillis)
                        .put("editedAt", it.editedAt)
                        .put("adjusted", it.adjusted)
                        .put("taskId", it.taskId)
                        .put("sortOrder", it.sortOrder)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("studyTasks", JSONArray().apply {
            dirtyTasks.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("planId", it.planId)
                        .put("subjectId", it.subjectId)
                        .put("title", it.title)
                        .put("due", it.due)
                        .put("dueTime", it.dueTime)
                        .put("remind", it.remind)
                        .put("remindLeadMinutes", it.remindLeadMinutes)
                        .put("createdAt", it.createdAt)
                        .put("doneAt", it.doneAt)
                        .put("isNext", it.isNext)
                        .put("priority", it.priority)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("studyReviews", JSONArray().apply {
            dirtyReviews.forEach {
                put(
                    JSONObject()
                        .put("recordId", it.recordId)
                        .put("prompt", it.prompt)
                        .put("day", it.day)
                        .put("completedAt", it.completedAt)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        if (dirtyGarden != null) {
            put(
                "studyGardenState",
                JSONObject()
                    .put("currentPlanId", dirtyGarden.currentPlanId)
                    .put("water", dirtyGarden.water)
                    .put("carryMillis", dirtyGarden.carryMillis)
                    .put("seeds", dirtyGarden.seeds)
                    .put("planted", dirtyGarden.planted)
                    .put("growth", dirtyGarden.growth)
                    .put("trees", dirtyGarden.trees)
                    .put("rewardedDaysJson", dirtyGarden.rewardedDaysJson)
                    .put("updatedAt", dirtyGarden.updatedAt)
            )
        }
        put("todos", JSONArray().apply {
            dirtyTodos.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("note", it.note)
                        .put("dueDate", it.dueDate)
                        .put("dueTime", it.dueTime)
                        .put("remind", it.remind)
                        .put("remindBeforeMinutes", it.remindBeforeMinutes)
                        .put("createdAt", it.createdAt)
                        .put("completedAt", it.completedAt)
                        .put("sortOrder", it.sortOrder)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
        put("recurringRules", JSONArray().apply {
            dirtyRecurring.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("ledgerId", it.ledgerId)
                        .put("title", it.title)
                        .put("type", it.type)
                        .put("category", it.category)
                        .put("amountCents", it.amountCents)
                        .put("frequency", it.frequency)
                        .put("dayOfMonth", it.dayOfMonth)
                        .put("dayOfWeek", it.dayOfWeek)
                        .put("monthOfYear", it.monthOfYear)
                        .put("startDateKey", it.startDateKey)
                        .put("lastGeneratedDateKey", it.lastGeneratedDateKey)
                        .put("autoPost", it.autoPost)
                        .put("enabled", it.enabled)
                        .put("note", it.note)
                        .put("sortOrder", it.sortOrder)
                        .put("updatedAt", it.updatedAt)
                        .put("deletedAt", it.deletedAt)
                        .put("clientRequestId", it.clientRequestId)
                )
            }
        })
    }

    private fun shouldKeepLocal(
        localUpdatedAt: Long,
        localSyncStatus: Int,
        remoteUpdatedAt: Long
    ): Boolean {
        if (localUpdatedAt > remoteUpdatedAt) return true
        if (localSyncStatus == SyncStatus.PENDING_PUSH && localUpdatedAt >= remoteUpdatedAt) return true
        return false
    }

    internal fun applyPulledLedgersLww(ownerId: String, ledgersArray: JSONArray?): Boolean {
        if (ledgersArray == null || ledgersArray.length() == 0) return false
        val ledgerDao = database.ledgerDao()
        val existingById = ledgerDao.getAllRowsIncludingPlaceholders(ownerId).associateBy { it.id }
        val toUpsert = ArrayList<LedgerEntity>()
        val now = System.currentTimeMillis()
        for (i in 0 until ledgersArray.length()) {
            val item = ledgersArray.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[id]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            val mergedName = item.optString("name", local?.name ?: "生活账本").ifBlank { local?.name ?: "生活账本" }
            val mergedBudget = if (item.has("monthlyBudgetCents")) {
                item.optLong("monthlyBudgetCents", 0L).coerceAtLeast(0L)
            } else {
                local?.monthlyBudgetCents ?: 0L
            }
            toUpsert.add(
                LedgerEntity(
                    id = id,
                    name = mergedName,
                    initialAssetsCents = item.optLong("initialAssetsCents", local?.initialAssetsCents ?: 0L).coerceAtLeast(0L),
                    liabilitiesCents = item.optLong("liabilitiesCents", local?.liabilitiesCents ?: 0L).coerceAtLeast(0L),
                    createdAt = local?.createdAt ?: remoteUpdatedAt.takeIf { it > 0L } ?: now,
                    isDefault = item.optBoolean("isDefault", local?.isDefault ?: false),
                    isCurrent = item.optBoolean("isCurrent", local?.isCurrent ?: false),
                    sortOrder = item.optInt("sortOrder", local?.sortOrder ?: i),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() },
                    monthlyBudgetCents = mergedBudget
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        ledgerDao.upsertAll(toUpsert)
        return true
    }

    internal fun applyPulledTransactionsLww(ownerId: String, txArray: JSONArray?): Boolean {
        if (txArray == null || txArray.length() == 0) return false
        val ledgerDao = database.ledgerDao()
        val existingById = ledgerDao.getAllTransactionRowsIncludingDeleted(ownerId).associateBy { it.id }
        val toUpsert = ArrayList<TransactionEntity>()
        val now = System.currentTimeMillis()
        for (i in 0 until txArray.length()) {
            val item = txArray.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[id]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            val occurredAt = item.optLong("occurredAt", local?.occurredAt ?: remoteUpdatedAt.takeIf { it > 0L } ?: now)
            toUpsert.add(
                TransactionEntity(
                    id = id,
                    ledgerId = item.optString("ledgerId", local?.ledgerId ?: "default").ifBlank { "default" },
                    type = item.optString("type", local?.type ?: "EXPENSE").ifBlank { "EXPENSE" },
                    category = item.optString("category", local?.category ?: "餐饮").ifBlank { "餐饮" },
                    amountCents = item.optLong("amountCents", local?.amountCents ?: 0L).coerceAtLeast(0L),
                    occurredAt = occurredAt,
                    note = item.optString("note", local?.note ?: ""),
                    createdAt = local?.createdAt ?: occurredAt,
                    sortOrder = item.optInt("sortOrder", local?.sortOrder ?: i),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        ledgerDao.insertTransactions(toUpsert)
        return true
    }

    internal fun applyPulledBorrowingsLww(ownerId: String, array: JSONArray?): Boolean {
        if (array == null || array.length() == 0) return false
        val dao = database.borrowingDao()
        val existingById = dao.getAllIncludingDeleted(ownerId).associateBy { it.id }
        val toUpsert = ArrayList<BorrowingRecordEntity>()
        val now = System.currentTimeMillis()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[id]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            val rawSourceTx = if (item.isNull("sourceTransactionId")) null else item.optString("sourceTransactionId", "").ifBlank { null }
            toUpsert.add(
                BorrowingRecordEntity(
                    id = id,
                    ledgerId = item.optString("ledgerId", local?.ledgerId ?: "default").ifBlank { "default" },
                    direction = item.optString("direction", local?.direction ?: "LEND").ifBlank { "LEND" },
                    counterparty = item.optString("counterparty", local?.counterparty ?: ""),
                    amountCents = item.optLong("amountCents", local?.amountCents ?: 0L).coerceAtLeast(0L),
                    occurredAt = item.optLong("occurredAt", local?.occurredAt ?: remoteUpdatedAt.takeIf { it > 0L } ?: now),
                    note = item.optString("note", local?.note ?: ""),
                    sourceTransactionId = rawSourceTx,
                    settled = item.optBoolean("settled", local?.settled ?: false),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        dao.insertAll(toUpsert)
        return true
    }

    internal fun applyPulledSavingsGoalsLww(ownerId: String, array: JSONArray?): Boolean {
        if (array == null || array.length() == 0) return false
        val dao = database.savingsGoalDao()
        val existingById = dao.getAllIncludingDeleted(ownerId).associateBy { it.id }
        val toUpsert = ArrayList<SavingsGoalEntity>()
        val now = System.currentTimeMillis()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[id]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            toUpsert.add(
                SavingsGoalEntity(
                    id = id,
                    title = item.optString("title", local?.title ?: ""),
                    targetCents = item.optLong("targetCents", local?.targetCents ?: 0L).coerceAtLeast(0L),
                    savedCents = item.optLong("savedCents", local?.savedCents ?: 0L).coerceAtLeast(0L),
                    deadline = item.optString("deadline", local?.deadline ?: ""),
                    createdAtMillis = item.optLong("createdAtMillis", local?.createdAtMillis ?: remoteUpdatedAt.takeIf { it > 0L } ?: now),
                    sortOrder = item.optInt("sortOrder", local?.sortOrder ?: i),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        dao.insertAll(toUpsert)
        return true
    }

    internal fun applyPulledCustomCategoriesLww(ownerId: String, array: JSONArray?): Boolean {
        if (array == null || array.length() == 0) return false
        val dao = database.categoryDao()
        val existingByKey = dao.getAllIncludingDeleted(ownerId).associateBy { "${it.type}\u0000${it.name}" }
        val toUpsert = ArrayList<CustomCategoryEntity>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val type = item.optString("type", "").trim()
            val name = item.optString("name", "").trim()
            if (type.isEmpty() || name.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingByKey["$type\u0000$name"]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            toUpsert.add(
                CustomCategoryEntity(
                    id = local?.id ?: 0L,
                    type = type,
                    name = name,
                    isDeletedDefault = item.optBoolean("isDeletedDefault", local?.isDeletedDefault ?: false),
                    sortOrder = item.optInt("sortOrder", local?.sortOrder ?: i),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        dao.insertAll(toUpsert)
        return true
    }

    internal fun applyPulledStudyPlansLww(ownerId: String, array: JSONArray?): Boolean {
        if (array == null || array.length() == 0) return false
        val dao = database.studyDao()
        val existingById = dao.getAllPlansIncludingDeleted(ownerId).associateBy { it.id }
        val toUpsert = ArrayList<StudyPlanEntity>()
        val now = System.currentTimeMillis()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[id]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            toUpsert.add(
                StudyPlanEntity(
                    id = id,
                    title = item.optString("title", local?.title ?: ""),
                    kind = item.optString("kind", local?.kind ?: "CUSTOM"),
                    due = item.optString("due", local?.due ?: ""),
                    partner = item.optString("partner", local?.partner ?: "SQUIRREL"),
                    previousPlanId = item.optString("previousPlanId", local?.previousPlanId ?: ""),
                    createdAt = item.optLong("createdAt", local?.createdAt ?: remoteUpdatedAt.takeIf { it > 0L } ?: now),
                    archivedAt = item.optLong("archivedAt", local?.archivedAt ?: 0L),
                    selectedSubjectId = item.optString("selectedSubjectId", local?.selectedSubjectId ?: ""),
                    sessionGoal = item.optInt("sessionGoal", local?.sessionGoal ?: 45),
                    dailyGoal = item.optInt("dailyGoal", local?.dailyGoal ?: 180),
                    remindDeadlines = item.optBoolean("remindDeadlines", local?.remindDeadlines ?: false),
                    remindTime = item.optString("remindTime", local?.remindTime ?: "18:00"),
                    isCurrent = item.optBoolean("isCurrent", local?.isCurrent ?: false),
                    sortOrder = item.optInt("sortOrder", local?.sortOrder ?: i),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        dao.insertPlans(toUpsert)
        return true
    }

    internal fun applyPulledStudySubjectsLww(ownerId: String, array: JSONArray?): Boolean {
        if (array == null || array.length() == 0) return false
        val dao = database.studyDao()
        val knownPlanIds = dao.getAllPlansIncludingDeleted(ownerId).map { it.id }.toHashSet()
        if (knownPlanIds.isEmpty()) return false
        val existingByKey = dao.getAllSubjectsIncludingDeleted(ownerId).associateBy { "${it.planId}\u0000${it.id}" }
        val toUpsert = ArrayList<StudySubjectEntity>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val planId = item.optString("planId", "").trim()
            val id = item.optString("id", "").trim()
            if (planId.isEmpty() || id.isEmpty() || !knownPlanIds.contains(planId)) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingByKey["$planId\u0000$id"]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            toUpsert.add(
                StudySubjectEntity(
                    id = id,
                    planId = planId,
                    title = item.optString("title", local?.title ?: ""),
                    hint = item.optString("hint", local?.hint ?: ""),
                    due = item.optString("due", local?.due ?: ""),
                    assessment = item.optString("assessment", local?.assessment ?: ""),
                    color = item.optInt("color", local?.color ?: 0),
                    sortOrder = item.optInt("sortOrder", local?.sortOrder ?: i),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        dao.insertSubjects(toUpsert)
        return true
    }

    internal fun applyPulledStudyRecordsLww(ownerId: String, array: JSONArray?): Boolean {
        if (array == null || array.length() == 0) return false
        val dao = database.studyDao()
        val existingById = dao.getAllRecordsIncludingDeleted(ownerId).associateBy { it.id }
        val toUpsert = ArrayList<StudyRecordEntity>()
        val now = System.currentTimeMillis()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[id]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            val duration = item.optLong("durationMillis", local?.durationMillis ?: 0L).coerceAtLeast(0L)
            toUpsert.add(
                StudyRecordEntity(
                    id = id,
                    planId = item.optString("planId", local?.planId ?: ""),
                    subjectId = item.optString("subjectId", local?.subjectId ?: ""),
                    subjectTitleSnapshot = item.optString("subjectTitleSnapshot", local?.subjectTitleSnapshot ?: ""),
                    subjectHintSnapshot = item.optString("subjectHintSnapshot", local?.subjectHintSnapshot ?: ""),
                    subjectColorSnapshot = item.optInt("subjectColorSnapshot", local?.subjectColorSnapshot ?: 0),
                    subjectDueSnapshot = item.optString("subjectDueSnapshot", local?.subjectDueSnapshot ?: ""),
                    subjectAssessmentSnapshot = item.optString("subjectAssessmentSnapshot", local?.subjectAssessmentSnapshot ?: ""),
                    durationMillis = duration,
                    completedAt = item.optLong("completedAt", local?.completedAt ?: remoteUpdatedAt.takeIf { it > 0L } ?: now),
                    note = item.optString("note", local?.note ?: ""),
                    manual = item.optBoolean("manual", local?.manual ?: false),
                    rewardMillis = item.optLong("rewardMillis", local?.rewardMillis ?: duration),
                    editedAt = item.optLong("editedAt", local?.editedAt ?: 0L),
                    adjusted = item.optBoolean("adjusted", local?.adjusted ?: false),
                    taskId = item.optString("taskId", local?.taskId ?: ""),
                    sortOrder = item.optInt("sortOrder", local?.sortOrder ?: i),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        dao.insertRecords(toUpsert)
        return true
    }

    internal fun applyPulledStudyTasksLww(ownerId: String, array: JSONArray?): Boolean {
        if (array == null || array.length() == 0) return false
        val dao = database.studyDao()
        val existingById = dao.getAllTasksIncludingDeleted(ownerId).associateBy { it.id }
        val toUpsert = ArrayList<StudyTaskEntity>()
        val now = System.currentTimeMillis()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[id]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            toUpsert.add(
                StudyTaskEntity(
                    id = id,
                    planId = item.optString("planId", local?.planId ?: ""),
                    subjectId = item.optString("subjectId", local?.subjectId ?: ""),
                    title = item.optString("title", local?.title ?: ""),
                    due = item.optString("due", local?.due ?: ""),
                    dueTime = item.optString("dueTime", local?.dueTime ?: ""),
                    remind = item.optBoolean("remind", local?.remind ?: false),
                    remindLeadMinutes = item.optInt("remindLeadMinutes", local?.remindLeadMinutes ?: 30),
                    createdAt = item.optLong("createdAt", local?.createdAt ?: remoteUpdatedAt.takeIf { it > 0L } ?: now),
                    doneAt = item.optLong("doneAt", local?.doneAt ?: 0L),
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    isNext = item.optBoolean("isNext", local?.isNext ?: false),
                    priority = item.optLong("priority", local?.priority ?: 0L),
                    ownerId = ownerId,
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        dao.insertTasks(toUpsert)
        return true
    }

    internal fun applyPulledStudyReviewsLww(ownerId: String, array: JSONArray?): Boolean {
        if (array == null || array.length() == 0) return false
        val dao = database.studyDao()
        val existingById = dao.getAllReviewsIncludingDeleted(ownerId).associateBy { it.recordId }
        val toUpsert = ArrayList<StudyReviewEntity>()
        val now = System.currentTimeMillis()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val recordId = item.optString("recordId", "").trim()
            if (recordId.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[recordId]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            toUpsert.add(
                StudyReviewEntity(
                    recordId = recordId,
                    prompt = item.optString("prompt", local?.prompt ?: ""),
                    day = item.optString("day", local?.day ?: ""),
                    completedAt = item.optLong("completedAt", local?.completedAt ?: remoteUpdatedAt.takeIf { it > 0L } ?: now),
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    ownerId = ownerId,
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        dao.insertReviews(toUpsert)
        return true
    }

    internal fun applyPulledStudyGardenStateLww(ownerId: String, item: JSONObject?): Boolean {
        if (item == null) return false
        val dao = database.studyDao()
        val local = dao.getGardenState(ownerId)
        val remoteUpdatedAt = item.optLong("updatedAt", 0L)
        if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
            return false
        }
        dao.upsertGardenState(
            StudyGardenStateEntity(
                ownerId = ownerId,
                currentPlanId = item.optString("currentPlanId", local?.currentPlanId ?: ""),
                water = item.optInt("water", local?.water ?: 0),
                carryMillis = item.optLong("carryMillis", local?.carryMillis ?: 0L),
                seeds = item.optInt("seeds", local?.seeds ?: 1),
                planted = item.optBoolean("planted", local?.planted ?: false),
                growth = item.optInt("growth", local?.growth ?: 0),
                trees = item.optInt("trees", local?.trees ?: 0),
                rewardedDaysJson = item.optString("rewardedDaysJson", local?.rewardedDaysJson ?: "[]").ifBlank { "[]" },
                activeSessionId = local?.activeSessionId,
                activePlanId = local?.activePlanId,
                activeSubjectJson = local?.activeSubjectJson,
                activeGoal = local?.activeGoal ?: 0,
                activeTaskId = local?.activeTaskId ?: "",
                activeAccumulated = local?.activeAccumulated ?: 0L,
                activeWall = local?.activeWall ?: 0L,
                activeElapsed = local?.activeElapsed ?: 0L,
                activeBoot = local?.activeBoot ?: 0,
                activeRunning = local?.activeRunning ?: false,
                updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                syncStatus = SyncStatus.SYNCED,
                clientRequestId = local?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                    ?: AccountScope.newRequestId()
            )
        )
        return true
    }

    internal fun applyPulledTodosLww(ownerId: String, array: JSONArray?): Boolean {
        if (array == null || array.length() == 0) return false
        val dao = database.todoDao()
        val existingById = dao.getAllIncludingDeleted(ownerId).associateBy { it.id }
        val toUpsert = ArrayList<TodoItemEntity>()
        val now = System.currentTimeMillis()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[id]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            toUpsert.add(
                TodoItemEntity(
                    id = id,
                    title = item.optString("title", local?.title ?: ""),
                    note = item.optString("note", local?.note ?: ""),
                    dueDate = item.optString("dueDate", local?.dueDate ?: ""),
                    dueTime = item.optString("dueTime", local?.dueTime ?: ""),
                    remind = item.optBoolean("remind", local?.remind ?: false),
                    remindBeforeMinutes = item.optInt("remindBeforeMinutes", local?.remindBeforeMinutes ?: 30),
                    createdAt = item.optLong("createdAt", local?.createdAt ?: remoteUpdatedAt.takeIf { it > 0L } ?: now),
                    completedAt = item.optLong("completedAt", local?.completedAt ?: 0L),
                    sortOrder = item.optInt("sortOrder", local?.sortOrder ?: i),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        dao.insertAll(toUpsert)
        return true
    }

    internal fun applyPulledRecurringRulesLww(ownerId: String, rulesArray: JSONArray?): Boolean {
        if (rulesArray == null || rulesArray.length() == 0) return false
        val recurringDao = database.recurringRuleDao()
        val existingById = recurringDao.getAllIncludingDeleted(ownerId).associateBy { it.id }
        val toUpsert = ArrayList<RecurringRuleEntity>()
        for (i in 0 until rulesArray.length()) {
            val item = rulesArray.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            val remoteUpdatedAt = item.optLong("updatedAt", 0L)
            val local = existingById[id]
            if (local != null && shouldKeepLocal(local.updatedAt, local.syncStatus, remoteUpdatedAt)) {
                continue
            }
            toUpsert.add(
                RecurringRuleEntity(
                    id = id,
                    ledgerId = item.optString("ledgerId", local?.ledgerId ?: "default"),
                    title = item.optString("title", local?.title ?: ""),
                    type = item.optString("type", local?.type ?: "EXPENSE"),
                    category = item.optString("category", local?.category ?: "餐饮"),
                    amountCents = item.optLong("amountCents", local?.amountCents ?: 1L).coerceAtLeast(1L),
                    frequency = item.optString("frequency", local?.frequency ?: "MONTHLY"),
                    dayOfMonth = item.optInt("dayOfMonth", local?.dayOfMonth ?: 1).coerceIn(1, 31),
                    dayOfWeek = item.optInt("dayOfWeek", local?.dayOfWeek ?: 1).coerceIn(1, 7),
                    monthOfYear = item.optInt("monthOfYear", local?.monthOfYear ?: 1).coerceIn(1, 12),
                    startDateKey = item.optString("startDateKey", local?.startDateKey ?: ""),
                    lastGeneratedDateKey = item.optString("lastGeneratedDateKey", local?.lastGeneratedDateKey ?: ""),
                    autoPost = item.optBoolean("autoPost", local?.autoPost ?: true),
                    enabled = item.optBoolean("enabled", local?.enabled ?: true),
                    note = item.optString("note", local?.note ?: ""),
                    sortOrder = item.optInt("sortOrder", local?.sortOrder ?: i),
                    ownerId = ownerId,
                    updatedAt = maxOf(remoteUpdatedAt, local?.updatedAt ?: 0L),
                    deletedAt = item.optLong("deletedAt", 0L).coerceAtLeast(0L),
                    syncStatus = SyncStatus.SYNCED,
                    clientRequestId = item.optString("clientRequestId", local?.clientRequestId ?: "")
                        .ifEmpty { AccountScope.newRequestId() }
                )
            )
        }
        if (toUpsert.isEmpty()) return false
        recurringDao.upsertAll(toUpsert)
        return true
    }

    private sealed class RefreshOutcome {
        data class Refreshed(val accessToken: String, val refreshToken: String) : RefreshOutcome()
        data object NetworkError : RefreshOutcome()
        data class Expired(val message: String) : RefreshOutcome()
    }

    companion object {
        private val SYNC_LOCK = Any()
        private const val SESSION_EXPIRED_MESSAGE = "登录状态已过期，请重新登录以恢复云同步"
    }
}

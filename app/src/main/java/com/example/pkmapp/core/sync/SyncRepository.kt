package com.example.pkmapp.core.sync

import android.content.Context
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.AppDatabase
import com.example.pkmapp.core.database.LedgerEntity
import com.example.pkmapp.core.database.SyncStatus
import com.example.pkmapp.core.security.CryptoSessionStore
import com.example.pkmapp.data.InMemoryLedgerRepository
import org.json.JSONArray
import org.json.JSONObject

sealed class SyncOutcome {
    data class Synced(val pushedCount: Int, val serverCursor: Long) : SyncOutcome()
    data class LocalOnly(val pendingCount: Int, val reason: String) : SyncOutcome()
    data class Failed(val pendingCount: Int, val message: String) : SyncOutcome()
}

class SyncRepository @JvmOverloads constructor(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context.applicationContext),
    private val sessionStore: CryptoSessionStore = CryptoSessionStore.get(context.applicationContext)
) {
    private val appContext = context.applicationContext

    fun pendingCount(ownerId: String = AccountScope.currentOwnerId(appContext)): Int {
        return database.ledgerDao().countPendingLedgers(ownerId) +
            database.ledgerDao().countPendingTransactions(ownerId) +
            database.borrowingDao().countPending(ownerId) +
            database.savingsGoalDao().countPending(ownerId) +
            database.categoryDao().countPending(ownerId) +
            database.studyDao().countPendingPlans(ownerId) +
            database.studyDao().countPendingRecords(ownerId) +
            database.studyDao().countPendingTasks(ownerId) +
            database.todoDao().countPending(ownerId) +
            database.feedbackDao().countPendingByOwner(ownerId)
    }

    fun syncNow(): SyncOutcome {
        val session = sessionStore.currentSession()
        val pending = pendingCount(session.ownerId)
        if (session.isGuest) {
            return SyncOutcome.LocalOnly(pending, "当前为本机游客模式，数据安全保存在本地 Room")
        }
        if (session.serverBaseUrl.isBlank()) {
            return SyncOutcome.LocalOnly(pending, "未配置自建后端地址，已暂存 $pending 条待同步变更")
        }
        if (session.accessToken.isBlank() || session.accessToken.startsWith("offline_session_")) {
            return SyncOutcome.LocalOnly(pending, "当前为离线登录会话，联网并重新登录后自动同步")
        }

        val ownerId = session.ownerId
        val ledgerDao = database.ledgerDao()
        val borrowingDao = database.borrowingDao()
        val savingsDao = database.savingsGoalDao()
        val categoryDao = database.categoryDao()
        val studyDao = database.studyDao()
        val todoDao = database.todoDao()
        val feedbackDao = database.feedbackDao()

        val dirtyLedgers = ledgerDao.getPendingLedgers(ownerId)
        val dirtyTx = ledgerDao.getPendingTransactions(ownerId)
        val dirtyBorrowings = borrowingDao.getPending(ownerId)
        val dirtyGoals = savingsDao.getPending(ownerId)
        val dirtyCats = categoryDao.getPending(ownerId)
        val dirtyPlans = studyDao.getPendingPlans(ownerId)
        val dirtyRecords = studyDao.getPendingRecords(ownerId)
        val dirtyTasks = studyDao.getPendingTasks(ownerId)
        val dirtyTodos = todoDao.getPending(ownerId)
        val dirtyFeedbacks = feedbackDao.getPendingByOwner(ownerId)

        val payload = JSONObject().apply {
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
                            .put("updatedAt", it.updatedAt)
                            .put("deletedAt", it.deletedAt)
                            .put("clientRequestId", it.clientRequestId)
                    )
                }
            })
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
                            .put("completedAt", it.completedAt)
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
                            .put("durationMillis", it.durationMillis)
                            .put("completedAt", it.completedAt)
                            .put("note", it.note)
                            .put("updatedAt", it.updatedAt)
                            .put("deletedAt", it.deletedAt)
                            .put("clientRequestId", it.clientRequestId)
                    )
                }
            })
        }

        val pushedTotal = dirtyLedgers.size + dirtyTx.size + dirtyBorrowings.size +
            dirtyGoals.size + dirtyCats.size + dirtyPlans.size + dirtyRecords.size +
            dirtyTasks.size + dirtyTodos.size + dirtyFeedbacks.size

        var serverCursor = database.localAccountDao().findByOwnerId(ownerId)?.lastSyncCursor ?: 0L
        if (pushedTotal > 0) {
            val pushRes = SyncApiClient.pushSyncBatch(session.serverBaseUrl, session.accessToken, payload)
                ?: return SyncOutcome.Failed(pending, "无法连接同步服务器，变更仍安全保存在本地")
            if (!pushRes.ok) {
                return SyncOutcome.Failed(pending, pushRes.errorMessage.ifEmpty { "云端同步失败" })
            }
            if (pushRes.serverSeq > serverCursor) {
                serverCursor = pushRes.serverSeq
            }
            val now = System.currentTimeMillis()
            database.runInTransaction {
                if (dirtyLedgers.isNotEmpty()) ledgerDao.markLedgersSynced(ownerId, dirtyLedgers.map { it.id })
                if (dirtyTx.isNotEmpty()) ledgerDao.markTransactionsSynced(ownerId, dirtyTx.map { it.id })
                if (dirtyBorrowings.isNotEmpty()) borrowingDao.markSynced(ownerId, dirtyBorrowings.map { it.id })
                if (dirtyGoals.isNotEmpty()) savingsDao.markSynced(ownerId, dirtyGoals.map { it.id })
                if (dirtyCats.isNotEmpty()) categoryDao.markSynced(ownerId, dirtyCats.map { it.id })
                if (dirtyPlans.isNotEmpty()) studyDao.markAllPlansSynced(ownerId)
                if (dirtyRecords.isNotEmpty()) studyDao.markAllRecordsSynced(ownerId)
                if (dirtyTasks.isNotEmpty()) studyDao.markAllTasksSynced(ownerId)
                if (dirtyTodos.isNotEmpty()) todoDao.markSynced(ownerId, dirtyTodos.map { it.id })
                if (dirtyFeedbacks.isNotEmpty()) {
                    feedbackDao.markSynced(ownerId, dirtyFeedbacks.map { it.id }, "pending", SyncStatus.SYNCED, now)
                }
                database.localAccountDao().updateSyncCursor(ownerId, serverCursor, now)
            }
        }

        val delta = SyncApiClient.pullSyncDelta(session.serverBaseUrl, session.accessToken, serverCursor)
        if (delta != null) {
            val appliedLedgers = applyPulledLedgersLww(ownerId, delta.optJSONArray("ledgers"))
            val nextCursor = delta.optLong("nextCursor", serverCursor)
            if (nextCursor > serverCursor) {
                serverCursor = nextCursor
                database.localAccountDao().updateSyncCursor(ownerId, serverCursor, System.currentTimeMillis())
            }
            if (appliedLedgers && AccountScope.currentOwnerId(appContext) == ownerId) {
                InMemoryLedgerRepository.getInstance().reloadFromPersistence()
            }
        }

        return SyncOutcome.Synced(pushedTotal, serverCursor)
    }

    internal fun applyPulledLedgersLww(ownerId: String, ledgersArray: JSONArray?): Boolean {
        if (ledgersArray == null || ledgersArray.length() == 0) {
            return false
        }
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
            if (local != null && local.updatedAt > remoteUpdatedAt) {
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
        if (toUpsert.isEmpty()) {
            return false
        }
        ledgerDao.upsertAll(toUpsert)
        return true
    }
}

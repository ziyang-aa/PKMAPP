package com.example.pkmapp.core.feedback

import android.content.Context
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.AppDatabase
import com.example.pkmapp.core.database.FeedbackRecordEntity
import com.example.pkmapp.core.database.SyncStatus
import com.example.pkmapp.core.security.CryptoSessionStore
import com.example.pkmapp.core.sync.SyncApiClient
import java.util.UUID

class FeedbackRepository @JvmOverloads constructor(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context.applicationContext),
    private val sessionStore: CryptoSessionStore = CryptoSessionStore.get(context.applicationContext)
) {
    private val appContext = context.applicationContext
    private val feedbackDao = database.feedbackDao()

    enum class Category(val code: String, val label: String) {
        SUGGESTION("suggestion", "功能建议"),
        UX("ux", "体验优化"),
        BUG("bug", "问题报错"),
        OTHER("other", "其他反馈");

        companion object {
            fun labelFor(code: String): String =
                entries.firstOrNull { it.code == code }?.label ?: OTHER.label
        }
    }

    sealed class SubmitResult {
        data class Saved(val record: FeedbackRecordEntity, val syncedRemote: Boolean) : SubmitResult()
        data class ValidationError(val message: String) : SubmitResult()
    }

    fun listMine(): List<FeedbackRecordEntity> {
        val ownerId = AccountScope.currentOwnerId(appContext)
        return feedbackDao.getByOwner(ownerId)
    }

    fun refreshFromRemoteIfConfigured(): List<FeedbackRecordEntity> {
        val session = sessionStore.currentSession()
        if (session.serverBaseUrl.isNotEmpty() && !session.isGuest &&
            session.accessToken.isNotEmpty() && !session.accessToken.startsWith("offline_session_")
        ) {
            val remote = SyncApiClient.fetchMyFeedbacks(session.serverBaseUrl, session.accessToken)
            if (remote != null && remote.isNotEmpty()) {
                val mapped = remote.map { item ->
                    FeedbackRecordEntity(
                        id = item.id,
                        ownerId = session.ownerId,
                        category = item.category,
                        content = item.content,
                        contact = item.contact,
                        appVersion = item.appVersion,
                        status = item.status,
                        reply = item.reply,
                        clientRequestId = item.clientRequestId,
                        syncStatus = SyncStatus.SYNCED,
                        createdAt = item.createdAt,
                        updatedAt = item.updatedAt
                    )
                }
                feedbackDao.upsertAll(mapped)
            }
        }
        return listMine()
    }

    fun submit(
        category: Category,
        rawContent: String,
        rawContact: String,
        appVersion: String = "4.0.0"
    ): SubmitResult {
        val content = rawContent.trim()
        if (content.length < 5) {
            return SubmitResult.ValidationError("请至少输入 5 个字的详细说明")
        }
        if (content.length > 500) {
            return SubmitResult.ValidationError("反馈内容请控制在 500 字以内")
        }
        val contact = rawContact.trim().take(60)
        val now = System.currentTimeMillis()
        val session = sessionStore.currentSession()
        val requestId = AccountScope.newRequestId()
        val record = FeedbackRecordEntity(
            id = "fb_${UUID.randomUUID()}",
            ownerId = session.ownerId,
            category = category.code,
            content = content,
            contact = contact,
            appVersion = appVersion,
            status = "local_saved",
            reply = "",
            clientRequestId = requestId,
            syncStatus = SyncStatus.PENDING_PUSH,
            createdAt = now,
            updatedAt = now
        )
        feedbackDao.upsert(record)

        var syncedRemote = false
        if (session.serverBaseUrl.isNotEmpty() && !session.isGuest &&
            session.accessToken.isNotEmpty() && !session.accessToken.startsWith("offline_session_")
        ) {
            val pushed = SyncApiClient.submitFeedback(
                baseUrl = session.serverBaseUrl,
                accessToken = session.accessToken,
                category = record.category,
                content = record.content,
                contact = record.contact,
                appVersion = record.appVersion,
                clientRequestId = record.clientRequestId
            )
            if (pushed) {
                feedbackDao.markSynced(
                    ownerId = session.ownerId,
                    ids = listOf(record.id),
                    status = "pending",
                    syncStatus = SyncStatus.SYNCED,
                    updatedAt = System.currentTimeMillis()
                )
                syncedRemote = true
            }
        }

        val latest = feedbackDao.getByOwner(session.ownerId).firstOrNull { it.id == record.id } ?: record
        return SubmitResult.Saved(latest, syncedRemote)
    }

    companion object {
        fun statusLabel(status: String): String = when (status) {
            "pending" -> "已收到"
            "reviewing" -> "处理中"
            "resolved" -> "已回复"
            else -> "离线暂存"
        }
    }
}

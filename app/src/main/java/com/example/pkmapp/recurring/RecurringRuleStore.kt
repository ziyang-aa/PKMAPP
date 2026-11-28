package com.example.pkmapp.recurring

import android.content.Context
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.AppDatabase
import com.example.pkmapp.core.database.RecurringRuleEntity
import com.example.pkmapp.core.database.SyncStatus
import com.example.pkmapp.core.work.SyncWorker
import com.example.pkmapp.data.TransactionType

/**
 * Room-backed store for recurring bills / subscriptions, scoped by active ownerId.
 */
class RecurringRuleStore(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).recurringRuleDao()

    private fun ownerId(): String = AccountScope.currentOwnerId(appContext)

    @Synchronized
    fun getAll(): List<RecurringRule> =
        dao.getAllActive(ownerId()).mapNotNull(::fromEntity)

    @Synchronized
    fun getForLedger(ledgerId: String): List<RecurringRule> {
        if (ledgerId.isBlank()) return emptyList()
        return dao.getForLedger(ledgerId.trim(), ownerId()).mapNotNull(::fromEntity)
    }

    @Synchronized
    fun save(rule: RecurringRule) {
        val owner = ownerId()
        val existing = dao.findById(rule.id, owner)
        val now = System.currentTimeMillis()
        dao.upsert(
            RecurringRuleEntity(
                id = rule.id,
                ledgerId = rule.ledgerId,
                title = rule.title.trim(),
                type = rule.type.name,
                category = rule.category.trim(),
                amountCents = rule.amountCents.coerceAtLeast(1L),
                frequency = rule.frequency.name,
                dayOfMonth = rule.dayOfMonth.coerceIn(1, 31),
                dayOfWeek = rule.dayOfWeek.coerceIn(1, 7),
                monthOfYear = rule.monthOfYear.coerceIn(1, 12),
                startDateKey = rule.startDateKey.trim(),
                lastGeneratedDateKey = rule.lastGeneratedDateKey.trim(),
                autoPost = rule.autoPost,
                enabled = rule.enabled,
                note = rule.note.trim(),
                sortOrder = existing?.sortOrder ?: rule.sortOrder,
                ownerId = owner,
                updatedAt = now,
                deletedAt = 0L,
                syncStatus = SyncStatus.PENDING_PUSH,
                clientRequestId = existing?.clientRequestId?.ifEmpty { AccountScope.newRequestId() }
                    ?: AccountScope.newRequestId()
            )
        )
        SyncWorker.scheduleDebouncedSync(appContext)
    }

    @Synchronized
    fun setEnabled(ruleId: String, enabled: Boolean) {
        val owner = ownerId()
        val existing = dao.findById(ruleId, owner) ?: return
        if (existing.enabled == enabled) return
        dao.upsert(
            existing.copy(
                enabled = enabled,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_PUSH
            )
        )
        SyncWorker.scheduleDebouncedSync(appContext)
    }

    @Synchronized
    fun advanceLastGenerated(ruleId: String, dateKey: String) {
        val owner = ownerId()
        val existing = dao.findById(ruleId, owner) ?: return
        if (existing.lastGeneratedDateKey >= dateKey) return
        dao.upsert(
            existing.copy(
                lastGeneratedDateKey = dateKey,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_PUSH
            )
        )
        SyncWorker.scheduleDebouncedSync(appContext)
    }

    @Synchronized
    fun delete(ruleId: String) {
        dao.softDelete(ruleId, ownerId(), System.currentTimeMillis())
        SyncWorker.scheduleDebouncedSync(appContext)
    }

    @Synchronized
    fun deleteForLedger(ledgerId: String) {
        if (ledgerId.isBlank()) return
        dao.softDeleteForLedger(ledgerId.trim(), ownerId(), System.currentTimeMillis())
        SyncWorker.scheduleDebouncedSync(appContext)
    }

    companion object {
        fun fromEntity(row: RecurringRuleEntity): RecurringRule? {
            if (row.id.isBlank() || row.title.isBlank() || row.amountCents <= 0L) return null
            val txType = if (TransactionType.INCOME.name.equals(row.type, ignoreCase = true)) {
                TransactionType.INCOME
            } else {
                TransactionType.EXPENSE
            }
            return RecurringRule(
                id = row.id,
                ledgerId = row.ledgerId,
                title = row.title,
                type = txType,
                category = row.category.ifBlank { if (txType == TransactionType.INCOME) "工资" else "餐饮" },
                amountCents = row.amountCents,
                frequency = RecurringFrequency.parse(row.frequency),
                dayOfMonth = row.dayOfMonth.coerceIn(1, 31),
                dayOfWeek = row.dayOfWeek.coerceIn(1, 7),
                monthOfYear = row.monthOfYear.coerceIn(1, 12),
                startDateKey = row.startDateKey,
                lastGeneratedDateKey = row.lastGeneratedDateKey,
                autoPost = row.autoPost,
                enabled = row.enabled,
                note = row.note,
                sortOrder = row.sortOrder
            )
        }
    }
}

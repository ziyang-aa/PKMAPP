package com.example.pkmapp.core.database

import com.example.pkmapp.borrowing.BorrowingRecordCodec
import com.example.pkmapp.core.datastore.AppPreferencesSnapshot
import com.example.pkmapp.data.LedgerCodec
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.study.StudyNotebook
import com.example.pkmapp.study.StudyNotebookSqlBridge
import com.example.pkmapp.todo.TodoStore
import java.io.File
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class LegacyRawPreferencesData(
    val ledgerStateJson: String? = null,
    val assetEntries: Map<String, Long> = emptyMap(),
    val borrowingRecordsJson: String? = null,
    val savingsGoalsJson: String? = null,
    val categoryEntries: Map<String, String> = emptyMap(),
    val studyNotebookJson: String? = null,
    val todoDataJson: String? = null,
    val nickname: String? = null,
    val checkedDates: Set<String>? = null,
    val rememberLastCottage: Boolean? = null,
    val lastCottage: String? = null,
    val countdownMode: Boolean? = null,
    val keepScreenOn: Boolean? = null,
    val askedNotifications: Boolean? = null
) {
    fun hasAnyLegacyData(): Boolean =
        ledgerStateJson != null ||
            assetEntries.isNotEmpty() ||
            borrowingRecordsJson != null ||
            savingsGoalsJson != null ||
            categoryEntries.isNotEmpty() ||
            studyNotebookJson != null ||
            todoDataJson != null ||
            nickname != null ||
            !checkedDates.isNullOrEmpty() ||
            rememberLastCottage != null ||
            !lastCottage.isNullOrEmpty() ||
            countdownMode != null ||
            keepScreenOn != null ||
            askedNotifications != null

    fun toColdBackupJson(exportedAtMillis: Long): String {
        val assetsObj = JSONObject()
        for ((k, v) in assetEntries) assetsObj.put(k, v)
        val categoriesObj = JSONObject()
        for ((k, v) in categoryEntries) categoriesObj.put(k, v)
        return JSONObject()
            .put("backupFormatVersion", 1)
            .put("exportedAtMillis", exportedAtMillis)
            .put("ledger_data_state", ledgerStateJson ?: JSONObject.NULL)
            .put("forest_assets", assetsObj)
            .put("borrowing_records", borrowingRecordsJson ?: JSONObject.NULL)
            .put("savings_goals", savingsGoalsJson ?: JSONObject.NULL)
            .put("record_categories", categoriesObj)
            .put("forest_study_v1_notebook", studyNotebookJson ?: JSONObject.NULL)
            .put("forest_todo_v1_data", todoDataJson ?: JSONObject.NULL)
            .put(
                "forest_profile",
                JSONObject()
                    .put("nickname", nickname ?: JSONObject.NULL)
                    .put("checked_dates", JSONArray(checkedDates?.toList() ?: emptyList<String>()))
            )
            .put(
                "forest_home_prefs",
                JSONObject()
                    .put("remember_last_cottage", rememberLastCottage ?: JSONObject.NULL)
                    .put("last_cottage", lastCottage ?: JSONObject.NULL)
            )
            .put(
                "study_ui_prefs",
                JSONObject()
                    .put("countdown_mode", countdownMode ?: JSONObject.NULL)
                    .put("keep_screen_on", keepScreenOn ?: JSONObject.NULL)
                    .put("askedNotifications", askedNotifications ?: JSONObject.NULL)
            )
            .toString()
    }
}

data class MigratedPayload(
    val ledgers: List<LedgerEntity>,
    val transactions: List<TransactionEntity>,
    val borrowingRecords: List<BorrowingRecordEntity>,
    val savingsGoals: List<SavingsGoalEntity>,
    val customCategories: List<CustomCategoryEntity>,
    val studySnapshot: StudyDatabaseSnapshot?,
    val todoItems: List<TodoItemEntity>,
    val preferences: AppPreferencesSnapshot,
    val legacyStudyBeforePlansJson: String?,
    val upgradedStudyNotebookJson: String?,
    val unreadableStudyRawJson: String?
)

interface LegacyMigrationEnvironment {
    fun isMigrationDone(): Boolean
    fun markMigrationDone()
    fun readLegacyPreferences(): LegacyRawPreferencesData
    fun backupDirectory(): File
    fun onStudyLegacySideEffects(
        legacyBeforePlansJson: String?,
        upgradedNotebookJson: String?,
        unreadableRawJson: String?,
        nowMillis: Long
    )
    fun commitPayloadAtomically(payload: MigratedPayload)
    fun nowMillis(): Long = System.currentTimeMillis()
}

object LegacySharedPrefsMigrator {
    const val MIGRATION_MARKER_KEY = "legacy_migration_v1_done"
    const val BACKUP_DIR_NAME = "migration_backup_v1"
    const val BACKUP_FILE_NAME = "legacy_prefs_snapshot_v1.json"

    private const val INITIAL_ASSETS_PREFIX = "initial_assets_"
    private const val LIABILITIES_PREFIX = "liabilities_"

    @Synchronized
    fun migrateIfNeeded(env: LegacyMigrationEnvironment): Boolean {
        if (env.isMigrationDone()) {
            return false
        }
        val now = env.nowMillis()
        val raw = env.readLegacyPreferences()
        if (raw.hasAnyLegacyData()) {
            writeColdBackup(env.backupDirectory(), raw.toColdBackupJson(now))
        }
        val payload = parseLegacyPayload(raw, now)
        env.onStudyLegacySideEffects(
            legacyBeforePlansJson = payload.legacyStudyBeforePlansJson,
            upgradedNotebookJson = payload.upgradedStudyNotebookJson,
            unreadableRawJson = payload.unreadableStudyRawJson,
            nowMillis = now
        )
        env.commitPayloadAtomically(payload)
        env.markMigrationDone()
        return true
    }

    internal fun parseLegacyPayload(raw: LegacyRawPreferencesData, nowMillis: Long): MigratedPayload {
        // 1. Ledgers, Assets & Transactions
        val decodedLedger = LedgerCodec.decode(raw.ledgerStateJson)
        val initialAssetsByLedger = LinkedHashMap<String, Long>()
        val liabilitiesByLedger = LinkedHashMap<String, Long>()
        for ((key, value) in raw.assetEntries) {
            when {
                key.startsWith(INITIAL_ASSETS_PREFIX) -> {
                    val id = key.removePrefix(INITIAL_ASSETS_PREFIX).trim()
                    if (id.isNotEmpty() && value >= 0L) initialAssetsByLedger[id] = value
                }
                key.startsWith(LIABILITIES_PREFIX) -> {
                    val id = key.removePrefix(LIABILITIES_PREFIX).trim()
                    if (id.isNotEmpty() && value >= 0L) liabilitiesByLedger[id] = value
                }
            }
        }

        val ledgers = ArrayList<LedgerEntity>()
        val transactions = ArrayList<TransactionEntity>()
        val seenLedgerIds = HashSet<String>()
        if (decodedLedger != null && decodedLedger.ledgers.isNotEmpty()) {
            val currentId = decodedLedger.currentLedgerId?.takeIf { candidate ->
                decodedLedger.ledgers.any { it.id == candidate }
            } ?: decodedLedger.ledgers.first().id

            decodedLedger.ledgers.forEachIndexed { index, ledger ->
                seenLedgerIds.add(ledger.id)
                ledgers.add(
                    LedgerEntity(
                        id = ledger.id,
                        name = ledger.name,
                        initialAssetsCents = initialAssetsByLedger[ledger.id] ?: 0L,
                        liabilitiesCents = liabilitiesByLedger[ledger.id] ?: 0L,
                        createdAt = nowMillis,
                        isDefault = index == 0,
                        isCurrent = ledger.id == currentId,
                        sortOrder = index
                    )
                )
            }
            decodedLedger.transactions.forEachIndexed { index, tx ->
                transactions.add(
                    TransactionEntity(
                        id = tx.id,
                        ledgerId = tx.ledgerId,
                        type = tx.type.name,
                        category = tx.category,
                        amountCents = tx.amountInCents,
                        occurredAt = tx.occurredAtMillis,
                        note = tx.note,
                        createdAt = tx.occurredAtMillis,
                        sortOrder = index
                    )
                )
            }
        }
        val orphanAssetLedgerIds = (initialAssetsByLedger.keys + liabilitiesByLedger.keys) - seenLedgerIds
        for (orphanId in orphanAssetLedgerIds) {
            ledgers.add(
                LedgerEntity(
                    id = orphanId,
                    name = orphanId,
                    initialAssetsCents = initialAssetsByLedger[orphanId] ?: 0L,
                    liabilitiesCents = liabilitiesByLedger[orphanId] ?: 0L,
                    createdAt = nowMillis,
                    isDefault = false,
                    isCurrent = false,
                    sortOrder = -1
                )
            )
        }

        // 2. Borrowing records
        val borrowingEntities = if (raw.borrowingRecordsJson.isNullOrBlank()) {
            emptyList()
        } else {
            BorrowingRecordCodec.decode(raw.borrowingRecordsJson).map { rec ->
                BorrowingRecordEntity(
                    id = rec.id,
                    ledgerId = rec.ledgerId,
                    direction = rec.direction.name,
                    counterparty = rec.person,
                    amountCents = rec.amountInCents,
                    occurredAt = rec.occurredAtMillis,
                    note = rec.note,
                    sourceTransactionId = rec.sourceTransactionId,
                    settled = false
                )
            }
        }

        // 3. Savings goals
        val savingsEntities = parseSavingsGoals(raw.savingsGoalsJson)

        // 4. Custom & deleted default categories
        val categoryEntities = parseCategories(raw.categoryEntries)

        // 5. Study notebook
        var studySnapshot: StudyDatabaseSnapshot? = null
        var legacyBeforePlans: String? = null
        var upgradedStudyJson: String? = null
        var unreadableStudyJson: String? = null
        val studyRaw = raw.studyNotebookJson
        if (!studyRaw.isNullOrEmpty()) {
            try {
                val version = JSONObject(studyRaw).optInt("version", 4)
                val notebook = StudyNotebook.fromJson(studyRaw)
                studySnapshot = StudyNotebookSqlBridge.toSnapshot(notebook)
                upgradedStudyJson = notebook.toJson()
                if (version < 3) {
                    legacyBeforePlans = studyRaw
                }
            } catch (_: RuntimeException) {
                unreadableStudyJson = studyRaw
                val fallback = StudyNotebook()
                studySnapshot = StudyNotebookSqlBridge.toSnapshot(fallback)
            } catch (_: JSONException) {
                unreadableStudyJson = studyRaw
                val fallback = StudyNotebook()
                studySnapshot = StudyNotebookSqlBridge.toSnapshot(fallback)
            }
        }

        // 6. Todo items & reminder preferences
        var todoRemindEnabled = true
        var todoDefaultTime = "18:00"
        val todoItems = ArrayList<TodoItemEntity>()
        if (!raw.todoDataJson.isNullOrBlank()) {
            try {
                val root = JSONObject(raw.todoDataJson)
                todoRemindEnabled = root.optBoolean("remindEnabled", true)
                val validatedTime = try {
                    TodoStore.TodoItem.validateTime(root.optString("defaultRemindTime", "18:00"))
                } catch (_: IllegalArgumentException) {
                    "18:00"
                }
                todoDefaultTime = validatedTime.ifEmpty { "18:00" }
                val arr = root.optJSONArray("items")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        try {
                            val validated = TodoStore.TodoItem(
                                obj.getString("id"),
                                obj.getString("title"),
                                obj.optString("note", ""),
                                obj.optString("day", ""),
                                obj.optString("dueTime", ""),
                                obj.optBoolean("remind", false),
                                obj.optInt("remindLead", 30),
                                obj.optLong("created", 0L),
                                obj.optLong("completed", 0L)
                            )
                            todoItems.add(
                                TodoItemEntity(
                                    id = validated.id,
                                    title = validated.title,
                                    note = validated.note,
                                    dueDate = validated.day,
                                    dueTime = validated.dueTime,
                                    remind = validated.remind,
                                    remindBeforeMinutes = validated.remindLeadMinutes,
                                    createdAt = validated.created,
                                    completedAt = validated.completed,
                                    sortOrder = i
                                )
                            )
                        } catch (_: RuntimeException) {
                        }
                    }
                }
            } catch (_: JSONException) {
            }
        }

        // 7. Consolidated Preferences DataStore snapshot
        val prefsSnapshot = AppPreferencesSnapshot(
            nickname = raw.nickname?.takeIf { it.isNotBlank() }
                ?: AppPreferencesSnapshot.DEFAULT_NICKNAME,
            checkedDates = HashSet(raw.checkedDates ?: emptySet()),
            rememberLastCottage = raw.rememberLastCottage ?: false,
            lastCottage = raw.lastCottage ?: "",
            countdownMode = raw.countdownMode ?: false,
            keepScreenOn = raw.keepScreenOn ?: false,
            askedNotifications = raw.askedNotifications ?: false,
            todoRemindEnabled = todoRemindEnabled,
            todoDefaultRemindTime = todoDefaultTime
        )

        return MigratedPayload(
            ledgers = ledgers,
            transactions = transactions,
            borrowingRecords = borrowingEntities,
            savingsGoals = savingsEntities,
            customCategories = categoryEntities,
            studySnapshot = studySnapshot,
            todoItems = todoItems,
            preferences = prefsSnapshot,
            legacyStudyBeforePlansJson = legacyBeforePlans,
            upgradedStudyNotebookJson = upgradedStudyJson,
            unreadableStudyRawJson = unreadableStudyJson
        )
    }

    private fun parseSavingsGoals(json: String?): List<SavingsGoalEntity> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val list = ArrayList<SavingsGoalEntity>()
            var order = 0
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isEmpty() || id == "default-autumn-trip" || id == "default-forest-home") {
                    continue
                }
                val name = item.optString("name", "").trim()
                val target = item.optLong("targetCents", 0L)
                val saved = item.optLong("savedCents", 0L)
                val created = item.optLong("createdAtMillis", 0L)
                if (name.isEmpty() || target <= 0L || saved < 0L || created <= 0L) continue
                list.add(
                    SavingsGoalEntity(
                        id = id,
                        title = name,
                        targetCents = target,
                        savedCents = saved,
                        deadline = "",
                        createdAtMillis = created,
                        sortOrder = order++
                    )
                )
            }
            list
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun parseCategories(entries: Map<String, String>): List<CustomCategoryEntity> {
        val result = ArrayList<CustomCategoryEntity>()
        val seen = HashSet<String>()

        fun appendJsonList(rawJson: String?, type: TransactionType, isDeletedDefault: Boolean) {
            if (rawJson.isNullOrBlank()) return
            try {
                val arr = JSONArray(rawJson)
                var order = 0
                for (i in 0 until arr.length()) {
                    val name = arr.optString(i, "").trim()
                    if (name.isEmpty() || name == "自定义") continue
                    val key = "${type.name}:$name"
                    if (seen.add(key)) {
                        result.add(
                            CustomCategoryEntity(
                                type = type.name,
                                name = name,
                                isDeletedDefault = isDeletedDefault,
                                sortOrder = order++
                            )
                        )
                    }
                }
            } catch (_: JSONException) {
            }
        }

        appendJsonList(entries["deleted_expense"], TransactionType.EXPENSE, isDeletedDefault = true)
        appendJsonList(entries["custom_expense"], TransactionType.EXPENSE, isDeletedDefault = false)
        appendJsonList(entries["deleted_income"], TransactionType.INCOME, isDeletedDefault = true)
        appendJsonList(entries["custom_income"], TransactionType.INCOME, isDeletedDefault = false)
        return result
    }

    private fun writeColdBackup(dir: File, content: String) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("无法创建迁移冷备目录: ${dir.absolutePath}")
        }
        val file = File(dir, BACKUP_FILE_NAME)
        file.writeText(content, Charsets.UTF_8)
    }
}

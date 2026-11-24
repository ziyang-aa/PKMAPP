package com.example.pkmapp.core.database

import android.content.Context
import com.example.pkmapp.core.datastore.AppPreferences
import java.io.File

object PkmStorageBootstrap {
    private const val META_PREFS = "pkm_storage_migration_meta"

    @Volatile
    private var bootstrapped = false

    @Volatile
    private var studyRecoveryWarning = false

    @JvmStatic
    fun takeStudyRecoveryWarning(): Boolean {
        val value = studyRecoveryWarning
        studyRecoveryWarning = false
        return value
    }

    @JvmStatic
    fun ensureInitialized(context: Context) {
        if (bootstrapped) return
        synchronized(this) {
            if (bootstrapped) return
            val appContext = context.applicationContext
            val database = AppDatabase.getRawInstance(appContext)
            val appPreferences = AppPreferences.getRawInstance(appContext)
            val metaPrefs = appContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)

            val env = object : LegacyMigrationEnvironment {
                override fun isMigrationDone(): Boolean =
                    metaPrefs.getBoolean(LegacySharedPrefsMigrator.MIGRATION_MARKER_KEY, false)

                override fun markMigrationDone() {
                    metaPrefs.edit()
                        .putBoolean(LegacySharedPrefsMigrator.MIGRATION_MARKER_KEY, true)
                        .commit()
                }

                override fun readLegacyPreferences(): LegacyRawPreferencesData =
                    readAndroidLegacyPrefs(appContext)

                override fun backupDirectory(): File =
                    File(appContext.filesDir, LegacySharedPrefsMigrator.BACKUP_DIR_NAME)

                override fun onStudyLegacySideEffects(
                    legacyBeforePlansJson: String?,
                    upgradedNotebookJson: String?,
                    unreadableRawJson: String?,
                    nowMillis: Long
                ) {
                    val studyPrefs = appContext.getSharedPreferences(
                        "forest_study_v1",
                        Context.MODE_PRIVATE
                    )
                    if (unreadableRawJson != null) {
                        val saved = studyPrefs.edit()
                            .putString("unreadable_$nowMillis", unreadableRawJson)
                            .commit()
                        if (!saved) {
                            throw IllegalStateException("学习存档无法读取，原始数据仍保留在本机")
                        }
                        studyRecoveryWarning = true
                    } else if (legacyBeforePlansJson != null && upgradedNotebookJson != null) {
                        studyPrefs.edit()
                            .putString("notebook_before_plans", legacyBeforePlansJson)
                            .putString("notebook", upgradedNotebookJson)
                            .commit()
                    }
                }

                override fun commitPayloadAtomically(payload: MigratedPayload) {
                    database.runInTransaction {
                        if (payload.ledgers.isNotEmpty()) {
                            database.ledgerDao().deleteAllLedgers()
                            database.ledgerDao().upsertAll(payload.ledgers)
                        }
                        if (payload.transactions.isNotEmpty()) {
                            database.ledgerDao().deleteAllTransactions()
                            database.ledgerDao().insertTransactions(payload.transactions)
                        }
                        if (payload.borrowingRecords.isNotEmpty()) {
                            database.borrowingDao().replaceAll(payload.borrowingRecords)
                        }
                        if (payload.savingsGoals.isNotEmpty()) {
                            database.savingsGoalDao().replaceAll(payload.savingsGoals)
                        }
                        if (payload.customCategories.isNotEmpty()) {
                            database.categoryDao().clear()
                            database.categoryDao().insertAll(payload.customCategories)
                        }
                        if (payload.studySnapshot != null) {
                            database.studyDao().replaceSnapshot(payload.studySnapshot)
                        }
                        if (payload.todoItems.isNotEmpty()) {
                            database.todoDao().replaceAll(payload.todoItems)
                        }
                    }
                    appPreferences.writeMigratedBlocking(payload.preferences)
                }
            }

            LegacySharedPrefsMigrator.migrateIfNeeded(env)
            bootstrapped = true
        }
    }

    private fun readAndroidLegacyPrefs(appContext: Context): LegacyRawPreferencesData {
        val ledgerPrefs = appContext.getSharedPreferences("ledger_data", Context.MODE_PRIVATE)
        val assetPrefs = appContext.getSharedPreferences("forest_assets", Context.MODE_PRIVATE)
        val borrowingPrefs = appContext.getSharedPreferences("borrowing_records", Context.MODE_PRIVATE)
        val savingsPrefs = appContext.getSharedPreferences("savings_goals", Context.MODE_PRIVATE)
        val categoryPrefs = appContext.getSharedPreferences("record_categories", Context.MODE_PRIVATE)
        val studyPrefs = appContext.getSharedPreferences("forest_study_v1", Context.MODE_PRIVATE)
        val todoPrefs = appContext.getSharedPreferences("forest_todo_v1", Context.MODE_PRIVATE)
        val profilePrefs = appContext.getSharedPreferences("forest_profile", Context.MODE_PRIVATE)
        val homePrefs = appContext.getSharedPreferences("forest_home_prefs", Context.MODE_PRIVATE)
        val studyUiPrefs = appContext.getSharedPreferences("forest_study_ui", Context.MODE_PRIVATE)
        val studyFlagsPrefs = appContext.getSharedPreferences("study_ui", Context.MODE_PRIVATE)

        val assetMap = LinkedHashMap<String, Long>()
        for ((k, v) in assetPrefs.all) {
            if (v is Number) {
                assetMap[k] = v.toLong()
            }
        }

        val categoryMap = LinkedHashMap<String, String>()
        for (key in arrayOf("custom_expense", "custom_income", "deleted_expense", "deleted_income")) {
            categoryPrefs.getString(key, null)?.let { categoryMap[key] = it }
        }

        return LegacyRawPreferencesData(
            ledgerStateJson = ledgerPrefs.getString("state", null),
            assetEntries = assetMap,
            borrowingRecordsJson = borrowingPrefs.getString("records", null),
            savingsGoalsJson = savingsPrefs.getString("goals", null),
            categoryEntries = categoryMap,
            studyNotebookJson = studyPrefs.getString("notebook", null),
            todoDataJson = todoPrefs.getString("data", null),
            nickname = if (profilePrefs.contains("nickname")) {
                profilePrefs.getString("nickname", null)
            } else {
                null
            },
            checkedDates = if (profilePrefs.contains("checked_dates")) {
                HashSet(profilePrefs.getStringSet("checked_dates", emptySet()) ?: emptySet())
            } else {
                null
            },
            rememberLastCottage = if (homePrefs.contains("remember_last_cottage")) {
                homePrefs.getBoolean("remember_last_cottage", false)
            } else {
                null
            },
            lastCottage = if (homePrefs.contains("last_cottage")) {
                homePrefs.getString("last_cottage", "")
            } else {
                null
            },
            countdownMode = if (studyUiPrefs.contains("countdown_mode")) {
                studyUiPrefs.getBoolean("countdown_mode", false)
            } else {
                null
            },
            keepScreenOn = if (studyUiPrefs.contains("keep_screen_on")) {
                studyUiPrefs.getBoolean("keep_screen_on", false)
            } else {
                null
            },
            askedNotifications = if (studyFlagsPrefs.contains("askedNotifications")) {
                studyFlagsPrefs.getBoolean("askedNotifications", false)
            } else {
                null
            }
        )
    }
}

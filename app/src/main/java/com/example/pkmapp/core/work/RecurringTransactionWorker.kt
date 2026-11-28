package com.example.pkmapp.core.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pkmapp.core.database.PkmStorageBootstrap
import com.example.pkmapp.data.InMemoryLedgerRepository
import com.example.pkmapp.recurring.RecurringNotifications
import com.example.pkmapp.recurring.RecurringTransactionEngine
import java.util.concurrent.TimeUnit

/**
 * Periodic worker (every 12 hours) that idempotently posts due auto-recurring transactions
 * and sends a quiet summary notification for newly posted or confirmation-pending bills.
 */
class RecurringTransactionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        PkmStorageBootstrap.ensureInitialized(context)
        InMemoryLedgerRepository.initialize(context)
        val summary = RecurringTransactionEngine.processDueRules(context)
        if (summary.postedCount > 0 || summary.pendingConfirmations.isNotEmpty()) {
            RecurringNotifications.notifySummary(context, summary)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "RecurringTxWorker"
        private const val UNIQUE_WORK_NAME = "pkm_recurring_transactions"

        @JvmStatic
        fun enqueuePeriodic(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(
                    12,
                    TimeUnit.HOURS
                ).build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to enqueue periodic recurring worker", error)
            }
        }
    }
}

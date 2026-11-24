package com.example.pkmapp.core.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pkmapp.core.database.PkmStorageBootstrap
import com.example.pkmapp.study.StudyNotifications
import com.example.pkmapp.todo.TodoNotifications
import java.util.concurrent.TimeUnit

/**
 * Periodic self-healing worker that re-arms study deadline & standalone todo alarms
 * across Doze windows, OEM background kills, and app restarts.
 */
class ReminderRescheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        PkmStorageBootstrap.ensureInitialized(context)
        StudyNotifications.rescheduleAll(context)
        TodoNotifications.rescheduleAll(context)
        return Result.success()
    }

    companion object {
        private const val TAG = "ReminderReschedule"
        private const val UNIQUE_WORK_NAME = "pkm_reminder_reschedule"

        @JvmStatic
        fun enqueuePeriodic(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<ReminderRescheduleWorker>(
                    6,
                    TimeUnit.HOURS
                ).build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to enqueue periodic reminder worker", error)
            }
        }
    }
}

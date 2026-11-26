package com.example.pkmapp.core.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pkmapp.core.security.CryptoSessionStore
import com.example.pkmapp.core.sync.SyncOutcome
import com.example.pkmapp.core.sync.SyncRepository
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return when (SyncRepository(applicationContext).syncNow()) {
            is SyncOutcome.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "pkm_periodic_cloud_sync"
        private const val DEBOUNCED_WORK_NAME = "pkm_debounced_cloud_sync"
        private const val DEBOUNCE_DELAY_SECONDS = 5L

        @JvmStatic
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            try {
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (_: IllegalStateException) {
                // WorkManager may be uninitialized in isolated unit tests.
            }
        }

        /**
         * Debounced silent cloud sync (5 seconds):
         * skips immediately for guest, offline sessions, or unconfigured servers.
         */
        @JvmStatic
        fun scheduleDebouncedSync(context: Context?) {
            val appContext = context?.applicationContext ?: return
            val session = try {
                CryptoSessionStore.get(appContext).currentSession()
            } catch (_: RuntimeException) {
                return
            }
            if (session.isGuest ||
                session.serverBaseUrl.isBlank() ||
                session.accessToken.isBlank() ||
                session.accessToken.startsWith("offline_session_")
            ) {
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(DEBOUNCE_DELAY_SECONDS, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build()
            try {
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    DEBOUNCED_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (_: IllegalStateException) {
                // WorkManager may be uninitialized in isolated unit tests.
            }
        }
    }
}

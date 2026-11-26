package com.example.pkmapp.study

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Foreground service keeping active focus timer notifications & forest white noise alive. */
class FocusTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val periodicRefresh = object : Runnable {
        override fun run() {
            val appContext = applicationContext
            val store = StudyStore.get(appContext)
            val notebook = store.notebook()
            if (!notebook.hasSession() || !StudyNotifications.permitted(appContext)) {
                stopGracefully()
                return
            }
            ForestSoundEngine.syncWithFocusState(appContext, notebook.running())
            val notification = StudyNotifications.buildTimerNotification(appContext, store)
            if (notification != null) {
                val manager = appContext.getSystemService(NotificationManager::class.java)
                try {
                    manager?.notify(StudyNotifications.TIMER_ID, notification)
                } catch (error: SecurityException) {
                    Log.w(TAG, "Notification refresh skipped", error)
                }
            }
            if (notebook.running()) {
                handler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopGracefully()
            return START_NOT_STICKY
        }
        val appContext = applicationContext
        val store = StudyStore.get(appContext)
        val notification = StudyNotifications.buildTimerNotification(appContext, store)
        if (notification == null || !StudyNotifications.permitted(appContext)) {
            stopGracefully()
            return START_NOT_STICKY
        }
        try {
            val serviceType = if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, StudyNotifications.TIMER_ID, notification, serviceType)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to promote FocusTimerService to foreground", error)
            stopSelf()
            return START_NOT_STICKY
        }
        ForestSoundEngine.syncWithFocusState(appContext, store.notebook().running())
        handler.removeCallbacks(periodicRefresh)
        if (store.notebook().running()) {
            handler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MS)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(periodicRefresh)
        ForestSoundEngine.stopAmbient()
        super.onDestroy()
    }

    private fun stopGracefully() {
        handler.removeCallbacks(periodicRefresh)
        ForestSoundEngine.stopAmbient()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "FocusTimerService"
        private const val REFRESH_INTERVAL_MS = 15_000L
        private const val ACTION_SYNC = "com.example.pkmapp.study.FOCUS_SERVICE_SYNC"
        private const val ACTION_STOP = "com.example.pkmapp.study.FOCUS_SERVICE_STOP"

        @JvmStatic
        fun sync(context: Context, store: StudyStore) {
            val appContext = context.applicationContext
            val notebook = store.notebook()
            val running = notebook.hasSession() && notebook.running()
            ForestSoundEngine.syncWithFocusState(appContext, running)
            val active = notebook.hasSession() && StudyNotifications.permitted(appContext)
            val intent = Intent(appContext, FocusTimerService::class.java)
                .setAction(if (active) ACTION_SYNC else ACTION_STOP)
            try {
                if (active) {
                    ContextCompat.startForegroundService(appContext, intent)
                } else {
                    appContext.stopService(intent)
                }
            } catch (error: RuntimeException) {
                Log.w(TAG, "Foreground service sync skipped: ${error.message}")
            }
        }
    }
}

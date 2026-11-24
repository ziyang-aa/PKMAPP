package com.example.pkmapp.study

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Foreground service keeping active focus timer notifications reliable across background kills. */
class FocusTimerService : Service() {

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
        return START_STICKY
    }

    private fun stopGracefully() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "FocusTimerService"
        private const val ACTION_SYNC = "com.example.pkmapp.study.FOCUS_SERVICE_SYNC"
        private const val ACTION_STOP = "com.example.pkmapp.study.FOCUS_SERVICE_STOP"

        @JvmStatic
        fun sync(context: Context, store: StudyStore) {
            val appContext = context.applicationContext
            val active = store.notebook().hasSession() && StudyNotifications.permitted(appContext)
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

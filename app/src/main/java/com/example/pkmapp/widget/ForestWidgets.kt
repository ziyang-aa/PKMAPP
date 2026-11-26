package com.example.pkmapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.pkmapp.MainActivity
import com.example.pkmapp.SplashActivity
import com.example.pkmapp.navigation.AppDestination

/** Helpers for creating splash-skipping widget intents and refreshing pinned home-screen widgets. */
object ForestWidgets {

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun launchPendingIntent(context: Context, destination: AppDestination, requestCode: Int): PendingIntent {
        val intent = Intent(context.applicationContext, SplashActivity::class.java).apply {
            action = "com.example.pkmapp.WIDGET_LAUNCH_${destination.name}"
            putExtra(MainActivity.EXTRA_WIDGET_DESTINATION, destination.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(context.applicationContext, requestCode, intent, FLAGS)
    }

    @JvmStatic
    fun refreshAll(context: Context?) {
        val appContext = context?.applicationContext ?: return
        try {
            val manager = AppWidgetManager.getInstance(appContext) ?: return
            val focusIds = manager.getAppWidgetIds(
                ComponentName(appContext, FocusWidgetProvider::class.java)
            )
            if (focusIds.isNotEmpty()) {
                FocusWidgetProvider.updateWidgets(appContext, manager, focusIds)
            }
            val lifeIds = manager.getAppWidgetIds(
                ComponentName(appContext, LifeBudgetWidgetProvider::class.java)
            )
            if (lifeIds.isNotEmpty()) {
                LifeBudgetWidgetProvider.updateWidgets(appContext, manager, lifeIds)
            }
        } catch (_: RuntimeException) {
            // Safe no-op in isolated JVM tests or restricted contexts.
        }
    }
}

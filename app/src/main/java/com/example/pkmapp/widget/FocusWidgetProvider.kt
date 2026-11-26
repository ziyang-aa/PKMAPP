package com.example.pkmapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.pkmapp.R
import com.example.pkmapp.navigation.AppDestination
import com.example.pkmapp.study.ExamCountdownFormatter
import com.example.pkmapp.study.StudyStore

/** Warm-paper 4x2 home-screen widget for the Study Cabin (jumps directly into Focus room). */
class FocusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateWidgets(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
            val appCtx = context.applicationContext
            val snap = try {
                ExamCountdownFormatter.buildFocusWidgetSnapshot(
                    StudyStore.get(appCtx),
                    System.currentTimeMillis()
                )
            } catch (_: RuntimeException) {
                ExamCountdownFormatter.FocusWidgetSnapshot(
                    planLabel = "学习小屋 · 森林自习室",
                    countdownText = "专注时刻",
                    countdownColor = 0xff315e3a.toInt(),
                    durationText = "今日 0 分钟",
                    subtitleText = "轻点直达专注自习室",
                    ctaText = "开始专注",
                    progressPct = 0
                )
            }

            val openFocus = ForestWidgets.launchPendingIntent(appCtx, AppDestination.STUDY_FOCUS, 9101)
            for (widgetId in widgetIds) {
                val views = RemoteViews(appCtx.packageName, R.layout.widget_focus).apply {
                    setTextViewText(R.id.widget_focus_plan, snap.planLabel)
                    setTextViewText(R.id.widget_focus_countdown, snap.countdownText)
                    setTextColor(R.id.widget_focus_countdown, snap.countdownColor)
                    setTextViewText(R.id.widget_focus_duration, snap.durationText)
                    setTextViewText(R.id.widget_focus_subtitle, snap.subtitleText)
                    setTextViewText(R.id.widget_focus_cta, snap.ctaText)
                    setProgressBar(R.id.widget_focus_progress, 100, snap.progressPct, false)
                    setOnClickPendingIntent(R.id.widget_focus_root, openFocus)
                    setOnClickPendingIntent(R.id.widget_focus_cta, openFocus)
                }
                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}

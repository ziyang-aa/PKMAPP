package com.example.pkmapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.example.pkmapp.R
import com.example.pkmapp.data.InMemoryLedgerRepository
import com.example.pkmapp.data.MonthlyBudgetEvaluator
import com.example.pkmapp.forest.ForestSheetHelper
import com.example.pkmapp.navigation.AppDestination
import com.example.pkmapp.record.MoneyParser

/** Warm-paper 4x2 home-screen widget for the Life Cabin (monthly budget + quick record / details). */
class LifeBudgetWidgetProvider : AppWidgetProvider() {

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
            var ledgerLabel = "生活小屋 · 生活账本"
            var badgeText = "查看明细 ›"
            var badgeColor = 0xff315e3a.toInt()
            var expenseText = "¥0.00"
            var noteText = "本月支出 · 轻点查看明细"
            var progressVisible = false
            var progressPermille = 0

            try {
                InMemoryLedgerRepository.initialize(appCtx)
                val repo = InMemoryLedgerRepository.getInstance()
                val ledger = repo.currentLedger
                val totals = repo.getCurrentMonthTotals(System.currentTimeMillis())
                ledgerLabel = "生活小屋 · ${ledger.name}"
                expenseText = MoneyParser.formatCents(totals.expenseInCents)

                val status = MonthlyBudgetEvaluator.evaluate(
                    ledger.monthlyBudgetCents,
                    totals.expenseInCents
                )
                if (!status.isSet) {
                    badgeText = "未设预算 ›"
                    badgeColor = 0xff7b5c3b.toInt()
                    noteText = "本月支出 · 结余 ${MoneyParser.formatCents(totals.balanceInCents)}"
                    progressVisible = false
                } else {
                    progressVisible = true
                    progressPermille = status.progressPermille
                    when (status.level) {
                        MonthlyBudgetEvaluator.Level.EXCEEDED -> {
                            badgeText = "超支 ${status.usagePercent}%"
                            badgeColor = ForestSheetHelper.TERRACOTTA
                            noteText = "已超支 ${MoneyParser.formatCents(status.overBudgetCents)} / 预算 ${MoneyParser.formatCents(status.budgetCents)}"
                        }
                        MonthlyBudgetEvaluator.Level.WARNING -> {
                            badgeText = "已用 ${status.usagePercent}% · 接近上限"
                            badgeColor = ForestSheetHelper.AMBER
                            noteText = "本月余 ${MoneyParser.formatCents(status.remainingCents)} / 预算 ${MoneyParser.formatCents(status.budgetCents)}"
                        }
                        else -> {
                            badgeText = "已用 ${status.usagePercent}%"
                            badgeColor = 0xff315e3a.toInt()
                            noteText = "本月余 ${MoneyParser.formatCents(status.remainingCents)} / 预算 ${MoneyParser.formatCents(status.budgetCents)}"
                        }
                    }
                }
            } catch (_: RuntimeException) {
            }

            val openDetails = ForestWidgets.launchPendingIntent(appCtx, AppDestination.DETAILS, 9102)
            val openRecord = ForestWidgets.launchPendingIntent(appCtx, AppDestination.RECORD, 9103)

            for (widgetId in widgetIds) {
                val views = RemoteViews(appCtx.packageName, R.layout.widget_life_budget).apply {
                    setTextViewText(R.id.widget_life_ledger, ledgerLabel)
                    setTextViewText(R.id.widget_life_budget_badge, badgeText)
                    setTextColor(R.id.widget_life_budget_badge, badgeColor)
                    setTextViewText(R.id.widget_life_expense, expenseText)
                    setTextViewText(R.id.widget_life_budget_note, noteText)
                    setViewVisibility(
                        R.id.widget_life_budget_progress,
                        if (progressVisible) View.VISIBLE else View.GONE
                    )
                    if (progressVisible) {
                        setProgressBar(R.id.widget_life_budget_progress, 1000, progressPermille, false)
                    }
                    setOnClickPendingIntent(R.id.widget_life_root, openDetails)
                    setOnClickPendingIntent(R.id.widget_life_details_area, openDetails)
                    setOnClickPendingIntent(R.id.widget_life_record_cta, openRecord)
                }
                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}

package com.example.pkmapp.recurring

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pkmapp.MainActivity
import com.example.pkmapp.R
import com.example.pkmapp.SplashActivity
import com.example.pkmapp.navigation.AppDestination
import com.example.pkmapp.record.MoneyParser

object RecurringNotifications {
    private const val CHANNEL_ID = "recurring_bills_v1"
    private const val NOTIFICATION_ID = 8201

    @JvmStatic
    fun notifySummary(context: Context, summary: RecurringTransactionEngine.RunSummary) {
        if (summary.postedCount == 0 && summary.pendingConfirmations.isEmpty()) return
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(appContext)

        val title = when {
            summary.postedCount > 0 && summary.pendingConfirmations.isNotEmpty() ->
                "周期账单已入账 ${summary.postedCount} 笔，另有 ${summary.pendingConfirmations.size} 笔待确认"
            summary.postedCount > 0 ->
                "小松果已自动入账 ${summary.postedCount} 笔周期账单"
            else ->
                "你有 ${summary.pendingConfirmations.size} 笔周期账单待确认"
        }
        val body = when {
            summary.postedCount > 0 -> {
                val names = summary.autoPostedTitles.take(2).joinToString("、")
                val suffix = if (summary.autoPostedTitles.size > 2) " 等" else ""
                "$names$suffix · 共 ${MoneyParser.formatCents(summary.autoPostedAmountCents)}"
            }
            else -> {
                val first = summary.pendingConfirmations.first()
                "${first.rule.title} · ${MoneyParser.formatCents(first.rule.amountCents)}，轻点进入确认"
            }
        }

        val intent = Intent(appContext, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_WIDGET_DESTINATION, AppDestination.RECURRING.name)
        }
        val pi = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_editorial_repeat)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "周期账单提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "固定订阅与周期性收支自动入账或到期确认提醒"
        }
        nm.createNotificationChannel(channel)
    }
}

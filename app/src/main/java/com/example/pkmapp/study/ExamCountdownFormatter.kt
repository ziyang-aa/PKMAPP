package com.example.pkmapp.study

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Computes exam/target countdown labels from StudyPlan.due ("yyyy-MM-dd"). */
object ExamCountdownFormatter {

    data class Countdown(
        val daysRemaining: Long,
        val dueDate: String,
        val focusHeadline: String,
        val homeCompactBadge: String,
        val accentColor: Int
    )

    data class FocusWidgetSnapshot(
        val planLabel: String,
        val countdownText: String,
        val countdownColor: Int,
        val durationText: String,
        val subtitleText: String,
        val ctaText: String,
        val progressPct: Int
    )

    private const val COLOR_PINE = 0xff315e3a.toInt()
    private const val COLOR_AMBER = 0xffb87a2a.toInt()
    private const val COLOR_TERRACOTTA = 0xff9e4c37.toInt()

    @JvmStatic
    fun currentPlanCountdown(notebook: StudyNotebook?, nowMillis: Long): Countdown? {
        val plan = notebook?.currentPlan() ?: return null
        if (plan.archived != 0L) return null
        return evaluate(plan.title, plan.due, nowMillis)
    }

    @JvmStatic
    fun buildFocusWidgetSnapshot(store: StudyStore, nowMillis: Long): FocusWidgetSnapshot {
        val todayKey = StudyNotebook.dayKey(nowMillis)
        val notebook = store.notebook()
        val plan = notebook.currentPlan()
        var todayMillis = 0L
        for (record in notebook.records()) {
            if (todayKey == StudyNotebook.dayKey(record.completedAt)) {
                todayMillis += record.durationMillis
            }
        }
        if (notebook.hasSession()) {
            todayMillis += store.elapsedMillis()
        }
        if (plan == null) {
            return FocusWidgetSnapshot(
                planLabel = "学习小屋 · 森林自习室",
                countdownText = "专注时刻",
                countdownColor = COLOR_PINE,
                durationText = "今日 0 分钟",
                subtitleText = "轻点直达专注自习室",
                ctaText = "开始专注",
                progressPct = 0
            )
        }
        val countdown = if (plan.archived == 0L) evaluate(plan.title, plan.due, nowMillis) else null
        val countdownText = countdown?.homeCompactBadge?.removeSuffix(" ›") ?: "专注时刻"
        val countdownColor = countdown?.accentColor ?: COLOR_PINE
        val dailyGoalMin = kotlin.math.max(1, notebook.dailyGoal())
        val todayMin = todayMillis / 60_000L
        val progressPct = kotlin.math.min(100L, (todayMin * 100L) / dailyGoalMin).toInt()
        val subjectTitle = if (notebook.hasSession()) {
            notebook.activeSubject().title
        } else {
            notebook.selected().title
        }
        val durationText: String
        val subtitleText: String
        val ctaText: String
        if (notebook.hasSession()) {
            durationText = (if (notebook.running()) "专注中 " else "已暂停 ") +
                StudyFormat.clock(store.elapsedMillis())
            subtitleText = "$subjectTitle · 今日累计 ${StudyFormat.duration(todayMillis)}"
            ctaText = if (notebook.running()) "查看计时" else "继续专注"
        } else {
            durationText = if (todayMillis > 0L) {
                "今日 ${StudyFormat.duration(todayMillis)}"
            } else {
                "今日 0 分钟"
            }
            subtitleText = "$subjectTitle · 每日目标 $progressPct%"
            ctaText = "开始专注"
        }
        return FocusWidgetSnapshot(
            planLabel = "学习小屋 · ${plan.title}",
            countdownText = countdownText,
            countdownColor = countdownColor,
            durationText = durationText,
            subtitleText = subtitleText,
            ctaText = ctaText,
            progressPct = progressPct
        )
    }

    @JvmStatic
    fun evaluate(planTitle: String?, dueDate: String?, nowMillis: Long): Countdown? {
        val cleanDue = dueDate?.trim().orEmpty()
        if (cleanDue.isEmpty()) return null
        val days = daysBetween(nowMillis, cleanDue) ?: return null
        val title = planTitle?.trim().orEmpty()
        val shortTitle = if (title.length > 6) title.substring(0, 6) else title.ifEmpty { "目标日" }
        val color = when {
            days <= 7L -> COLOR_TERRACOTTA
            days <= 30L -> COLOR_AMBER
            else -> COLOR_PINE
        }
        val focusHeadline = when {
            days > 0L -> "距考期还有 $days 天 · $cleanDue"
            days == 0L -> "目标日就在今天 · $cleanDue"
            else -> "目标日已过 ${-days} 天 · $cleanDue"
        }
        val homeBadge = when {
            days > 0L -> "距$shortTitle ${days}天 ›"
            days == 0L -> "$shortTitle · 今日 ›"
            else -> "$shortTitle · 已过${-days}天 ›"
        }
        return Countdown(
            daysRemaining = days,
            dueDate = cleanDue,
            focusHeadline = focusHeadline,
            homeCompactBadge = homeBadge,
            accentColor = color
        )
    }

    @JvmStatic
    fun daysBetween(nowMillis: Long, dueDate: String): Long? {
        val dueDayStart = parseUtcMidnight(dueDate) ?: return null
        val todayKey = StudyNotebook.dayKey(nowMillis)
        val todayStart = parseUtcMidnight(todayKey) ?: return null
        return TimeUnit.MILLISECONDS.toDays(dueDayStart - todayStart)
    }

    private fun parseUtcMidnight(yyyyMmDd: String): Long? {
        if (!yyyyMmDd.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return try {
            val date = sdf.parse(yyyyMmDd) ?: return null
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (_: ParseException) {
            null
        }
    }
}

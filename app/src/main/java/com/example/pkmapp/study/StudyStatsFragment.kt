package com.example.pkmapp.study

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.pkmapp.R
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.GREEN
import com.example.pkmapp.forest.ForestUi.LIGHT
import com.example.pkmapp.forest.ForestUi.LINE
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.button
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.heading
import com.example.pkmapp.forest.ForestUi.image
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.navigation.AppDestination
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.roundToInt

class StudyStatsFragment : BaseStudyFragment() {

    internal var days: Int = 7
    internal var statsPlan: String? = null
    private var chart: StudyChartView.Kind = StudyChartView.Kind.RING

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (saved != null) {
            days = saved.getInt("days", 7)
            statsPlan = saved.getString("statsPlan")
            chart = StudyChartView.Kind.valueOf(
                saved.getString("chart", StudyChartView.Kind.RING.name)
            )
        }
        val pendingPlan = sharedViewModel.consumePendingHistoryPlan()
        if (pendingPlan != null) {
            statsPlan = pendingPlan
            days = 0
        }
        sharedViewModel.updateStatsScope(statsPlan, days)
        sharedViewModel.updateStatsChart(chart)
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putInt("days", days)
        state.putString("chart", chart.name)
        state.putString("statsPlan", statsPlan)
    }

    internal fun setStatsScope(plan: String?, period: Int) {
        statsPlan = plan
        days = period
        sharedViewModel.updateStatsScope(plan, period)
        render()
    }

    override fun buildPage(body: LinearLayout) {
        val content = content()
        body.addView(content)
        val notebook = store.notebook()
        val scope = if (statsPlan == null) notebook.currentPlan() else notebook.plan(statsPlan)
        val scopeId = scope?.id
        val initialForRecord = scope ?: notebook.currentPlan()

        setBarTitle("学习足迹")

        val statsActions = row(ctx()).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(10))
            background = bottomHairlineBg(0x1f5c4938)
        }
        val scopeWrap = row(ctx()).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
            setPadding(0, dp(4), dp(8), dp(4))
            background = ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false)
            setOnClickListener { StudyStatsDialogs.showPlanFilterSheet(this@StudyStatsFragment) }
            ForestMotion.bind(this)
        }
        val scopeDot = View(ctx()).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = rounded(ctx(), GREEN, 0, 99)
        }
        scopeWrap.addView(
            scopeDot,
            LinearLayout.LayoutParams(dp(7.5f), dp(7.5f)).apply { marginEnd = dp(8) }
        )
        val scopeLabel = if (scope == null) {
            "所有计划 ▾"
        } else {
            "${scope.title}${if (scope.archived == 0L) "" else " · 已归档"} ▾"
        }
        val scopeButton = StudySheet.link(ctx(), scopeLabel, 0xff243624.toInt()) {
            StudyStatsDialogs.showPlanFilterSheet(this)
        }.apply {
            textSize = 19f
            typeface = Typeface.create("serif", Typeface.BOLD)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            background = null
        }
        scopeWrap.addView(scopeButton, LinearLayout.LayoutParams(-2, -2))
        statsActions.addView(scopeWrap, LinearLayout.LayoutParams(0, -2, 1f))

        if (initialForRecord != null) {
            val manualBtn = StudySheet.link(ctx(), "补记学习", DEEP) {
                val sheet = newSheet()
                workDialogs(sheet).recordForm(initialForRecord, null) {
                    sheet.dismiss()
                    render()
                    toast("已补记学习")
                }
            }.apply {
                textSize = 12.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                minimumHeight = dp(36)
                setPadding(dp(13), dp(6), dp(13), dp(6))
                setCompoundDrawablesRelative(tintedIcon(R.drawable.ic_editorial_pen, 14, DEEP), null, null, null)
                compoundDrawablePadding = dp(5)
                background = ripple(ctx(), rounded(ctx(), 0xfffffefa.toInt(), 0x38315e3a, 999), false)
            }
            statsActions.addView(manualBtn, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        }
        ForestUi.add(content, statsActions, 4)

        val periods = intArrayOf(1, 7, 30, 0)
        val periodSelector = editorialPeriodTabs(
            arrayOf("今天", "近7天", "近30天", "全部"),
            intArrayOf(R.id.study_date_today, R.id.study_date_week, R.id.study_date_month, View.NO_ID),
            when (days) {
                0 -> 3; 30 -> 2; 7 -> 1; else -> 0
            }
        ) { index ->
            if (days == periods[index]) return@editorialPeriodTabs
            days = periods[index]
            sharedViewModel.updateStatsScope(statsPlan, days)
            render()
        }
        ForestUi.add(content, periodSelector, 2)

        val allScoped = notebook.forPlan(scopeId)
        val end = Calendar.getInstance()
        if (days == 0 && allScoped.isNotEmpty()) end.timeInMillis = allScoped[0].completedAt
        end.set(Calendar.HOUR_OF_DAY, 0)
        end.set(Calendar.MINUTE, 0)
        end.set(Calendar.SECOND, 0)
        end.set(Calendar.MILLISECOND, 0)
        end.add(Calendar.DATE, 1)

        val start = end.clone() as Calendar
        if (days == 0 && allScoped.isNotEmpty()) {
            start.timeInMillis = allScoped[allScoped.lastIndex].completedAt
            start.set(Calendar.HOUR_OF_DAY, 0)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
        } else {
            start.add(Calendar.DATE, -max(days, 1))
        }
        val chartDays = if (days > 0) {
            days
        } else {
            max(1, ((end.timeInMillis - start.timeInMillis) / 86_400_000.0).roundToInt())
        }
        val records = if (days == 0) allScoped else notebook.between(scopeId, start.timeInMillis, end.timeInMillis)
        var total = 0L
        val dates = HashSet<String>()
        for (record in records) {
            total += record.durationMillis
            dates.add(StudyNotebook.dayKey(record.completedAt))
        }
        val range = statsRange(start, end, records.isEmpty())
        val streak = StudySharedViewModel.computeStudyStreak(allScoped)
        val avgDaily = if (dates.isEmpty()) 0L else total / dates.size
        ForestUi.add(content, durationSummary(range, total, records.size, dates.size, avgDaily, streak), 14)

        val chartCard = statsPanel(LIGHT, LINE)
        val activeSubjects = HashSet<StudySubject>()
        for (r in records) activeSubjects.add(r.subject)
        val periodTag = when (days) {
            1 -> "今天"; 7 -> "近7天"; 30 -> "近30天"; else -> "全部"
        }
        val chartHeader = row(ctx())
        chartHeader.addView(
            heading(ctx(), if (chart == StudyChartView.Kind.TREND && records.isNotEmpty()) "学习趋势" else "科目分布", 19),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        chartHeader.addView(
            text(ctx(), if (records.isEmpty()) periodTag else "$periodTag · ${activeSubjects.size}门科目", 12, MUTED)
                .apply { fontFeatureSettings = "tnum" }
        )
        chartCard.addView(chartHeader)

        if (records.isNotEmpty()) {
            ForestUi.add(
                chartCard,
                editorialChartTabs(
                    arrayOf("环形占比", "科目时长", "每日趋势"),
                    intArrayOf(R.id.study_chart_ring, R.id.study_chart_bars, R.id.study_chart_trend),
                    chart.ordinal
                ) { index ->
                    val type = StudyChartView.Kind.entries[index]
                    if (chart == type) return@editorialChartTabs
                    chart = type
                    sharedViewModel.updateStatsChart(type)
                    render()
                },
                8
            )
        }
        if (records.isEmpty()) {
            val empty = column(ctx()).apply {
                id = R.id.study_chart_view
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val meadow = image(ctx(), R.drawable.crayon_study_meadow, null).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            empty.addView(meadow, LinearLayout.LayoutParams(-1, dp(86)))
            val message = text(
                ctx(),
                if (allScoped.isEmpty()) "还没有学习记录" else "这段时间没有学习记录",
                14,
                DEEP
            ).apply { gravity = Gravity.CENTER }
            ForestUi.add(empty, message, 2)
            ForestUi.add(chartCard, empty, 6)
        } else {
            val graphic = StudyChartView(ctx()).apply {
                id = R.id.study_chart_view
                setData(records, chart, start.timeInMillis, chartDays)
            }
            ForestUi.add(chartCard, graphic, 8)
        }
        if (records.isEmpty() && allScoped.isNotEmpty()) {
            ForestUi.add(chartCard, StudySheet.link(ctx(), "查看全部时间", DEEP) { setStatsScope(statsPlan, 0) }, 0)
        } else if (records.isEmpty() && scope != null && scope === notebook.currentPlan() && scope.archived == 0L) {
            ForestUi.add(chartCard, StudySheet.link(ctx(), "去专注", DEEP) { open(AppDestination.STUDY_FOCUS) }, 0)
        }
        ForestUi.add(content, chartCard, 14)
        ForestUi.add(content, heatmapCard(allScoped, streak), 14)
        weeklyReview(content, scopeId)
        if (allScoped.isEmpty()) return

        val history = row(ctx())
        history.addView(
            heading(ctx(), if (records.isEmpty()) "学习记录" else "科目汇总", 18),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val allBtn = button(ctx(), "查看全部", false).apply {
            textSize = 12f
            minHeight = dp(48)
            contentDescription = "查看全部学习记录，不限时间"
            setOnClickListener {
                StudyStatsDialogs.showRecordsSheet(
                    this@StudyStatsFragment, scopeId, { store.notebook().forPlan(scopeId) }, "全部时间", null
                )
            }
        }
        history.addView(allBtn)
        ForestUi.add(content, history, 18)
        if (records.isEmpty()) return

        val list = statsPanel(LIGHT, LINE).apply { id = R.id.study_records_container }
        ForestUi.add(content, list, 7)
        subjectSummary(list, records, total) { subject ->
            StudyStatsDialogs.showRecordsSheet(
                this,
                scopeId,
                {
                    if (days == 0) store.notebook().forPlan(scopeId)
                    else store.notebook().between(scopeId, start.timeInMillis, end.timeInMillis)
                },
                range,
                subject
            )
        }
    }

    private fun weeklyReview(content: LinearLayout, scopeId: String?) {
        val end = StudyFormat.day(StudyNotebook.dayKey(System.currentTimeMillis())).apply { add(Calendar.DATE, 1) }
        val start = (end.clone() as Calendar).apply { add(Calendar.DATE, -7) }
        val completed = store.notebook().completedTasks(scopeId, start.timeInMillis, end.timeInMillis)
        val pending = store.notebook().pendingReviews(scopeId)
        val reviewed = store.notebook().completedReviews(scopeId, start.timeInMillis, end.timeInMillis).size
        if (completed.isEmpty() && pending.isEmpty() && reviewed == 0) return
        var summary = "完成 ${completed.size} 项 · 待回顾 ${pending.size} 项"
        if (reviewed > 0) summary += " · 已回顾 $reviewed 项"
        ForestUi.add(
            content,
            StudySheet.item(ctx(), "近7天回顾", summary, R.drawable.ic_study_trail, StudySheet.MOSS, "›", false) {
                StudyStatsDialogs.showWeeklyReviewSheet(this, scopeId)
            },
            12
        )
    }

    private fun editorialPeriodTabs(
        labels: Array<String>,
        ids: IntArray,
        selected: Int,
        change: (Int) -> Unit
    ): LinearLayout {
        val strip = row(ctx()).apply { gravity = Gravity.BOTTOM }
        val indicatorHeight = max(3, (2.5f * ctx().resources.displayMetrics.density).roundToInt())
        for (i in labels.indices) {
            val isSelected = i == selected
            val tab = column(ctx()).apply {
                id = ids[i]
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                minimumHeight = dp(42)
                isClickable = true; isFocusable = true
                this.isSelected = isSelected
                contentDescription = labels[i]
                background = ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 8), false)
            }
            val label = text(ctx(), labels[i], 13, if (isSelected) 0xff274c2f.toInt() else 0xff7a7364.toInt()).apply {
                typeface = Typeface.create(
                    if (isSelected) "sans-serif-medium" else "sans-serif",
                    if (isSelected) Typeface.BOLD else Typeface.NORMAL
                )
                setPadding(dp(4), dp(9), dp(4), dp(8))
            }
            tab.addView(label, LinearLayout.LayoutParams(-2, -2))
            val indicator = View(ctx()).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = rounded(ctx(), if (isSelected) 0xff3b613f.toInt() else Color.TRANSPARENT, 0, 99)
            }
            tab.addView(indicator, LinearLayout.LayoutParams(dp(26), indicatorHeight))
            tab.setOnClickListener { change(i) }
            ForestMotion.bind(tab)
            strip.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return strip
    }

    private fun statsRange(start: Calendar, end: Calendar, empty: Boolean): String {
        if (days == 0 && empty) return "全部时间"
        val last = (end.clone() as Calendar).apply { add(Calendar.DATE, -1) }
        val crossesYear = last.get(Calendar.YEAR) != start.get(Calendar.YEAR)
        val date = SimpleDateFormat(if (crossesYear) "yyyy年M月d日" else "M月d日", Locale.CHINA)
        val first = date.format(start.time)
        val until = date.format(last.time)
        if (days == 1) return "今天 · $until"
        return if (first == until) first else "$first 至 $until"
    }

    private fun heatmapCard(allScoped: List<StudyNotebook.Record>, streak: Int): LinearLayout {
        val card = statsPanel(LIGHT, LINE)
        val top = row(ctx())
        top.addView(heading(ctx(), "专注热力图", 18), LinearLayout.LayoutParams(0, -2, 1f))
        val detailLabel = text(
            ctx(),
            if (streak > 0) "已连续打卡 $streak 天" else "点按格子查看当天时长",
            11,
            DEEP
        )
        top.addView(detailLabel)
        card.addView(top)
        val heatmap = StudyHeatmapView(ctx()).apply {
            setData(allScoped) { detailLabel.text = it }
        }
        ForestUi.add(card, heatmap, 10)
        return card
    }

    private fun durationSummary(
        range: String,
        total: Long,
        sessions: Int,
        activeDays: Int,
        avgDaily: Long,
        streak: Int
    ): LinearLayout {
        val card = statsPanel(0xfff1f6e9.toInt(), 0x24456444).apply { elevation = dp(2).toFloat() }
        val top = row(ctx())
        top.addView(text(ctx(), "累计专注", 13, DEEP), LinearLayout.LayoutParams(0, -2, 1f))
        val icon = ImageView(ctx()).apply {
            setImageResource(R.drawable.ic_study_sprout)
            imageTintList = ColorStateList.valueOf(DEEP)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = rounded(ctx(), 0xfffffefa.toInt(), 0x18456444, 12)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        top.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)))
        card.addView(top)

        val formatted = StudyFormat.duration(total)
        val value = SpannableString(formatted)
        val units = DURATION_UNITS.matcher(formatted)
        while (units.find()) value.setSpan(RelativeSizeSpan(0.55f), units.start(), units.end(), 0)
        val number = heading(ctx(), "", 36).apply {
            text = value
            fontFeatureSettings = "tnum"
        }
        ForestUi.add(card, number, 1)
        ForestUi.add(card, text(ctx(), range, 12, MUTED), 5)
        val line = View(ctx()).apply { setBackgroundColor(0x22456444) }
        card.addView(line, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12) })
        val details = row(ctx()).apply { gravity = Gravity.TOP }
        stat(details, "学习次数", "$sessions 次", 1f)
        stat(details, "学习天数", "$activeDays 天", 1f)
        stat(details, "日均专注", if (avgDaily > 0) StudyFormat.duration(avgDaily) else "0分钟", 1.25f)
        stat(details, "连续打卡", "$streak 天", 1f)
        ForestUi.add(card, details, 11)
        return card
    }

    private fun subjectSummary(
        list: LinearLayout,
        records: List<StudyNotebook.Record>,
        total: Long,
        onSubject: (StudySubject) -> Unit
    ) {
        val durations = LinkedHashMap<StudySubject, Long>()
        for (record in records) {
            durations[record.subject] = (durations[record.subject] ?: 0L) + record.durationMillis
        }
        val subjects = durations.keys.sortedByDescending { durations[it] ?: 0L }
        list.addView(summaryRow(null, "科目", "时长", "占比"))
        for (subject in subjects) {
            list.addView(View(ctx()).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, 1))
            val duration = durations[subject] ?: 0L
            val share = 100.0 * duration / total
            val percent = if (share < 0.1) "<0.1%" else String.format(Locale.CHINA, "%.1f%%", share)
            val time = StudyFormat.duration(duration)
            val rowView = summaryRow(subject, subject.title, time, percent).apply {
                minimumHeight = dp(48)
                background = ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 12), false)
                contentDescription = "${subject.title}，$time，占比 $percent，查看记录"
                isFocusable = true
                setOnClickListener { onSubject(subject) }
                ForestMotion.bind(this)
            }
            list.addView(rowView)
        }
    }

    private fun summaryRow(subject: StudySubject?, label: String, duration: String, percent: String): LinearLayout {
        val cells = row(ctx())
        val vPad = dp(if (subject == null) 8 else 12)
        cells.setPadding(0, vPad, 0, vPad)
        StudyStatsDialogs.addSubjectDot(cells, subject?.color ?: 0)
        val ink = if (subject == null) MUTED else DEEP
        val size = if (subject == null) 11 else 13
        cells.addView(text(ctx(), label, size, ink), LinearLayout.LayoutParams(0, -2, 1f))
        cells.addView(text(ctx(), duration, size, ink).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 0.9f))
        cells.addView(text(ctx(), percent, size, ink).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 0.53f))
        cells.addView(
            text(ctx(), if (subject == null) "" else "›", 18, MUTED).apply {
                gravity = Gravity.END
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(15), -2)
        )
        return cells
    }

    companion object {
        private val DURATION_UNITS: Pattern = Pattern.compile("[小时分钟秒]+")
    }
}

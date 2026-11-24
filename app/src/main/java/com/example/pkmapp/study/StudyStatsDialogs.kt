package com.example.pkmapp.study

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.example.pkmapp.R
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.LINE
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.button
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.dp
import com.example.pkmapp.forest.ForestUi.heading
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.min

internal object StudyStatsDialogs {

    fun showPlanFilterSheet(fragment: StudyStatsFragment) {
        val sheet = fragment.newSheet()
        val pane = fragment.sheetPane("计划范围", null, R.drawable.ic_study_book)
        val notebook = fragment.store.notebook()
        val current = notebook.currentPlan()
        val curStatsPlan = fragment.statsPlan
        if (current != null) {
            addPlanFilter(
                fragment, sheet, pane, "当前计划",
                current.title + if (current.archived != 0L) " · 已归档" else "",
                null, fragment.days, curStatsPlan == null || current.id == curStatsPlan
            )
        }
        addPlanFilter(
            fragment, sheet, pane, "所有计划", null, "", 0,
            "" == curStatsPlan || current == null
        )
        for (plan in notebook.plans()) {
            if (plan === current) continue
            addPlanFilter(
                fragment, sheet, pane, plan.title,
                if (plan.archived == 0L) null else "已归档",
                plan.id, 0, plan.id == curStatsPlan
            )
        }
        sheet.push(pane)
    }

    private fun addPlanFilter(
        fragment: StudyStatsFragment,
        sheet: StudySheet,
        pane: StudySheet.Pane,
        name: String,
        detail: String?,
        plan: String?,
        period: Int,
        selected: Boolean
    ) {
        ForestUi.add(
            pane.body,
            StudySheet.item(
                fragment.ctx(), name, detail, 0,
                if (selected) StudySheet.MOSS else Color.TRANSPARENT,
                if (selected) "✓" else null,
                selected
            ) {
                sheet.dismiss()
                fragment.setStatsScope(plan, period)
            },
            4
        )
    }

    fun showWeeklyReviewSheet(fragment: StudyStatsFragment, scopeId: String?) {
        val sheet = fragment.newSheet()
        val work = fragment.workDialogs(sheet)
        refreshWeeklyReview(fragment, work, sheet, scopeId, false)
    }

    private fun refreshWeeklyReview(
        fragment: StudyStatsFragment,
        work: StudyWorkDialogs,
        sheet: StudySheet,
        scopeId: String?,
        replace: Boolean
    ) {
        val ctx = fragment.ctx()
        val notebook = fragment.store.notebook()
        val today = StudyNotebook.dayKey(System.currentTimeMillis())
        val end = StudyFormat.day(today).apply { add(Calendar.DATE, 1) }
        val start = (end.clone() as Calendar).apply { add(Calendar.DATE, -7) }
        val completed = notebook.completedTasks(scopeId, start.timeInMillis, end.timeInMillis)
        val pending = notebook.pendingReviews(scopeId)
        val pane = work.pane("近7天回顾", null)

        ForestUi.add(pane.body, StudySheet.section(ctx, "待回顾"), 0)
        if (pending.isEmpty()) ForestUi.add(pane.body, fragment.note("回顾可以从一条学习手记发起"), 4)
        for (review in pending) {
            val source = notebook.record(review.recordId)
            val status = if (review.day <= today) "可回顾" else review.day
            ForestUi.add(
                pane.body,
                StudySheet.item(ctx, review.prompt, "${source.subject.title} · $status", 0, 0, "›", false) {
                    openReview(fragment, work, sheet, review, source) {
                        sheet.pop()
                        refreshWeeklyReview(fragment, work, sheet, scopeId, true)
                        fragment.render()
                    }
                },
                4
            )
        }

        ForestUi.add(pane.body, StudySheet.section(ctx, "已完成的事项"), 14)
        if (completed.isEmpty()) ForestUi.add(pane.body, fragment.note("这周还没有勾选完成的事项"), 4)
        for (task in completed) {
            val plan = notebook.plan(task.planId)
            val subject = plan.book(task.subjectId)
            ForestUi.add(pane.body, fragment.note("${task.title} · ${subject?.title ?: plan.title}"), 7)
        }

        val reviewed = notebook.completedReviews(scopeId, start.timeInMillis, end.timeInMillis)
        if (reviewed.isNotEmpty()) {
            ForestUi.add(pane.body, StudySheet.section(ctx, "已完成的回顾"), 14)
            for (review in reviewed) {
                val source = notebook.record(review.recordId)
                ForestUi.add(
                    pane.body,
                    StudySheet.item(ctx, review.prompt, source.subject.title, 0, 0, "›", false) {
                        recordDetail(fragment, sheet, source, {}, false)
                    },
                    4
                )
            }
        }
        if (replace) sheet.replace(pane) else sheet.push(pane)
    }

    private fun openReview(
        fragment: BaseStudyFragment,
        work: StudyWorkDialogs,
        sheet: StudySheet,
        review: StudyWork.Review,
        source: StudyNotebook.Record,
        changed: () -> Unit
    ) {
        val ctx = fragment.ctx()
        val pane = work.pane(source.subject.title, "先试着回答，再打开原手记核对")
        ForestUi.add(pane.body, heading(ctx, review.prompt, 19), 4)
        ForestUi.add(
            pane.body,
            StudySheet.link(ctx, "查看原学习记录", DEEP) {
                recordDetail(fragment, sheet, fragment.store.notebook().record(review.recordId), {}, false)
            },
            6
        )
        pane.primary("已掌握，完成回顾") {
            work.save {
                fragment.store.update { n ->
                    n.changeReview(source.id, review.prompt, review.day, true, System.currentTimeMillis())
                    null
                }
                changed()
            }
        }
        pane.secondary("再约一次") {
            work.reviewForm(source, review) {
                sheet.pop()
                changed()
            }
        }
        sheet.push(pane)
    }

    private fun recordDetail(
        fragment: BaseStudyFragment,
        sheet: StudySheet,
        record: StudyNotebook.Record,
        changed: () -> Unit,
        editable: Boolean
    ) {
        sheet.push(recordPane(fragment, sheet, record, changed, editable))
    }

    private fun recordPane(
        fragment: BaseStudyFragment,
        sheet: StudySheet,
        record: StudyNotebook.Record,
        changed: () -> Unit,
        editable: Boolean
    ): StudySheet.Pane {
        val ctx = fragment.ctx()
        val pane = fragment.sheetPane(
            record.subject.title,
            fragment.store.notebook().plan(record.planId).title,
            0
        )
        StudySheet.addHairline(pane.body, 0, 14)
        val durHeading = heading(ctx, StudyFormat.duration(record.durationMillis), 30).apply {
            fontFeatureSettings = "tnum"
        }
        pane.body.addView(durHeading)
        ForestUi.add(
            pane.body,
            fragment.note(
                SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(record.completedAt)) +
                    recordOrigin(record)
            ),
            6
        )
        StudySheet.addHairline(pane.body, 14, 0)
        val task = fragment.store.notebook().task(record.taskId)
        if (task != null) ForestUi.add(pane.body, fragment.note("学习事项 · ${task.title}"), 12)
        if (record.note.isNotEmpty()) {
            ForestUi.add(pane.body, StudySheet.section(ctx, "学习手记"), 16)
            val entry = text(ctx, record.note, 14, DEEP).apply {
                setLineSpacing(fragment.dp(5).toFloat(), 1f)
            }
            ForestUi.add(pane.body, entry, 6)
        }
        if (editable) {
            val work = fragment.workDialogs(sheet)
            val refresh = {
                sheet.pop()
                sheet.replace(recordPane(fragment, sheet, fragment.store.notebook().record(record.id), changed, true))
                changed()
                fragment.render()
            }
            pane.secondary("编辑记录") {
                work.recordForm(fragment.store.notebook().plan(record.planId), record, refresh)
            }
            if (record.note.isNotEmpty()) {
                val review = fragment.store.notebook().review(record.id)
                val label = when {
                    review == null -> "加入待回顾"
                    review.completed == 0L -> "查看待回顾"
                    else -> "再次安排回顾"
                }
                if (review != null && review.completed == 0L) {
                    ForestUi.add(pane.body, fragment.note("回顾日期 · ${review.day}"), 12)
                }
                pane.primary(label) {
                    if (review != null && review.completed == 0L) {
                        openReview(fragment, work, sheet, review, record, refresh)
                    } else {
                        work.reviewForm(record, review, refresh)
                    }
                }
            }
        }
        return pane
    }

    private fun recordOrigin(record: StudyNotebook.Record): String = when {
        record.manual -> " · 手动补记"
        record.adjusted -> " · 时长已修正"
        else -> ""
    }

    fun showRecordsSheet(
        fragment: StudyStatsFragment,
        planId: String?,
        source: () -> List<StudyNotebook.Record>,
        range: String,
        initialSubject: StudySubject?
    ) {
        val all = source()
        if (all.isEmpty()) {
            fragment.toast("还没有学习记录，从第一段专注开始吧")
            return
        }
        val ctx = fragment.ctx()
        val sheet = fragment.newSheet()
        val scope = if (planId == null) "所有计划" else fragment.store.notebook().plan(planId).title
        val pane = fragment.sheetPane(
            if (initialSubject == null) "全部学习记录" else "学习记录",
            "$scope · $range",
            R.drawable.ic_study_trail
        )
        val initialRecords = recordsForSubject(all, initialSubject)
        val toolbar = row(ctx)
        val filter = button(ctx, "${initialSubject?.title ?: "全部科目"} ▾", false).apply { textSize = 12f }
        toolbar.addView(filter, LinearLayout.LayoutParams(0, -2, 1f))
        val count = text(ctx, "${initialRecords.size} 次", 11, MUTED).apply { gravity = Gravity.END }
        toolbar.addView(count, LinearLayout.LayoutParams(-2, -2).apply { marginStart = fragment.dp(12) })
        pane.above.addView(toolbar)

        val search = EditText(ctx).apply {
            isSingleLine = true
            hint = "搜索学习手记"
            contentDescription = "搜索学习手记"
        }
        StudySheet.styleField(search)
        ForestUi.add(pane.above, search, 8)
        val empty = fragment.note("没有符合条件的学习记录").apply { visibility = View.GONE }
        ForestUi.add(pane.above, empty, 6)

        val dateFmt = SimpleDateFormat("yyyy/MM/dd · HH:mm", Locale.CHINA)
        val adapter = object : ArrayAdapter<StudyNotebook.Record>(ctx, 0, ArrayList(initialRecords)) {
            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val cell = (recycled as? HistoryRow) ?: HistoryRow(parent.context)
                val record = getItem(position)!!
                var timestamp = dateFmt.format(Date(record.completedAt))
                if (planId == null) timestamp += " · ${fragment.store.notebook().plan(record.planId).title}"
                cell.bind(record, timestamp + recordOrigin(record))
                return cell
            }
        }
        val entries = ListView(ctx).apply {
            divider = ColorDrawable(LINE)
            dividerHeight = 1
            selector = rounded(ctx, 0x2291aa78, 0, 10)
            this.adapter = adapter
        }
        val maxRowsHeight = fragment.dp(ctx.resources.configuration.screenHeightDp * 0.52f)
        pane.listBody(entries, min(maxRowsHeight, fragment.dp(68) * min(all.size, 10)))

        var selection: StudySubject? = initialSubject
        val updateList = {
            val query = StudyWorkDialogs.value(search).lowercase(Locale.ROOT)
            val filtered = recordsForSubject(source(), selection).toMutableList()
            if (query.isNotEmpty()) {
                filtered.removeIf { !it.note.lowercase(Locale.ROOT).contains(query) }
            }
            adapter.setNotifyOnChange(false)
            adapter.clear()
            adapter.addAll(filtered)
            adapter.notifyDataSetChanged()
            count.text = "${adapter.count} 次"
            empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
        search.addTextChangedListener(StudyTodoDialogs.simpleWatcher {
            updateList()
            entries.setSelection(0)
        })
        entries.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let { recordDetail(fragment, sheet, it, updateList, true) }
        }
        filter.setOnClickListener {
            val recorded = LinkedHashMap<StudySubject, String>()
            for (record in source()) {
                recorded.putIfAbsent(record.subject, fragment.store.notebook().plan(record.planId).title)
            }
            val subjectOptions = ArrayList<StudySubject?>(recorded.size + 1).apply {
                add(null)
                addAll(recorded.keys)
            }
            val options = fragment.sheetPane("按科目筛选", null, R.drawable.ic_study_book)
            for (subject in subjectOptions) {
                val label = subject?.title ?: "全部科目"
                val selected = selection == subject
                val contextText = if (subject == null || planId != null) null else recorded[subject]
                ForestUi.add(
                    options.body,
                    StudySheet.item(
                        ctx, label, contextText, 0,
                        if (selected) StudySheet.MOSS else Color.TRANSPARENT,
                        if (selected) "✓" else null,
                        selected
                    ) {
                        selection = subject
                        updateList()
                        filter.text = "$label ▾"
                        sheet.pop()
                        entries.setSelection(0)
                    },
                    4
                )
            }
            sheet.push(options)
        }
        sheet.push(pane)
    }

    private fun recordsForSubject(
        source: List<StudyNotebook.Record>,
        subject: StudySubject?
    ): List<StudyNotebook.Record> =
        if (subject == null) ArrayList(source) else source.filter { it.subject == subject }

    internal fun addSubjectDot(row: LinearLayout, color: Int): View {
        val context = row.context
        val dot = View(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = rounded(context, color, 0, 5)
        }
        val params = LinearLayout.LayoutParams(dp(context, 9f), dp(context, 9f)).apply {
            marginEnd = dp(context, 11f)
        }
        row.addView(dot, params)
        return dot
    }

    private class HistoryRow(context: Context) : LinearLayout(context) {
        private val dot: View
        private val subject: TextView
        private val date: TextView
        private val duration: TextView

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 59f)
            setPadding(dp(context, 2f), dp(context, 9f), dp(context, 2f), dp(context, 9f))
            dot = addSubjectDot(this, 0)
            val copy = column(context)
            subject = text(context, "", 13, DEEP)
            date = text(context, "", 11, MUTED)
            copy.addView(subject)
            ForestUi.add(copy, date, 5)
            addView(copy, LayoutParams(0, -2, 1f))
            duration = text(context, "", 12, DEEP).apply { gravity = Gravity.END }
            addView(duration, LayoutParams(-2, -2).apply { marginStart = dp(context, 8f) })
        }

        fun bind(record: StudyNotebook.Record, timestamp: String) {
            dot.background = rounded(context, record.subject.color, 0, 5)
            subject.text = record.subject.title
            date.text = timestamp
            duration.text = StudyFormat.duration(record.durationMillis)
        }
    }
}

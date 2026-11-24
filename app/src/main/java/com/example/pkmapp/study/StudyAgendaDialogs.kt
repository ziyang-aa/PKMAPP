package com.example.pkmapp.study

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.WOOD
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.study.BaseStudyFragment.Companion.formatTodoDayHeader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

internal object StudyAgendaDialogs {

    private fun planRemindSubtitle(plan: StudyPlan): String =
        if (!plan.remindDeadlines) "已关闭 · 开启后在截止前发送森林自习提醒"
        else "已开启 · ${plan.remindTime} 发送森林自习提醒"

    fun showAgendaSheet(fragment: StudyFocusFragment) {
        val plan = fragment.store.notebook().currentPlan()
        if (plan == null || plan.archived != 0L) return
        val ctx = fragment.ctx()
        val sheet = fragment.newSheet()
        val work = fragment.workDialogs(sheet)
        val pane = work.pane("今天安排", plan.title)
        val refresh = {
            sheet.dismiss()
            fragment.render()
            showAgendaSheet(fragment)
        }
        pane.primary("添加事项") { work.taskForm(plan, plan.book(plan.selected), null, refresh, null) }
        pane.secondary("设置科目日期") { chooseDeadline(work, sheet, fragment, plan, refresh) }

        StudySheet.addHairline(pane.body, 0, 2)
        val remind = ForestUi.flatSwitchRow(ctx, "考试与截止前提醒", planRemindSubtitle(plan)).apply {
            isChecked = plan.remindDeadlines
        }
        remind.setOnClickListener {
            fragment.perform {
                fragment.store.update { n -> n.remindDeadlines(plan.id, remind.isChecked); null }
                ForestUi.updateFlatSwitchSubtitle(remind, planRemindSubtitle(plan))
                if (remind.isChecked) fragment.allowStudyNotifications()
            }
        }
        pane.body.addView(remind, LinearLayout.LayoutParams(-1, -2))

        val timeRow = row(ctx).apply {
            minimumHeight = fragment.dp(44)
            setPadding(fragment.dp(2), fragment.dp(6), fragment.dp(2), fragment.dp(6))
            background = ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 10), false)
        }
        timeRow.addView(text(ctx, "提醒时间设置", 13, 0xff5b5447.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
        timeRow.addView(text(ctx, "${plan.remindTime} ›", 13, 0xff315e3a.toInt()).apply { fontFeatureSettings = "tnum" })
        timeRow.setOnClickListener { showPlanReminderSettingsDialog(fragment, plan, refresh) }
        ForestMotion.bind(timeRow)
        pane.body.addView(timeRow, LinearLayout.LayoutParams(-1, -2))

        if (plan.remindDeadlines && !StudyNotifications.permitted(ctx)) {
            ForestUi.add(pane.body, StudySheet.link(ctx, "开启系统通知", DEEP) { fragment.allowStudyNotifications() }, 2)
        }

        val tasks = fragment.store.notebook().pendingTasks(plan.id)
        val todayKey = StudyNotebook.dayKey(System.currentTimeMillis())
        val tomorrowKey = StudyNotebook.dayKey(Calendar.getInstance().apply { add(Calendar.DATE, 1) }.timeInMillis)
        val yesterdayKey = StudyNotebook.dayKey(Calendar.getInstance().apply { add(Calendar.DATE, -1) }.timeInMillis)

        var filterMode = 0
        val filterHost = column(ctx)
        val taskListContainer = column(ctx)

        lateinit var renderTasks: () -> Unit
        renderTasks = renderTasks@{
            filterHost.removeAllViews()
            filterHost.addView(
                fragment.editorialChartTabs(
                    arrayOf("今天", "计划", "已完成"),
                    intArrayOf(View.NO_ID, View.NO_ID, View.NO_ID),
                    filterMode
                ) { idx ->
                    if (filterMode != idx) {
                        filterMode = idx
                        renderTasks()
                    }
                }
            )
            taskListContainer.removeAllViews()
            if (filterMode == 2) {
                val doneTasks = fragment.store.notebook().completedTasks(plan.id, 0, Long.MAX_VALUE)
                if (doneTasks.isEmpty()) {
                    ForestUi.add(taskListContainer, fragment.note("还没有已完成的事项"), 10)
                    return@renderTasks
                }
                val doneByDay = LinkedHashMap<String, MutableList<StudyWork.Task>>()
                for (dt in doneTasks) {
                    val dKey = if (dt.completed > 0) StudyNotebook.dayKey(dt.completed) else todayKey
                    doneByDay.getOrPut(dKey) { ArrayList() }.add(dt)
                }
                val hm = SimpleDateFormat("HH:mm", Locale.CHINA)
                for ((dayKey, list) in doneByDay) {
                    val dayHd = text(
                        ctx,
                        formatTodoDayHeader(dayKey, todayKey, tomorrowKey, yesterdayKey, true),
                        12,
                        WOOD
                    ).apply { typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) }
                    ForestUi.add(taskListContainer, dayHd, 12)
                    for (doneTask in list) {
                        val subj = plan.book(doneTask.subjectId)
                        val sub = (if (doneTask.completed > 0) "${hm.format(Date(doneTask.completed))} 完成" else "已完成") +
                            (if (subj == null) "" else " · ${subj.title}")
                        val cb = ForestUi.flatCheckWithSubtitle(ctx, doneTask.title, sub).apply { isChecked = true }
                        cb.setOnClickListener {
                            fragment.perform {
                                fragment.store.update { n ->
                                    n.changeTask(
                                        doneTask.id, doneTask.title, doneTask.day,
                                        doneTask.dueTime, doneTask.remind, doneTask.remindLeadMinutes,
                                        false, System.currentTimeMillis()
                                    )
                                    null
                                }
                                refresh()
                            }
                        }
                        taskListContainer.addView(cb, LinearLayout.LayoutParams(-1, -2))
                    }
                }
                return@renderTasks
            }

            val filtered = tasks.filter { t ->
                val isTodayOrOverdue = t.day.isNotEmpty() && t.day <= todayKey
                if (filterMode == 0) isTodayOrOverdue else !isTodayOrOverdue
            }
            if (filtered.isEmpty()) {
                ForestUi.add(
                    taskListContainer,
                    fragment.note(if (filterMode == 0) "今天暂时没有待做事项" else "暂无后续安排"),
                    10
                )
                return@renderTasks
            }
            val grouped = groupTasksByDate(filtered, todayKey, tomorrowKey)
            for ((header, groupList) in grouped) {
                val dateHeader = text(
                    ctx, "$header · ${groupList.size} 项", 12,
                    if (header.startsWith("已逾期")) 0xff9e4c37.toInt() else WOOD
                ).apply { typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) }
                ForestUi.add(taskListContainer, dateHeader, 12)
                var rowIdx = 0
                for (task in groupList) {
                    if (rowIdx++ > 0) StudySheet.addHairline(taskListContainer, 0, 0)
                    val subject = plan.book(task.subjectId)
                    val detail = formatTaskMeta(subject, task, todayKey, tomorrowKey)
                    val start: Runnable? = if (fragment.store.notebook().hasSession()) {
                        null
                    } else {
                        Runnable { fragment.perform { fragment.startTask(task.id) } }
                    }
                    taskListContainer.addView(
                        StudySheet.item(ctx, task.title, detail, 0, Color.TRANSPARENT, "›", false) {
                            work.taskForm(plan, subject, task, refresh, start)
                        },
                        LinearLayout.LayoutParams(-1, -2)
                    )
                }
            }
        }

        ForestUi.add(pane.body, filterHost, 8)
        ForestUi.add(pane.body, taskListContainer, 2)
        renderTasks()

        StudySheet.addHairline(pane.body, 14, 14)
        pane.body.addView(StudySheet.section(ctx, "近期考试与截止"))
        val deadlines = StudyWorkDialogs.deadlines(plan)
        if (deadlines.isEmpty()) ForestUi.add(pane.body, fragment.note("还没有设置近期日期"), 6)
        var dlIdx = 0
        for (subject in deadlines) {
            if (dlIdx++ > 0) StudySheet.addHairline(pane.body, 0, 0)
            val detail = subject.due + if (subject.assessment.isEmpty()) "" else " · ${subject.assessment}"
            pane.body.addView(
                StudySheet.item(ctx, subject.title, detail, 0, Color.TRANSPARENT, "›", false) {
                    work.taskForm(plan, subject, null, refresh, null)
                },
                LinearLayout.LayoutParams(-1, -2)
            )
        }
        sheet.push(pane)
    }

    private fun chooseDeadline(
        work: StudyWorkDialogs,
        sheet: StudySheet,
        fragment: BaseStudyFragment,
        plan: StudyPlan,
        changed: () -> Unit
    ) {
        val picker = work.pane("设置科目日期", plan.title)
        for (subject in plan.books()) {
            ForestUi.add(
                picker.body,
                StudySheet.item(fragment.ctx(), subject.title, subject.due.ifEmpty { "未设置" }, 0, 0, "›", false) {
                    val editor = work.pane(subject.title, subject.assessment)
                    val day = work.chooseDate(editor.body, "考试或提交日", subject.due, true)
                    editor.primary("保存日期") {
                        work.save {
                            fragment.store.update { n -> n.setDeadline(plan.id, subject.id, day.day); null }
                            changed()
                        }
                    }
                    sheet.push(editor)
                },
                4
            )
        }
        sheet.push(picker)
    }

    private fun showPlanReminderSettingsDialog(
        fragment: BaseStudyFragment,
        plan: StudyPlan,
        afterSave: (() -> Unit)?
    ) {
        val ctx = fragment.ctx()
        val sheet = fragment.newSheet()
        val pane = fragment.sheetPane("提醒设置", null, 0)

        val initialTime = plan.remindTime
        val presets = arrayOf("09:00", "12:00", "18:00", "20:30")
        var customMode = presets.none { it == initialTime }
        var selectedTime = initialTime
        StudySheet.addHairline(pane.body, 0, 2)

        val masterSwitch = ForestUi.flatSwitchRow(ctx, "考试与截止前提醒", planRemindSubtitle(plan)).apply {
            isChecked = plan.remindDeadlines
        }
        pane.body.addView(masterSwitch, LinearLayout.LayoutParams(-1, -2))

        val detailsSection = column(ctx)
        StudySheet.addHairline(detailsSection, 2, 14)

        val timeHeader = row(ctx)
        timeHeader.addView(
            text(ctx, "默认提醒时间", 14, 0xff243624.toInt()).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val timeDisplay = text(ctx, initialTime, 14, 0xff315e3a.toInt()).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); fontFeatureSettings = "tnum"
        }
        timeHeader.addView(timeDisplay)
        detailsSection.addView(timeHeader, LinearLayout.LayoutParams(-1, -2))

        val presetRow = row(ctx)
        val timeChips = ArrayList<TextView>(5)
        val chipLabels = arrayOf("09:00", "12:00", "18:00", "20:30", "自定")
        val customInput = EditText(ctx).apply {
            isSingleLine = true; setText(initialTime); hint = "输入时刻，如 18:00"
            contentDescription = "每日提醒时间"; fontFeatureSettings = "tnum"
        }
        StudySheet.styleQuietInlineField(customInput)

        val syncUi = {
            val on = masterSwitch.isChecked
            detailsSection.visibility = if (on) View.VISIBLE else View.GONE
            val cur = selectedTime.ifEmpty { initialTime }
            timeDisplay.text = cur
            ForestUi.updateFlatSwitchSubtitle(
                masterSwitch,
                if (on) "截止前一天 $cur 发送森林自习提醒" else "已关闭，开启后在截止前自动提醒"
            )
            customInput.visibility = if (customMode) View.VISIBLE else View.GONE
            for (i in chipLabels.indices) {
                StudySheet.styleChoiceChip(timeChips[i], if (customMode) i == chipLabels.lastIndex else chipLabels[i] == cur)
            }
        }
        for (i in chipLabels.indices) {
            val lbl = chipLabels[i]
            val chip = StudySheet.link(ctx, lbl, DEEP) {
                if ("自定" == lbl) {
                    customMode = true
                    val typed = customInput.text.toString().trim()
                    if (typed.isNotEmpty()) selectedTime = typed
                    syncUi()
                    customInput.requestFocus()
                } else {
                    customMode = false; selectedTime = lbl; customInput.setText(lbl); syncUi()
                }
            }.apply {
                textSize = 12f; fontFeatureSettings = "tnum"; minimumHeight = fragment.dp(36)
                setPadding(fragment.dp(4), fragment.dp(5), fragment.dp(4), fragment.dp(5))
            }
            presetRow.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = fragment.dp(6) })
            timeChips.add(chip)
        }
        ForestUi.add(detailsSection, presetRow, 12)
        ForestUi.add(detailsSection, customInput, 6)

        customInput.addTextChangedListener(StudyTodoDialogs.simpleWatcher { s ->
            if (customMode) {
                selectedTime = s.trim()
                val cur = selectedTime.ifEmpty { initialTime }
                timeDisplay.text = cur
                if (masterSwitch.isChecked) ForestUi.updateFlatSwitchSubtitle(masterSwitch, "截止前一天 $cur 发送森林自习提醒")
            }
        })
        StudySheet.addHairline(detailsSection, 14, 2)

        val testRow = row(ctx).apply {
            minimumHeight = fragment.dp(48)
            setPadding(fragment.dp(2), fragment.dp(8), fragment.dp(2), fragment.dp(8))
            background = ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 10), false)
        }
        testRow.addView(
            text(ctx, "发送一条测试通知", 13, 0xff456444.toInt()).apply { typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val testStatus = text(ctx, "验证提醒通道 ›", 12, 0xff8f8675.toInt())
        testRow.addView(testStatus)
        testRow.setOnClickListener {
            fragment.allowStudyNotifications()
            val sent = StudyNotifications.sendImmediateReminder(
                ctx, plan.id, "森林自习室 · 待办与截止提醒", "提醒功能已就绪！将在待办截止时间前自动通知你。"
            )
            testStatus.text = if (sent) "已发送测试通知 ✓" else "请先开启系统通知"
            testStatus.setTextColor(if (sent) 0xff315e3a.toInt() else 0xff9e4c37.toInt())
            fragment.toast(if (sent) "已发送测试提醒通知，请查看顶部通知栏" else "请先允许应用发送系统通知")
        }
        ForestMotion.bind(testRow)
        detailsSection.addView(testRow, LinearLayout.LayoutParams(-1, -2))

        pane.body.addView(detailsSection, LinearLayout.LayoutParams(-1, -2))
        masterSwitch.setOnCheckedChangeListener { _, _ -> syncUi() }
        syncUi()

        pane.primary("保存设置") {
            fragment.perform {
                val rawTime = if (customMode) customInput.text.toString() else selectedTime
                val clean = StudyWork.Task.validateTime(rawTime)
                if (clean.isEmpty()) throw IllegalArgumentException("请输入时间，例如 18:00")
                fragment.store.update { n ->
                    n.remindDeadlines(plan.id, masterSwitch.isChecked)
                    n.setRemindTime(plan.id, clean)
                    null
                }
                if (masterSwitch.isChecked) fragment.allowStudyNotifications()
                sheet.dismiss()
                afterSave?.invoke()
            }
        }
        sheet.push(pane)
    }

    private fun groupTasksByDate(
        tasks: List<StudyWork.Task>,
        todayKey: String,
        tomorrowKey: String
    ): Map<String, List<StudyWork.Task>> {
        val groups = LinkedHashMap<String, MutableList<StudyWork.Task>>()
        val sorted = tasks.sortedWith { a, b ->
            val da = a.day.ifEmpty { "9999-99-99" }
            val db = b.day.ifEmpty { "9999-99-99" }
            val cmp = da.compareTo(db)
            if (cmp != 0) cmp else {
                val ta = a.dueTime.ifEmpty { "99:99" }
                val tb = b.dueTime.ifEmpty { "99:99" }
                val tcmp = ta.compareTo(tb)
                if (tcmp != 0) tcmp else b.priority.compareTo(a.priority)
            }
        }
        for (task in sorted) {
            val header = when {
                task.day.isEmpty() -> "日常待办 · 未限日期"
                task.day < todayKey -> "已逾期 · ${task.day}"
                task.day == todayKey -> "今天 · $todayKey"
                task.day == tomorrowKey -> "明天 · $tomorrowKey"
                else -> "日期 · ${task.day}"
            }
            groups.getOrPut(header) { ArrayList() }.add(task)
        }
        return groups
    }

    private fun formatTaskMeta(
        subject: StudySubject?,
        task: StudyWork.Task,
        todayKey: String,
        tomorrowKey: String
    ): String {
        val sb = StringBuilder()
        if (subject != null) sb.append(subject.title)
        if (task.day.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append(" · ")
            sb.append(
                when (task.day) {
                    todayKey -> "今天"
                    tomorrowKey -> "明天"
                    else -> task.day
                }
            )
        }
        if (task.dueTime.isNotEmpty()) sb.append(" ").append(task.dueTime).append(" 截止")
        if (task.remind) sb.append(" · ").append(task.leadLabel()).append("提醒")
        return sb.toString()
    }
}

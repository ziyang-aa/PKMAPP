package com.example.pkmapp.study

import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.example.pkmapp.forest.ForestCalendarView
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.navigation.AppDestination
import com.example.pkmapp.study.BaseStudyFragment.Companion.shortMonthDay
import com.example.pkmapp.todo.TodoNotifications
import com.example.pkmapp.todo.TodoStore
import java.util.Calendar
import java.util.LinkedHashMap

internal object StudyTodoDialogs {

    fun showDateFilterSheet(
        fragment: StudyTodoFragment,
        todos: TodoStore,
        onFilterChanged: () -> Unit
    ) {
        val ctx = fragment.ctx()
        val sheet = fragment.newSheet()
        val pane = fragment.sheetPane("按日期筛选", null, 0)
        val todayKey = TodoStore.dayKey(System.currentTimeMillis())

        val markers = LinkedHashMap<String, Int>()
        val pendingCounts = LinkedHashMap<String, Int>()
        val doneCounts = LinkedHashMap<String, Int>()
        for (item in todos.pending()) {
            if (item.day.isEmpty()) continue
            val flag = if (item.day < todayKey) ForestCalendarView.MARKER_OVERDUE else ForestCalendarView.MARKER_PENDING
            markers[item.day] = (markers[item.day] ?: 0) or flag
            pendingCounts[item.day] = (pendingCounts[item.day] ?: 0) + 1
        }
        for (item in todos.completed()) {
            val doneDay = if (item.completed > 0) TodoStore.dayKey(item.completed) else item.day
            if (doneDay.isEmpty()) continue
            markers[doneDay] = (markers[doneDay] ?: 0) or ForestCalendarView.MARKER_DONE
            doneCounts[doneDay] = (doneCounts[doneDay] ?: 0) + 1
        }

        StudySheet.addHairline(pane.body, 0, 8)
        val previewDate = text(ctx, "", 13, 0xff243624.toInt()).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        val previewStats = text(ctx, "", 12, MUTED).apply { fontFeatureSettings = "tnum" }

        val currentFilterDay = fragment.todoFilterDay
        var activeKey = if (!currentFilterDay.isNullOrEmpty()) currentFilterDay else todayKey
        val syncPreview = {
            previewDate.text = ForestCalendarView.formatChineseFull(activeKey)
            val pCount = pendingCounts[activeKey] ?: 0
            val dCount = doneCounts[activeKey] ?: 0
            if (pCount == 0 && dCount == 0) {
                previewStats.text = "暂无待办或完成记录"
                previewStats.setTextColor(MUTED)
            } else {
                val sb = StringBuilder()
                if (pCount > 0) sb.append(pCount).append(" 项待办")
                if (dCount > 0) {
                    if (sb.isNotEmpty()) sb.append(" · ")
                    sb.append(dCount).append(" 项已完成")
                }
                previewStats.text = sb.toString()
                previewStats.setTextColor(if (pCount > 0 && activeKey < todayKey) 0xff9e4c37.toInt() else DEEP)
            }
        }

        val calendar = ForestCalendarView(ctx, activeKey, markers) { dayKey ->
            activeKey = dayKey
            syncPreview()
        }
        pane.body.addView(calendar, LinearLayout.LayoutParams(-1, -2))

        val legend = row(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        addLegendDot(fragment, legend, 0xff456444.toInt(), "待办", false)
        addLegendDot(fragment, legend, 0xffb2553f.toInt(), "逾期", true)
        addLegendDot(fragment, legend, 0xff9bb595.toInt(), "已完成", true)
        ForestUi.add(pane.body, legend, 6)

        StudySheet.addHairline(pane.body, 10, 12)
        val previewRow = row(ctx)
        previewRow.addView(previewDate, LinearLayout.LayoutParams(0, -2, 1f))
        previewRow.addView(previewStats)
        pane.body.addView(previewRow, LinearLayout.LayoutParams(-1, -2))
        syncPreview()

        if (!currentFilterDay.isNullOrEmpty()) {
            pane.secondary("查看全部") {
                fragment.todoFilterDay = null
                sheet.dismiss()
                onFilterChanged()
            }
        }
        pane.primary("查看这一天") {
            fragment.todoFilterDay = calendar.selectedDayKey()
            sheet.dismiss()
            onFilterChanged()
        }
        sheet.push(pane)
    }

    private fun addLegendDot(f: BaseStudyFragment, row: LinearLayout, color: Int, label: String, spaced: Boolean) {
        val dot = View(f.ctx()).apply {
            background = rounded(f.ctx(), color, 0, 99)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val lp = LinearLayout.LayoutParams(f.dp(6), f.dp(6)).apply {
            if (spaced) marginStart = f.dp(14)
            marginEnd = f.dp(5)
        }
        row.addView(dot, lp)
        row.addView(text(f.ctx(), label, 11, MUTED))
    }

    fun showEditorSheet(fragment: StudyTodoFragment, existing: TodoStore.TodoItem?) {
        val ctx = fragment.ctx()
        val todos = TodoStore.get(ctx)
        val sheet = fragment.newSheet()
        val pane = fragment.sheetPane(if (existing == null) "新建待办" else "编辑待办", null, 0)

        val title = EditText(ctx).apply {
            isSingleLine = false; maxLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = "准备做哪件事？"
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(120))
            setText(existing?.title.orEmpty())
            textSize = 17.5f
            setTextColor(0xff243624.toInt()); setHintTextColor(0xff9aa094.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = null
            setPadding(fragment.dp(2), fragment.dp(4), fragment.dp(2), fragment.dp(6))
        }
        pane.body.addView(title, LinearLayout.LayoutParams(-1, -2))

        val noteInput = EditText(ctx).apply {
            isSingleLine = false; maxLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "添加备注（选填）"
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(200))
            setText(existing?.note.orEmpty())
            textSize = 13.5f
            setTextColor(MUTED); setHintTextColor(0xffa7ad9f.toInt())
            background = null
            setPadding(fragment.dp(2), fragment.dp(2), fragment.dp(2), fragment.dp(8))
        }
        pane.body.addView(noteInput, LinearLayout.LayoutParams(-1, -2))
        StudySheet.addHairline(pane.body, 4, 10)

        val todayKey = TodoStore.dayKey(System.currentTimeMillis())
        val tomorrowKey = TodoStore.dayKey(Calendar.getInstance().apply { add(Calendar.DATE, 1) }.timeInMillis)
        val filterDay = fragment.todoFilterDay
        val defaultNewDay = if (!filterDay.isNullOrEmpty()) filterDay else if (fragment.todoFilter == 1) "" else todayKey
        var chosenDay = existing?.day ?: defaultNewDay
        var leadMinutes = existing?.remindLeadMinutes ?: 30
        val initialRemind = existing != null && existing.remind

        val dateRow = row(ctx).apply { minimumHeight = fragment.dp(44) }
        dateRow.addView(
            text(ctx, "日期", 13, 0xff5b5447.toInt()).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val todayChip = StudySheet.link(ctx, "今天", DEEP) {}
        val tomorrowChip = StudySheet.link(ctx, "明天", DEEP) {}
        val pickDayChip = StudySheet.link(ctx, "选日期", DEEP) {}
        val anytimeChip = StudySheet.link(ctx, "不限", DEEP) {}
        val dateChips = arrayOf(todayChip, tomorrowChip, pickDayChip, anytimeChip)
        for (i in dateChips.indices) {
            dateChips[i].apply {
                textSize = 12f; minimumHeight = fragment.dp(32)
                setPadding(fragment.dp(11), fragment.dp(5), fragment.dp(11), fragment.dp(5))
            }
            dateRow.addView(dateChips[i], LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = fragment.dp(6) })
        }
        pane.body.addView(dateRow, LinearLayout.LayoutParams(-1, -2))

        val syncDateChips = {
            val isToday = chosenDay == todayKey
            val isTomorrow = chosenDay == tomorrowKey
            val isAnytime = chosenDay.isEmpty()
            val isCustom = !isToday && !isTomorrow && !isAnytime
            pickDayChip.text = if (isCustom) shortMonthDay(chosenDay) else "选日期"
            StudySheet.styleChoiceChip(todayChip, isToday)
            StudySheet.styleChoiceChip(tomorrowChip, isTomorrow)
            StudySheet.styleChoiceChip(pickDayChip, isCustom)
            StudySheet.styleChoiceChip(anytimeChip, isAnytime)
        }
        todayChip.setOnClickListener { chosenDay = todayKey; syncDateChips() }
        tomorrowChip.setOnClickListener { chosenDay = tomorrowKey; syncDateChips() }
        anytimeChip.setOnClickListener { chosenDay = ""; syncDateChips() }
        pickDayChip.setOnClickListener {
            sheet.hideKeyboard()
            sheet.pushDatePicker("选择待办日期", chosenDay) { picked -> chosenDay = picked; syncDateChips() }
        }
        syncDateChips()
        StudySheet.addHairline(pane.body, 10, 4)

        val timeHeader = row(ctx).apply {
            minimumHeight = fragment.dp(48)
            setPadding(fragment.dp(2), fragment.dp(8), fragment.dp(2), fragment.dp(8))
            background = ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 10), false)
        }
        timeHeader.addView(
            text(ctx, "截止时刻", 13, 0xff5b5447.toInt()).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val timeValue = text(ctx, "未设置 ›", 13, MUTED).apply { fontFeatureSettings = "tnum" }
        timeHeader.addView(timeValue)
        pane.body.addView(timeHeader, LinearLayout.LayoutParams(-1, -2))

        val timePanel = column(ctx).apply { visibility = View.GONE }
        val timePresetsRow = row(ctx)
        val dueTimeInput = EditText(ctx).apply {
            isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT
            hint = "或输入具体时刻，如 19:30"
            setText(existing?.dueTime.orEmpty()); fontFeatureSettings = "tnum"
        }
        StudySheet.styleQuietInlineField(dueTimeInput)

        val timePresets = arrayOf("12:00", "18:00", "21:00", "22:30", "不限")
        val timePresetChips = ArrayList<TextView>(timePresets.size)
        val syncTimeUi = {
            val tVal = dueTimeInput.text.toString().trim()
            val hasTime = tVal.isNotEmpty()
            timeValue.text = if (hasTime) "$tVal 截止 ›" else "未设置 ›"
            timeValue.setTextColor(if (hasTime) 0xff9e4c37.toInt() else MUTED)
            for (i in timePresets.indices) {
                StudySheet.styleChoiceChip(timePresetChips[i], if ("不限" == timePresets[i]) !hasTime else timePresets[i] == tVal)
            }
        }
        for (i in timePresets.indices) {
            val preset = timePresets[i]
            val chip = StudySheet.link(ctx, preset, DEEP) {
                dueTimeInput.setText(if ("不限" == preset) "" else preset)
                syncTimeUi()
                timePanel.visibility = View.GONE
            }.apply {
                textSize = 12f; fontFeatureSettings = "tnum"; minimumHeight = fragment.dp(34)
                setPadding(fragment.dp(6), fragment.dp(5), fragment.dp(6), fragment.dp(5))
            }
            timePresetsRow.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = fragment.dp(6) })
            timePresetChips.add(chip)
        }
        timePanel.addView(timePresetsRow)
        ForestUi.add(timePanel, dueTimeInput, 6)
        ForestUi.add(pane.body, timePanel, 4)

        timeHeader.setOnClickListener { timePanel.visibility = if (timePanel.visibility != View.VISIBLE) View.VISIBLE else View.GONE }
        dueTimeInput.addTextChangedListener(simpleWatcher { syncTimeUi() })
        syncTimeUi()
        StudySheet.addHairline(pane.body, 4, 2)

        val remindSwitch = ForestUi.flatSwitchRow(
            ctx, "截止提醒", todoLeadSubtitle(initialRemind, leadMinutes, todos.defaultRemindTime())
        ).apply { isChecked = initialRemind }
        pane.body.addView(remindSwitch, LinearLayout.LayoutParams(-1, -2))

        val leadRow = row(ctx)
        val leadValues = intArrayOf(15, 30, 60, 1440)
        val leadLabels = arrayOf("提前15分", "提前30分", "提前1小时", "前一天")
        val leadButtons = ArrayList<TextView>(leadValues.size)
        val updateLeadUi = {
            val on = remindSwitch.isChecked
            leadRow.visibility = if (on) View.VISIBLE else View.GONE
            ForestUi.updateFlatSwitchSubtitle(remindSwitch, todoLeadSubtitle(on, leadMinutes, todos.defaultRemindTime()))
            for (i in leadButtons.indices) StudySheet.styleChoiceChip(leadButtons[i], leadMinutes == leadValues[i])
        }
        for (i in leadValues.indices) {
            val value = leadValues[i]
            val btn = StudySheet.link(ctx, leadLabels[i], DEEP) { leadMinutes = value; updateLeadUi() }.apply {
                textSize = 11f; minimumHeight = fragment.dp(32)
                setPadding(fragment.dp(6), fragment.dp(5), fragment.dp(6), fragment.dp(5))
            }
            leadRow.addView(btn, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = fragment.dp(6) })
            leadButtons.add(btn)
        }
        remindSwitch.setOnCheckedChangeListener { _, _ -> updateLeadUi() }
        updateLeadUi()
        ForestUi.add(pane.body, leadRow, 4)

        val notebook = fragment.store.notebook()
        if (existing != null && existing.completed == 0L && notebook.currentPlan() != null && !notebook.hasSession()) {
            StudySheet.addHairline(pane.body, 12, 4)
            val focusRow = row(ctx).apply {
                minimumHeight = fragment.dp(48)
                setPadding(fragment.dp(2), fragment.dp(8), fragment.dp(2), fragment.dp(8))
                background = ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 10), false)
            }
            focusRow.addView(
                text(ctx, "以此为目标去专注", 13, 0xff315e3a.toInt()).apply { typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) },
                LinearLayout.LayoutParams(0, -2, 1f)
            )
            focusRow.addView(text(ctx, "前往自习室 ›", 12, MUTED))
            focusRow.setOnClickListener {
                val taskName = title.text.toString().trim().ifEmpty { existing.title }
                fragment.store.start("", taskName)
                sheet.dismiss()
                fragment.open(AppDestination.STUDY_FOCUS)
            }
            ForestMotion.bind(focusRow)
            pane.body.addView(focusRow, LinearLayout.LayoutParams(-1, -2))
        }

        if (existing != null) {
            pane.secondary("删除待办") { todos.delete(existing.id); sheet.dismiss(); fragment.render() }
        }
        pane.primary("保存待办") {
            fragment.perform {
                val cleanTime = TodoStore.TodoItem.validateTime(dueTimeInput.text.toString())
                if (existing == null) {
                    todos.add(title.text.toString(), noteInput.text.toString(), chosenDay, cleanTime, remindSwitch.isChecked, leadMinutes)
                } else {
                    todos.update(
                        existing.id, title.text.toString(), noteInput.text.toString(),
                        chosenDay, cleanTime, remindSwitch.isChecked, leadMinutes, existing.completed > 0
                    )
                }
                if (remindSwitch.isChecked) fragment.allowStudyNotifications()
                sheet.dismiss()
                fragment.render()
            }
        }
        sheet.push(pane)
    }

    private fun todoLeadSubtitle(enabled: Boolean, leadMinutes: Int, defaultTime: String): String = when {
        !enabled -> "已关闭，开启后在截止前自动提醒"
        leadMinutes >= 1440 -> "截止前一天 $defaultTime 轻提醒"
        leadMinutes >= 60 && leadMinutes % 60 == 0 -> "截止前 ${leadMinutes / 60} 小时轻提醒"
        else -> "截止前 $leadMinutes 分钟轻提醒"
    }

    fun showReminderSettingsDialog(fragment: StudyTodoFragment) {
        val ctx = fragment.ctx()
        val todos = TodoStore.get(ctx)
        val sheet = fragment.newSheet()
        val pane = fragment.sheetPane("待办提醒", null, 0)

        val initialTime = todos.defaultRemindTime()
        val presets = arrayOf("09:00", "12:00", "18:00", "20:30")
        var customMode = presets.none { it == initialTime }
        var selectedTime = initialTime
        StudySheet.addHairline(pane.body, 0, 2)

        val masterSwitch = ForestUi.flatSwitchRow(
            ctx, "截止前提醒",
            if (todos.remindEnabled()) "未设具体时刻时，每日 $initialTime 提醒" else "已关闭，开启后在截止前自动提醒"
        ).apply { isChecked = todos.remindEnabled() }
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
            isSingleLine = true; setText(initialTime); hint = "输入时刻，如 19:30"; fontFeatureSettings = "tnum"
        }
        StudySheet.styleQuietInlineField(customInput)

        val syncUi = {
            val on = masterSwitch.isChecked
            detailsSection.visibility = if (on) View.VISIBLE else View.GONE
            val cur = selectedTime.ifEmpty { initialTime }
            timeDisplay.text = cur
            ForestUi.updateFlatSwitchSubtitle(
                masterSwitch, if (on) "未设具体时刻时，每日 $cur 提醒" else "已关闭，开启后在截止前自动提醒"
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

        customInput.addTextChangedListener(simpleWatcher { s ->
            if (customMode) {
                selectedTime = s.trim()
                val cur = selectedTime.ifEmpty { initialTime }
                timeDisplay.text = cur
                if (masterSwitch.isChecked) ForestUi.updateFlatSwitchSubtitle(masterSwitch, "未设具体时刻时，每日 $cur 提醒")
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
            val sent = TodoNotifications.sendImmediate(ctx, "待办即将截止提醒", "提醒功能已就绪！将在待办截止时间前自动通知你。")
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
                val timeToSave = if (customMode) customInput.text.toString() else selectedTime
                todos.setReminderConfig(masterSwitch.isChecked, timeToSave)
                if (masterSwitch.isChecked) fragment.allowStudyNotifications()
                sheet.dismiss()
                fragment.render()
            }
        }
        sheet.push(pane)
    }

    internal fun simpleWatcher(onChanged: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onChanged(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) {}
    }
}

package com.example.pkmapp.study

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.example.pkmapp.R
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestNotebookPad
import com.example.pkmapp.forest.ForestSegmentedControl
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.GREEN
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.heading
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.todo.TodoStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.max

class StudyTodoFragment : BaseStudyFragment() {

    internal var todoFilter: Int
        get() = sharedViewModel.todoFilterState.value.tabIndex
        set(value) = sharedViewModel.updateTodoTab(value)

    internal var todoFilterDay: String?
        get() = sharedViewModel.todoFilterState.value.filterDay
        set(value) = sharedViewModel.updateTodoFilterDay(value)

    override fun buildPage(body: LinearLayout) {
        val content = content()
        body.addView(content)
        val todos = TodoStore.get(ctx())
        setBarTitle("待办")

        val toolbar = row(ctx()).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(10))
            background = bottomHairlineBg(0x1f5c4938)
        }

        val leftMeta = column(ctx())
        val todayLabel = text(
            ctx(),
            SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(Date()),
            14,
            0xff5c4938.toInt()
        ).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        leftMeta.addView(todayLabel)

        val subLinks = row(ctx()).apply { gravity = Gravity.CENTER_VERTICAL }
        val remindOn = todos.remindEnabled()
        val remindColor = if (remindOn) DEEP else MUTED
        val remindRow = row(ctx()).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
            setPadding(0, dp(4), dp(4), dp(4))
            background = ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 8), false)
            setOnClickListener { StudyTodoDialogs.showReminderSettingsDialog(this@StudyTodoFragment) }
            ForestMotion.bind(this)
        }
        val remindText = text(
            ctx(),
            if (remindOn) "提醒 ${todos.defaultRemindTime()}" else "提醒已关",
            13,
            remindColor
        ).apply {
            textSize = 13.5f
            isSingleLine = true
            typeface = Typeface.create(if (remindOn) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            setCompoundDrawablesRelative(tintedIcon(R.drawable.ic_editorial_bell, 15, remindColor), null, null, null)
            compoundDrawablePadding = dp(4)
        }
        remindRow.addView(remindText)
        if (remindOn) {
            val dot = View(ctx()).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = rounded(ctx(), GREEN, 0, 99)
            }
            remindRow.addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginStart = dp(5) })
        }
        subLinks.addView(remindRow)

        val sep = View(ctx()).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(0x285c4938)
        }
        subLinks.addView(
            sep,
            LinearLayout.LayoutParams(max(1, dp(1)), dp(13)).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            }
        )

        val dateFilterPill = StudySheet.link(ctx(), "按日期筛选 ▾", 0xff6b5c49.toInt()) {}.apply {
            textSize = 13.5f
            isSingleLine = true
            fontFeatureSettings = "tnum"
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
            compoundDrawablePadding = dp(4)
        }
        subLinks.addView(dateFilterPill)
        ForestUi.add(leftMeta, subLinks, 4)
        toolbar.addView(leftMeta, LinearLayout.LayoutParams(0, -2, 1f))

        val addTodoBtn = StudySheet.link(ctx(), "新建", DEEP) {
            StudyTodoDialogs.showEditorSheet(this, null)
        }.apply {
            contentDescription = "新建待办"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumHeight = dp(40)
            setPadding(dp(14), dp(7), dp(15), dp(7))
            setCompoundDrawablesRelative(tintedIcon(R.drawable.ic_editorial_plus, 14, DEEP), null, null, null)
            compoundDrawablePadding = dp(5)
            background = ripple(ctx(), rounded(ctx(), 0xfffffefa.toInt(), 0x4c315e3a, 999), false)
            elevation = dp(1).toFloat()
        }
        toolbar.addView(addTodoBtn, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        ForestUi.add(content, toolbar, 4)

        val notebookPad = ForestNotebookPad(ctx()).apply { minimumHeight = dp(350) }
        val dateFilterStrip = row(ctx()).apply { setPadding(dp(2), dp(2), dp(2), dp(2)) }
        val prevDayBtn = StudySheet.link(ctx(), "‹", 0xff4c594a.toInt()) {}.apply {
            textSize = 18f; contentDescription = "前一天"; minimumHeight = dp(32)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = ripple(ctx(), rounded(ctx(), 0x125c4938, 0, 999), false)
        }
        dateFilterStrip.addView(prevDayBtn, LinearLayout.LayoutParams(dp(32), dp(32)))

        val filterDayLabel = StudySheet.link(ctx(), "", 0xff243624.toInt()) {}.apply {
            textSize = 13f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"; minimumHeight = dp(32)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        dateFilterStrip.addView(filterDayLabel, LinearLayout.LayoutParams(-2, -2))

        val nextDayBtn = StudySheet.link(ctx(), "›", 0xff4c594a.toInt()) {}.apply {
            textSize = 18f; contentDescription = "后一天"; minimumHeight = dp(32)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = ripple(ctx(), rounded(ctx(), 0x125c4938, 0, 999), false)
        }
        dateFilterStrip.addView(nextDayBtn, LinearLayout.LayoutParams(dp(32), dp(32)))
        dateFilterStrip.addView(View(ctx()), LinearLayout.LayoutParams(0, 1, 1f))

        val clearFilterBtn = StudySheet.link(ctx(), "退出筛选", 0xff7b5c3b.toInt()) {}.apply {
            textSize = 12f; minimumHeight = dp(30)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = ripple(ctx(), rounded(ctx(), 0x125c4938, 0, 999), false)
        }
        dateFilterStrip.addView(clearFilterBtn)

        var filterBar: ForestSegmentedControl? = null
        val refreshTodoView = {
            val activeDay = todoFilterDay
            if (!activeDay.isNullOrEmpty()) {
                dateFilterPill.text = "已选 ${shortMonthDay(activeDay)} ▾"
                dateFilterPill.setTextColor(DEEP)
                dateFilterPill.setCompoundDrawablesRelative(
                    tintedIcon(R.drawable.ic_editorial_calendar, 15, DEEP), null, null, null
                )
                dateFilterPill.setPadding(dp(10), dp(4), dp(10), dp(4))
                dateFilterPill.background = ripple(ctx(), rounded(ctx(), 0xffe5eee0.toInt(), 0x55315e3a, 999), false)
                dateFilterStrip.visibility = View.VISIBLE
                val todayKey = TodoStore.dayKey(System.currentTimeMillis())
                val tomorrowKey = TodoStore.dayKey(Calendar.getInstance().apply { add(Calendar.DATE, 1) }.timeInMillis)
                val yesterdayKey = TodoStore.dayKey(Calendar.getInstance().apply { add(Calendar.DATE, -1) }.timeInMillis)
                filterDayLabel.text = formatTodoDayHeader(activeDay, todayKey, tomorrowKey, yesterdayKey, true)
            } else {
                dateFilterPill.text = "按日期筛选 ▾"
                dateFilterPill.setTextColor(0xff6b5c49.toInt())
                dateFilterPill.setCompoundDrawablesRelative(
                    tintedIcon(R.drawable.ic_editorial_calendar, 15, 0xff6b5c49.toInt()), null, null, null
                )
                dateFilterPill.setPadding(dp(2), dp(4), dp(4), dp(4))
                dateFilterPill.background = ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 8), false)
                dateFilterStrip.visibility = View.GONE
            }
            populateStandaloneTodos(notebookPad, filterBar, todos)
            sharedViewModel.syncFromStores(ctx())
        }

        dateFilterPill.setOnClickListener { StudyTodoDialogs.showDateFilterSheet(this, todos, refreshTodoView) }
        filterDayLabel.setOnClickListener { StudyTodoDialogs.showDateFilterSheet(this, todos, refreshTodoView) }
        prevDayBtn.setOnClickListener { sharedViewModel.shiftTodoFilterDay(-1); refreshTodoView() }
        nextDayBtn.setOnClickListener { sharedViewModel.shiftTodoFilterDay(1); refreshTodoView() }
        clearFilterBtn.setOnClickListener { todoFilterDay = null; refreshTodoView() }

        val segmented = ForestSegmentedControl(
            ctx(),
            arrayOf("今天", "计划", "已完成"),
            null,
            todoFilter.coerceIn(0, 2)
        ) { idx ->
            todoFilter = idx
            refreshTodoView()
        }
        filterBar = segmented
        ForestUi.add(content, segmented, 12)
        ForestUi.add(content, dateFilterStrip, 8)
        ForestUi.add(content, notebookPad, 10)
        refreshTodoView()
    }

    private fun populateStandaloneTodos(
        host: LinearLayout,
        filterBar: ForestSegmentedControl?,
        todos: TodoStore
    ) {
        host.removeAllViews()
        val refreshList: () -> Unit = {
            populateStandaloneTodos(host, filterBar, todos)
            sharedViewModel.syncFromStores(ctx())
        }
        val todayKey = TodoStore.dayKey(System.currentTimeMillis())
        val tomorrowKey = TodoStore.dayKey(Calendar.getInstance().apply { add(Calendar.DATE, 1) }.timeInMillis)
        val yesterdayKey = TodoStore.dayKey(Calendar.getInstance().apply { add(Calendar.DATE, -1) }.timeInMillis)

        val pendingList = todos.pending()
        val overdueItems = ArrayList<TodoStore.TodoItem>()
        val todayItems = ArrayList<TodoStore.TodoItem>()
        val plannedDated = ArrayList<TodoStore.TodoItem>()
        val plannedAnytime = ArrayList<TodoStore.TodoItem>()
        for (t in pendingList) {
            when {
                t.day.isNotEmpty() && t.day < todayKey -> overdueItems.add(t)
                t.day == todayKey -> todayItems.add(t)
                t.day.isNotEmpty() -> plannedDated.add(t)
                else -> plannedAnytime.add(t)
            }
        }
        val doneList = todos.completed()
        val todayCount = overdueItems.size + todayItems.size
        val plannedCount = plannedDated.size + plannedAnytime.size
        val targetDay = todoFilterDay
        filterBar?.apply {
            setTabLabels(
                arrayOf(
                    StudyTodoRows.formatTabLabel("今天", todayCount),
                    StudyTodoRows.formatTabLabel("计划", plannedCount),
                    StudyTodoRows.formatTabLabel("已完成", doneList.size)
                )
            )
            setSelectionCleared(!targetDay.isNullOrEmpty())
        }

        if (!targetDay.isNullOrEmpty()) {
            val targetOverdue = targetDay < todayKey
            val dayPending = pendingList.filter { targetDay == it.day }
            val dayDone = doneList.filter {
                targetDay == (if (it.completed > 0) TodoStore.dayKey(it.completed) else it.day)
            }
            val quickHint = "＋ 快速记一条 ${shortMonthDay(targetDay)} 待办..."
            if (dayPending.isEmpty() && dayDone.isEmpty()) {
                val emptyBox = column(ctx()).apply {
                    setPadding(dp(50), dp(18), dp(8), dp(12))
                    addView(heading(ctx(), "${shortMonthDay(targetDay)} 暂无待办", 17))
                }
                ForestUi.add(emptyBox, note("可直接在下方记一条这一天的待办，或点上方 ‹ › 切换相邻日期。"), 6)
                host.addView(emptyBox)
                StudyTodoRows.appendQuickAddRow(this, host, todos, targetDay, quickHint, refreshList)
                return
            }
            var gIdx = 0
            if (dayPending.isNotEmpty()) {
                val header = (if (targetOverdue) "已逾期 · " else "待办 · ") +
                    formatTodoDayHeader(targetDay, todayKey, tomorrowKey, yesterdayKey, false)
                StudyTodoRows.addGroupHeader(this, host, header, dayPending.size, targetOverdue, gIdx++ == 0)
                for (item in dayPending) {
                    StudyTodoRows.addPendingRow(this, host, todos, item, todayKey, targetOverdue, refreshList)
                }
            }
            if (dayDone.isNotEmpty()) {
                StudyTodoRows.addGroupHeader(this, host, "当天已完成", dayDone.size, false, gIdx == 0)
                val hm = SimpleDateFormat("HH:mm", Locale.CHINA)
                for (item in dayDone) {
                    StudyTodoRows.addCompletedRow(this, host, filterBar, todos, item, targetDay, hm, refreshList)
                }
            }
            StudyTodoRows.appendQuickAddRow(this, host, todos, targetDay, quickHint, refreshList)
            return
        }

        if (todoFilter == 2) {
            if (doneList.isEmpty()) {
                val emptyBox = column(ctx()).apply {
                    setPadding(dp(50), dp(20), dp(8), dp(20))
                    addView(heading(ctx(), "还没有已完成的待办", 17))
                }
                ForestUi.add(emptyBox, note("打勾划掉后，会按实际完成日期与时刻自动记入这里。"), 6)
                host.addView(emptyBox)
                return
            }
            val doneByDay = LinkedHashMap<String, MutableList<TodoStore.TodoItem>>()
            for (item in doneList) {
                val doneDay = if (item.completed > 0) TodoStore.dayKey(item.completed) else item.day.ifEmpty { todayKey }
                doneByDay.getOrPut(doneDay) { ArrayList() }.add(item)
            }
            val hm = SimpleDateFormat("HH:mm", Locale.CHINA)
            var groupIdx = 0
            for ((doneDay, items) in doneByDay) {
                StudyTodoRows.addGroupHeader(
                    this, host,
                    formatTodoDayHeader(doneDay, todayKey, tomorrowKey, yesterdayKey, true),
                    items.size, false, groupIdx++ == 0
                )
                for (item in items) {
                    StudyTodoRows.addCompletedRow(this, host, filterBar, todos, item, doneDay, hm, refreshList)
                }
            }
            return
        }

        if (todoFilter == 0) {
            if (todayCount == 0) {
                val emptyBox = column(ctx()).apply {
                    setPadding(dp(50), dp(18), dp(8), dp(12))
                    addView(heading(ctx(), "今天的事项都清完了", 17))
                }
                val sub = if (plannedCount > 0) "计划里还有 $plannedCount 项后续安排，随时可切过去查看。"
                else "在下方直接记下今日待办，或点右上角「+ 新建」设置截止提醒。"
                ForestUi.add(emptyBox, note(sub), 6)
                host.addView(emptyBox)
                StudyTodoRows.appendQuickAddRow(this, host, todos, todayKey, "＋ 快速记一条今日待办...", refreshList)
                return
            }
            var groupIdx = 0
            if (overdueItems.isNotEmpty()) {
                StudyTodoRows.addGroupHeader(this, host, "已逾期 · 优先清掉", overdueItems.size, true, groupIdx++ == 0)
                for (item in overdueItems) {
                    StudyTodoRows.addPendingRow(this, host, todos, item, todayKey, true, refreshList)
                }
            }
            if (todayItems.isNotEmpty()) {
                StudyTodoRows.addGroupHeader(
                    this, host,
                    formatTodoDayHeader(todayKey, todayKey, tomorrowKey, yesterdayKey, false),
                    todayItems.size, false, groupIdx == 0
                )
                for (item in todayItems) {
                    StudyTodoRows.addPendingRow(this, host, todos, item, todayKey, false, refreshList)
                }
            }
            StudyTodoRows.appendQuickAddRow(this, host, todos, todayKey, "＋ 快速记一条今日待办...", refreshList)
            return
        }

        if (plannedCount == 0) {
            val emptyBox = column(ctx()).apply {
                setPadding(dp(50), dp(18), dp(8), dp(12))
                addView(heading(ctx(), "暂无后续安排", 17))
            }
            ForestUi.add(emptyBox, note("未来日期的日程与未限日期的收集箱都会收在这里，和「今天」互不重复。"), 6)
            host.addView(emptyBox)
            StudyTodoRows.appendQuickAddRow(this, host, todos, "", "＋ 随手记一条待安排事项（不限日期）...", refreshList)
            return
        }
        val futureByDay = LinkedHashMap<String, MutableList<TodoStore.TodoItem>>()
        for (item in plannedDated) futureByDay.getOrPut(item.day) { ArrayList() }.add(item)
        var groupIdx = 0
        for ((day, items) in futureByDay) {
            StudyTodoRows.addGroupHeader(
                this, host,
                formatTodoDayHeader(day, todayKey, tomorrowKey, yesterdayKey, false),
                items.size, false, groupIdx++ == 0
            )
            for (item in items) {
                StudyTodoRows.addPendingRow(this, host, todos, item, todayKey, false, refreshList)
            }
        }
        if (plannedAnytime.isNotEmpty()) {
            StudyTodoRows.addGroupHeader(this, host, "随时可做 · 未限日期", plannedAnytime.size, false, groupIdx == 0)
            for (item in plannedAnytime) {
                StudyTodoRows.addPendingRow(this, host, todos, item, todayKey, false, refreshList)
            }
        }
        StudyTodoRows.appendQuickAddRow(this, host, todos, "", "＋ 随手记一条待安排事项（不限日期）...", refreshList)
    }
}

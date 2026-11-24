package com.example.pkmapp.study

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pkmapp.R
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestNotebookPad
import com.example.pkmapp.forest.ForestSegmentedControl
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.GREEN
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.todo.TodoStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
        lateinit var todoAdapter: StudyTodoListAdapter
        lateinit var quickAdd: StudyTodoRows.QuickAddController
        var refreshTodoView: () -> Unit = {}

        todoAdapter = StudyTodoListAdapter(
            onToggleDone = { item, nowDone ->
                todos.toggleDone(item.id, nowDone)
                if (nowDone) toast("已归档到「已完成」")
                refreshTodoView()
            },
            onEdit = { item -> StudyTodoDialogs.showEditorSheet(this, item) }
        )
        val todoRecycler = RecyclerView(ctx()).apply {
            layoutManager = LinearLayoutManager(ctx())
            adapter = todoAdapter
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            addItemDecoration(
                StudyTodoListAdapter.NotebookRuleDecoration(ctx().resources.displayMetrics.density)
            )
        }
        quickAdd = StudyTodoRows.createQuickAddController(this, todos) { refreshTodoView() }
        notebookPad.addView(todoRecycler, LinearLayout.LayoutParams(-1, -2))
        notebookPad.addView(quickAdd.root, LinearLayout.LayoutParams(-1, -2))

        refreshTodoView = {
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
            val todayKey = TodoStore.dayKey(System.currentTimeMillis())
            val tomorrowKey = TodoStore.dayKey(Calendar.getInstance().apply { add(Calendar.DATE, 1) }.timeInMillis)
            val yesterdayKey = TodoStore.dayKey(Calendar.getInstance().apply { add(Calendar.DATE, -1) }.timeInMillis)
            val model = StudyTodoListBuilder.build(
                pendingList = todos.pending(),
                doneList = todos.completed(),
                defaultRemindTime = todos.defaultRemindTime(),
                todoFilter = todoFilter,
                todoFilterDay = todoFilterDay,
                todayKey = todayKey,
                tomorrowKey = tomorrowKey,
                yesterdayKey = yesterdayKey
            )
            filterBar?.apply {
                setTabLabels(
                    arrayOf(
                        StudyTodoRows.formatTabLabel("今天", model.todayCount),
                        StudyTodoRows.formatTabLabel("计划", model.plannedCount),
                        StudyTodoRows.formatTabLabel("已完成", model.doneCount)
                    )
                )
                setSelectionCleared(!todoFilterDay.isNullOrEmpty())
            }
            todoAdapter.submitList(model.items)
            quickAdd.bind(model.quickAddDay, model.quickAddHint)
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
}

package com.example.pkmapp.study

import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.example.pkmapp.forest.ForestSegmentedControl
import com.example.pkmapp.forest.ForestTodoRow
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.study.BaseStudyFragment.Companion.shortMonthDay
import com.example.pkmapp.todo.TodoStore
import java.text.SimpleDateFormat
import java.util.Date

internal object StudyTodoRows {

    fun formatTabLabel(title: String, count: Int): CharSequence {
        if (count <= 0) return title
        val raw = "$title $count"
        return SpannableString(raw).apply {
            setSpan(RelativeSizeSpan(0.86f), title.length + 1, raw.length, 0)
        }
    }

    fun addGroupHeader(
        fragment: StudyTodoFragment,
        host: LinearLayout,
        title: String,
        count: Int,
        overdue: Boolean,
        first: Boolean
    ) {
        val ctx = fragment.ctx()
        val dateHeader = row(ctx).apply {
            setPadding(fragment.dp(50), fragment.dp(if (first) 6 else 14), fragment.dp(4), fragment.dp(6))
        }
        val dateTitle = text(ctx, title, 12, if (overdue) 0xff9e4c37.toInt() else 0xff3b543c.toInt()).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        dateHeader.addView(dateTitle, LinearLayout.LayoutParams(0, -2, 1f))
        dateHeader.addView(text(ctx, "$count 项", 11, MUTED).apply { fontFeatureSettings = "tnum" })
        host.addView(dateHeader, LinearLayout.LayoutParams(-1, -2))
    }

    fun addCompletedRow(
        fragment: StudyTodoFragment,
        host: LinearLayout,
        filterBar: ForestSegmentedControl?,
        todos: TodoStore,
        item: TodoStore.TodoItem,
        groupDay: String,
        hm: SimpleDateFormat,
        onRefresh: () -> Unit
    ) {
        val meta = StringBuilder()
        if (item.completed > 0) meta.append(hm.format(Date(item.completed))).append(" 完成")
        else meta.append("已完成")
        if (item.day.isNotEmpty() && item.day != groupDay) meta.append(" · 原计划 ").append(shortMonthDay(item.day))
        if (item.note.isNotEmpty()) meta.append(" · ").append(item.note)
        host.addView(
            ForestTodoRow(
                fragment.ctx(), item.title, 0, null, meta.toString(), false, true,
                {
                    todos.toggleDone(item.id, false)
                    onRefresh()
                },
                { StudyTodoDialogs.showEditorSheet(fragment, item) }
            )
        )
    }

    fun addPendingRow(
        fragment: StudyTodoFragment,
        host: LinearLayout,
        todos: TodoStore,
        item: TodoStore.TodoItem,
        todayKey: String,
        isOverdue: Boolean,
        onRefresh: () -> Unit
    ) {
        val meta = StringBuilder()
        if (isOverdue) meta.append("原定 ").append(shortMonthDay(item.day))
        if (item.dueTime.isNotEmpty()) {
            if (meta.isNotEmpty()) meta.append(" · ")
            meta.append(item.dueTime).append(" 截止")
            if (item.remind && item.day.isNotEmpty()) meta.append(" · ").append(item.leadLabel()).append("提醒")
        } else if (item.remind && item.day.isNotEmpty()) {
            if (meta.isNotEmpty()) meta.append(" · ")
            meta.append("当日 ").append(todos.defaultRemindTime()).append(" 提醒")
        }
        if (item.note.isNotEmpty()) {
            if (meta.isNotEmpty()) meta.append(" · ")
            meta.append(item.note)
        }
        val urgent = isOverdue || (item.day == todayKey && item.dueTime.isNotEmpty())
        host.addView(
            ForestTodoRow(
                fragment.ctx(), item.title, 0, null, meta.toString(), urgent, false,
                {
                    todos.toggleDone(item.id, true)
                    fragment.toast("已归档到「已完成」")
                    onRefresh()
                },
                { StudyTodoDialogs.showEditorSheet(fragment, item) }
            )
        )
    }

    fun appendQuickAddRow(
        fragment: StudyTodoFragment,
        host: LinearLayout,
        todos: TodoStore,
        defaultDay: String,
        hint: String,
        onRefresh: () -> Unit
    ) {
        val ctx = fragment.ctx()
        val quick = row(ctx).apply { setPadding(fragment.dp(50), fragment.dp(10), fragment.dp(6), fragment.dp(6)) }
        val input = EditText(ctx).apply {
            this.hint = hint
            setHintTextColor(0xff9aa094.toInt())
            setTextColor(0xff243624.toInt())
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = null
            setPadding(0, fragment.dp(6), fragment.dp(6), fragment.dp(6))
        }
        val commit = commit@{
            val textVal = input.text.toString().trim()
            if (textVal.isEmpty()) return@commit
            todos.add(textVal, "", defaultDay, "", false, 30)
            input.setText("")
            onRefresh()
            val msg = when {
                defaultDay.isEmpty() -> "已记入计划收集箱"
                defaultDay == TodoStore.dayKey(System.currentTimeMillis()) -> "已记入今日待办"
                else -> "已记入 ${shortMonthDay(defaultDay)} 待办"
            }
            fragment.toast(msg)
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { commit(); true } else false
        }
        quick.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val addBtn = StudySheet.link(ctx, "记下", DEEP, commit).apply {
            textSize = 12f
            minimumHeight = fragment.dp(32)
            setPadding(fragment.dp(12), fragment.dp(4), fragment.dp(12), fragment.dp(4))
            background = ripple(ctx, rounded(ctx, 0xffeef4ea.toInt(), 0, 999), false)
        }
        quick.addView(addBtn)
        host.addView(quick, LinearLayout.LayoutParams(-1, -2))
    }
}

package com.example.pkmapp.study

import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.study.BaseStudyFragment.Companion.shortMonthDay
import com.example.pkmapp.todo.TodoStore

internal object StudyTodoRows {

    fun formatTabLabel(title: String, count: Int): CharSequence {
        if (count <= 0) return title
        val raw = "$title $count"
        return SpannableString(raw).apply {
            setSpan(RelativeSizeSpan(0.86f), title.length + 1, raw.length, 0)
        }
    }

    internal class QuickAddController(
        val root: LinearLayout,
        private val input: EditText
    ) {
        private var targetDay: String = ""

        fun bind(defaultDay: String?, hint: String?) {
            if (defaultDay == null) {
                root.visibility = View.GONE
                return
            }
            targetDay = defaultDay
            input.hint = hint ?: ""
            root.visibility = View.VISIBLE
        }

        fun currentDay(): String = targetDay
    }

    fun createQuickAddController(
        fragment: StudyTodoFragment,
        todos: TodoStore,
        onRefresh: () -> Unit
    ): QuickAddController {
        val ctx = fragment.ctx()
        val quick = row(ctx).apply {
            setPadding(fragment.dp(50), fragment.dp(10), fragment.dp(6), fragment.dp(6))
        }
        lateinit var controller: QuickAddController
        val input = EditText(ctx).apply {
            setHintTextColor(0xff9aa094.toInt())
            setTextColor(0xff243624.toInt())
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = null
            setPadding(0, fragment.dp(6), fragment.dp(6), fragment.dp(6))
        }
        controller = QuickAddController(quick, input)
        val commit = commit@{
            val textVal = input.text.toString().trim()
            if (textVal.isEmpty()) return@commit
            val defaultDay = controller.currentDay()
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
        return controller
    }
}

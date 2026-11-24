package com.example.pkmapp.study

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pkmapp.forest.ForestTodoRow
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.dp
import com.example.pkmapp.forest.ForestUi.heading
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.study.BaseStudyFragment.Companion.shortMonthDay
import com.example.pkmapp.todo.TodoStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

internal sealed interface StudyTodoListItem {
    val key: String

    data class GroupHeader(
        override val key: String,
        val title: String,
        val count: Int,
        val overdue: Boolean,
        val first: Boolean
    ) : StudyTodoListItem

    data class TodoEntry(
        override val key: String,
        val itemId: String,
        val title: String,
        val meta: String,
        val urgent: Boolean,
        val checked: Boolean,
        val rawItem: TodoStore.TodoItem
    ) : StudyTodoListItem {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TodoEntry) return false
            return key == other.key &&
                itemId == other.itemId &&
                title == other.title &&
                meta == other.meta &&
                urgent == other.urgent &&
                checked == other.checked
        }

        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + itemId.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + meta.hashCode()
            result = 31 * result + urgent.hashCode()
            result = 31 * result + checked.hashCode()
            return result
        }
    }

    data class EmptyState(
        override val key: String,
        val headingText: String,
        val subText: String,
        val compactBottom: Boolean
    ) : StudyTodoListItem
}

internal data class StudyTodoPageModel(
    val todayCount: Int,
    val plannedCount: Int,
    val doneCount: Int,
    val items: List<StudyTodoListItem>,
    val quickAddDay: String?,
    val quickAddHint: String?
)

internal object StudyTodoListBuilder {

    fun build(
        pendingList: List<TodoStore.TodoItem>,
        doneList: List<TodoStore.TodoItem>,
        defaultRemindTime: String,
        todoFilter: Int,
        todoFilterDay: String?,
        todayKey: String,
        tomorrowKey: String,
        yesterdayKey: String,
        formatDayHeader: (String, String, String, String, Boolean) -> String =
            BaseStudyFragment.Companion::formatTodoDayHeader
    ): StudyTodoPageModel {
        val overdueItems = ArrayList<TodoStore.TodoItem>()
        val todayItems = ArrayList<TodoStore.TodoItem>()
        val plannedDated = ArrayList<TodoStore.TodoItem>()
        val plannedAnytime = ArrayList<TodoStore.TodoItem>()
        for (item in pendingList) {
            when {
                item.day.isNotEmpty() && item.day < todayKey -> overdueItems.add(item)
                item.day == todayKey -> todayItems.add(item)
                item.day.isNotEmpty() -> plannedDated.add(item)
                else -> plannedAnytime.add(item)
            }
        }
        val todayCount = overdueItems.size + todayItems.size
        val plannedCount = plannedDated.size + plannedAnytime.size
        val items = ArrayList<StudyTodoListItem>()
        val hm = SimpleDateFormat("HH:mm", Locale.CHINA)

        if (!todoFilterDay.isNullOrEmpty()) {
            val targetOverdue = todoFilterDay < todayKey
            val dayPending = pendingList.filter { todoFilterDay == it.day }
            val dayDone = doneList.filter {
                todoFilterDay == (if (it.completed > 0) TodoStore.dayKey(it.completed) else it.day)
            }
            val quickHint = "＋ 快速记一条 ${shortMonthDay(todoFilterDay)} 待办..."
            if (dayPending.isEmpty() && dayDone.isEmpty()) {
                items.add(
                    StudyTodoListItem.EmptyState(
                        key = "empty_day_$todoFilterDay",
                        headingText = "${shortMonthDay(todoFilterDay)} 暂无待办",
                        subText = "可直接在下方记一条这一天的待办，或点上方 ‹ › 切换相邻日期。",
                        compactBottom = true
                    )
                )
            } else {
                var groupIdx = 0
                if (dayPending.isNotEmpty()) {
                    val headerTitle = (if (targetOverdue) "已逾期 · " else "待办 · ") +
                        formatDayHeader(todoFilterDay, todayKey, tomorrowKey, yesterdayKey, false)
                    items.add(
                        StudyTodoListItem.GroupHeader(
                            key = "hdr_day_pending_$todoFilterDay",
                            title = headerTitle,
                            count = dayPending.size,
                            overdue = targetOverdue,
                            first = groupIdx++ == 0
                        )
                    )
                    dayPending.forEach { item ->
                        items.add(pendingEntry(item, todayKey, targetOverdue, defaultRemindTime))
                    }
                }
                if (dayDone.isNotEmpty()) {
                    items.add(
                        StudyTodoListItem.GroupHeader(
                            key = "hdr_day_done_$todoFilterDay",
                            title = "当天已完成",
                            count = dayDone.size,
                            overdue = false,
                            first = groupIdx == 0
                        )
                    )
                    dayDone.forEach { item ->
                        items.add(completedEntry(item, todoFilterDay, hm))
                    }
                }
            }
            return StudyTodoPageModel(todayCount, plannedCount, doneList.size, items, todoFilterDay, quickHint)
        }

        if (todoFilter == 2) {
            if (doneList.isEmpty()) {
                items.add(
                    StudyTodoListItem.EmptyState(
                        key = "empty_done",
                        headingText = "还没有已完成的待办",
                        subText = "打勾划掉后，会按实际完成日期与时刻自动记入这里。",
                        compactBottom = false
                    )
                )
            } else {
                val doneByDay = LinkedHashMap<String, MutableList<TodoStore.TodoItem>>()
                for (item in doneList) {
                    val doneDay = if (item.completed > 0) {
                        TodoStore.dayKey(item.completed)
                    } else {
                        item.day.ifEmpty { todayKey }
                    }
                    doneByDay.getOrPut(doneDay) { ArrayList() }.add(item)
                }
                var groupIdx = 0
                for ((doneDay, groupItems) in doneByDay) {
                    items.add(
                        StudyTodoListItem.GroupHeader(
                            key = "hdr_done_$doneDay",
                            title = formatDayHeader(doneDay, todayKey, tomorrowKey, yesterdayKey, true),
                            count = groupItems.size,
                            overdue = false,
                            first = groupIdx++ == 0
                        )
                    )
                    groupItems.forEach { item ->
                        items.add(completedEntry(item, doneDay, hm))
                    }
                }
            }
            return StudyTodoPageModel(todayCount, plannedCount, doneList.size, items, null, null)
        }

        if (todoFilter == 0) {
            if (todayCount == 0) {
                val sub = if (plannedCount > 0) {
                    "计划里还有 $plannedCount 项后续安排，随时可切过去查看。"
                } else {
                    "在下方直接记下今日待办，或点右上角「+ 新建」设置截止提醒。"
                }
                items.add(
                    StudyTodoListItem.EmptyState(
                        key = "empty_today",
                        headingText = "今天的事项都清完了",
                        subText = sub,
                        compactBottom = true
                    )
                )
            } else {
                var groupIdx = 0
                if (overdueItems.isNotEmpty()) {
                    items.add(
                        StudyTodoListItem.GroupHeader(
                            key = "hdr_overdue",
                            title = "已逾期 · 优先清掉",
                            count = overdueItems.size,
                            overdue = true,
                            first = groupIdx++ == 0
                        )
                    )
                    overdueItems.forEach { item ->
                        items.add(pendingEntry(item, todayKey, isOverdue = true, defaultRemindTime = defaultRemindTime))
                    }
                }
                if (todayItems.isNotEmpty()) {
                    items.add(
                        StudyTodoListItem.GroupHeader(
                            key = "hdr_today_$todayKey",
                            title = formatDayHeader(todayKey, todayKey, tomorrowKey, yesterdayKey, false),
                            count = todayItems.size,
                            overdue = false,
                            first = groupIdx == 0
                        )
                    )
                    todayItems.forEach { item ->
                        items.add(pendingEntry(item, todayKey, isOverdue = false, defaultRemindTime = defaultRemindTime))
                    }
                }
            }
            return StudyTodoPageModel(
                todayCount, plannedCount, doneList.size, items,
                todayKey, "＋ 快速记一条今日待办..."
            )
        }

        if (plannedCount == 0) {
            items.add(
                StudyTodoListItem.EmptyState(
                    key = "empty_planned",
                    headingText = "暂无后续安排",
                    subText = "未来日期的日程与未限日期的收集箱都会收在这里，和「今天」互不重复。",
                    compactBottom = true
                )
            )
        } else {
            val futureByDay = LinkedHashMap<String, MutableList<TodoStore.TodoItem>>()
            for (item in plannedDated) {
                futureByDay.getOrPut(item.day) { ArrayList() }.add(item)
            }
            var groupIdx = 0
            for ((day, groupItems) in futureByDay) {
                items.add(
                    StudyTodoListItem.GroupHeader(
                        key = "hdr_planned_$day",
                        title = formatDayHeader(day, todayKey, tomorrowKey, yesterdayKey, false),
                        count = groupItems.size,
                        overdue = false,
                        first = groupIdx++ == 0
                    )
                )
                groupItems.forEach { item ->
                    items.add(pendingEntry(item, todayKey, isOverdue = false, defaultRemindTime = defaultRemindTime))
                }
            }
            if (plannedAnytime.isNotEmpty()) {
                items.add(
                    StudyTodoListItem.GroupHeader(
                        key = "hdr_planned_anytime",
                        title = "随时可做 · 未限日期",
                        count = plannedAnytime.size,
                        overdue = false,
                        first = groupIdx == 0
                    )
                )
                plannedAnytime.forEach { item ->
                    items.add(pendingEntry(item, todayKey, isOverdue = false, defaultRemindTime = defaultRemindTime))
                }
            }
        }
        return StudyTodoPageModel(
            todayCount, plannedCount, doneList.size, items,
            "", "＋ 随手记一条待安排事项（不限日期）..."
        )
    }

    private fun pendingEntry(
        item: TodoStore.TodoItem,
        todayKey: String,
        isOverdue: Boolean,
        defaultRemindTime: String
    ): StudyTodoListItem.TodoEntry {
        val meta = StringBuilder()
        if (isOverdue) meta.append("原定 ").append(shortMonthDay(item.day))
        if (item.dueTime.isNotEmpty()) {
            if (meta.isNotEmpty()) meta.append(" · ")
            meta.append(item.dueTime).append(" 截止")
            if (item.remind && item.day.isNotEmpty()) meta.append(" · ").append(item.leadLabel()).append("提醒")
        } else if (item.remind && item.day.isNotEmpty()) {
            if (meta.isNotEmpty()) meta.append(" · ")
            meta.append("当日 ").append(defaultRemindTime).append(" 提醒")
        }
        if (item.note.isNotEmpty()) {
            if (meta.isNotEmpty()) meta.append(" · ")
            meta.append(item.note)
        }
        val urgent = isOverdue || (item.day == todayKey && item.dueTime.isNotEmpty())
        return StudyTodoListItem.TodoEntry(
            key = "todo_pending_${item.id}",
            itemId = item.id,
            title = item.title,
            meta = meta.toString(),
            urgent = urgent,
            checked = false,
            rawItem = item
        )
    }

    private fun completedEntry(
        item: TodoStore.TodoItem,
        groupDay: String,
        hm: SimpleDateFormat
    ): StudyTodoListItem.TodoEntry {
        val meta = StringBuilder()
        if (item.completed > 0) meta.append(hm.format(Date(item.completed))).append(" 完成")
        else meta.append("已完成")
        if (item.day.isNotEmpty() && item.day != groupDay) {
            meta.append(" · 原计划 ").append(shortMonthDay(item.day))
        }
        if (item.note.isNotEmpty()) meta.append(" · ").append(item.note)
        return StudyTodoListItem.TodoEntry(
            key = "todo_done_${item.id}",
            itemId = item.id,
            title = item.title,
            meta = meta.toString(),
            urgent = false,
            checked = true,
            rawItem = item
        )
    }
}

internal class StudyTodoListAdapter(
    private val onToggleDone: (TodoStore.TodoItem, Boolean) -> Unit,
    private val onEdit: (TodoStore.TodoItem) -> Unit
) : ListAdapter<StudyTodoListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is StudyTodoListItem.GroupHeader -> TYPE_HEADER
        is StudyTodoListItem.TodoEntry -> TYPE_ENTRY
        is StudyTodoListItem.EmptyState -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ctx)
            TYPE_ENTRY -> EntryHolder(ctx)
            else -> EmptyHolder(ctx)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is StudyTodoListItem.GroupHeader -> (holder as HeaderHolder).bind(item)
            is StudyTodoListItem.TodoEntry -> (holder as EntryHolder).bind(item, onToggleDone, onEdit)
            is StudyTodoListItem.EmptyState -> (holder as EmptyHolder).bind(item)
        }
    }

    private class HeaderHolder(ctx: Context) : RecyclerView.ViewHolder(
        row(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {
        private val root = itemView as LinearLayout
        private val titleTv: TextView = text(ctx, "", 12, 0xff3b543c.toInt()).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        private val countTv: TextView = text(ctx, "", 11, MUTED).apply {
            fontFeatureSettings = "tnum"
        }

        init {
            root.addView(titleTv, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(countTv)
        }

        fun bind(item: StudyTodoListItem.GroupHeader) {
            val ctx = root.context
            root.setPadding(dp(ctx, 50f), dp(ctx, if (item.first) 6f else 14f), dp(ctx, 4f), dp(ctx, 6f))
            titleTv.text = item.title
            titleTv.setTextColor(if (item.overdue) 0xff9e4c37.toInt() else 0xff3b543c.toInt())
            countTv.text = "${item.count} 项"
        }
    }

    private class EntryHolder(ctx: Context) : RecyclerView.ViewHolder(
        FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {
        private val host = itemView as FrameLayout

        fun bind(
            item: StudyTodoListItem.TodoEntry,
            onToggleDone: (TodoStore.TodoItem, Boolean) -> Unit,
            onEdit: (TodoStore.TodoItem) -> Unit
        ) {
            host.removeAllViews()
            host.addView(
                ForestTodoRow(
                    host.context,
                    item.title,
                    0,
                    null,
                    item.meta,
                    item.urgent,
                    item.checked,
                    { onToggleDone(item.rawItem, !item.checked) },
                    { onEdit(item.rawItem) }
                ),
                FrameLayout.LayoutParams(-1, -2)
            )
        }
    }

    private class EmptyHolder(ctx: Context) : RecyclerView.ViewHolder(
        column(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {
        private val root = itemView as LinearLayout
        private val titleTv = heading(ctx, "", 17)
        private val subTv = text(ctx, "", 12, MUTED)

        init {
            root.addView(titleTv)
            ForestUi.add(root, subTv, 6)
        }

        fun bind(item: StudyTodoListItem.EmptyState) {
            val ctx = root.context
            val vPad = if (item.compactBottom) 12f else 20f
            val topPad = if (item.compactBottom) 18f else 20f
            root.setPadding(dp(ctx, 50f), dp(ctx, topPad), dp(ctx, 8f), dp(ctx, vPad))
            titleTv.text = item.headingText
            subTv.text = item.subText
        }
    }

    internal class NotebookRuleDecoration(density: Float) : RecyclerView.ItemDecoration() {
        private val d = density
        private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = d
            color = 0xffeae5d5.toInt()
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val count = parent.childCount
            val maxAdapterIndex = state.itemCount - 1
            for (i in 0 until count) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos == RecyclerView.NO_POSITION || pos >= maxAdapterIndex) continue
                val y = child.bottom.toFloat()
                canvas.drawLine(2 * d, y, parent.width.toFloat(), y, rulePaint)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<StudyTodoListItem>() {
        override fun areItemsTheSame(oldItem: StudyTodoListItem, newItem: StudyTodoListItem): Boolean =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: StudyTodoListItem, newItem: StudyTodoListItem): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
        private const val TYPE_EMPTY = 2
    }
}

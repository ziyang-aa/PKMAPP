package com.example.pkmapp.study

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.LINE
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.dp
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class StudyRecordListItem(
    val id: String,
    val subjectTitle: String,
    val subjectColor: Int,
    val subtitle: String,
    val durationText: String,
    val record: StudyNotebook.Record
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StudyRecordListItem) return false
        return id == other.id &&
            subjectTitle == other.subjectTitle &&
            subjectColor == other.subjectColor &&
            subtitle == other.subtitle &&
            durationText == other.durationText
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + subjectTitle.hashCode()
        result = 31 * result + subjectColor
        result = 31 * result + subtitle.hashCode()
        result = 31 * result + durationText.hashCode()
        return result
    }
}

internal class StudyRecordListAdapter(
    private val onClick: (StudyNotebook.Record) -> Unit
) : ListAdapter<StudyRecordListItem, StudyRecordListAdapter.Holder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(parent.context, onClick)

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        context: Context,
        private val onClick: (StudyNotebook.Record) -> Unit
    ) : RecyclerView.ViewHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 59f)
            setPadding(dp(context, 2f), dp(context, 9f), dp(context, 2f), dp(context, 9f))
            background = ripple(context, rounded(context, 0x00000000, 0, 10), false)
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {
        private val root = itemView as LinearLayout
        private val dot: View = StudyStatsDialogs.addSubjectDot(root, 0)
        private val subject: TextView = text(context, "", 13, DEEP)
        private val date: TextView = text(context, "", 11, MUTED)
        private val duration: TextView = text(context, "", 12, DEEP).apply {
            gravity = Gravity.END
            fontFeatureSettings = "tnum"
        }
        private var boundRecord: StudyNotebook.Record? = null

        init {
            val copy = column(context)
            copy.addView(subject)
            ForestUi.add(copy, date, 5)
            root.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(
                duration,
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(context, 8f) }
            )
            root.setOnClickListener { boundRecord?.let(onClick) }
        }

        fun bind(item: StudyRecordListItem) {
            boundRecord = item.record
            dot.background = rounded(root.context, item.subjectColor, 0, 5)
            subject.text = item.subjectTitle
            date.text = item.subtitle
            duration.text = item.durationText
        }
    }

    class HairlineItemDecoration(density: Float) : RecyclerView.ItemDecoration() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, density * 0.75f)
            color = LINE
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val count = parent.childCount
            val lastIndex = state.itemCount - 1
            for (i in 0 until count) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos == RecyclerView.NO_POSITION || pos >= lastIndex) continue
                val y = child.bottom.toFloat()
                canvas.drawLine(0f, y, parent.width.toFloat(), y, paint)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<StudyRecordListItem>() {
        override fun areItemsTheSame(oldItem: StudyRecordListItem, newItem: StudyRecordListItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: StudyRecordListItem, newItem: StudyRecordListItem): Boolean =
            oldItem == newItem
    }

    companion object {
        fun buildItems(
            source: List<StudyNotebook.Record>,
            selection: StudySubject?,
            query: String,
            planTitleLookup: ((String) -> String)?
        ): List<StudyRecordListItem> {
            val normalizedQuery = query.trim().lowercase(Locale.ROOT)
            val dateFmt = SimpleDateFormat("yyyy/MM/dd · HH:mm", Locale.CHINA)
            val result = ArrayList<StudyRecordListItem>(source.size)
            for (record in source) {
                if (selection != null && record.subject != selection) continue
                if (normalizedQuery.isNotEmpty() &&
                    !record.note.lowercase(Locale.ROOT).contains(normalizedQuery)
                ) {
                    continue
                }
                var subtitle = dateFmt.format(Date(record.completedAt))
                if (planTitleLookup != null) {
                    subtitle += " · ${planTitleLookup(record.planId)}"
                }
                subtitle += when {
                    record.manual -> " · 手动补记"
                    record.adjusted -> " · 时长已修正"
                    else -> ""
                }
                result.add(
                    StudyRecordListItem(
                        id = record.id,
                        subjectTitle = record.subject.title,
                        subjectColor = record.subject.color,
                        subtitle = subtitle,
                        durationText = StudyFormat.duration(record.durationMillis),
                        record = record
                    )
                )
            }
            return result
        }
    }
}

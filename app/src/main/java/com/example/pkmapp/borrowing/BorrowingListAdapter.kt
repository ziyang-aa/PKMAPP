package com.example.pkmapp.borrowing

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.WOOD
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.dp
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface BorrowingListItem {
    val key: String

    data class DateHeader(
        override val key: String,
        val label: String,
        val firstGroup: Boolean
    ) : BorrowingListItem

    data class Entry(
        override val key: String,
        val recordId: String,
        val initials: String,
        val counterpartyLabel: String,
        val noteCopy: String,
        val amountLabel: String,
        val isLend: Boolean,
        val showTopHairline: Boolean
    ) : BorrowingListItem
}

class BorrowingListAdapter : ListAdapter<BorrowingListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is BorrowingListItem.DateHeader -> TYPE_HEADER
        is BorrowingListItem.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) HeaderHolder(parent.context) else EntryHolder(parent.context)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is BorrowingListItem.DateHeader -> (holder as HeaderHolder).bind(item)
            is BorrowingListItem.Entry -> (holder as EntryHolder).bind(item)
        }
    }

    private class HeaderHolder(ctx: Context) : RecyclerView.ViewHolder(
        text(ctx, "", 12, WOOD).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {
        private val label = itemView as TextView

        fun bind(item: BorrowingListItem.DateHeader) {
            val ctx = label.context
            label.text = item.label
            label.setPadding(
                dp(ctx, 2f),
                dp(ctx, if (item.firstGroup) 12f else 18f),
                dp(ctx, 2f),
                dp(ctx, 6f)
            )
        }
    }

    private class EntryHolder(ctx: Context) : RecyclerView.ViewHolder(
        column(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {
        private val container = itemView as LinearLayout
        private val hairline = View(ctx).apply { setBackgroundColor(0x165c4938) }
        private val rowView = row(ctx).apply {
            minimumHeight = dp(ctx, 60f)
            setPadding(dp(ctx, 2f), dp(ctx, 10f), dp(ctx, 2f), dp(ctx, 10f))
        }
        private val avatar = text(ctx, "", 13, DEEP).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        private val person = text(ctx, "", 15, 0xff243624.toInt()).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        private val note = text(ctx, "", 12, MUTED)
        private val amount = text(ctx, "", 16, DEEP).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }

        init {
            val hairlineHeight = max(1, (0.75f * ctx.resources.displayMetrics.density).roundToInt())
            container.addView(hairline, LinearLayout.LayoutParams(-1, hairlineHeight))

            rowView.addView(avatar, LinearLayout.LayoutParams(dp(ctx, 38f), dp(ctx, 38f)))
            val copy = column(ctx)
            copy.addView(person)
            ForestUi.add(copy, note, 3)
            val copyLp = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(ctx, 12f)
            }
            rowView.addView(copy, copyLp)
            rowView.addView(amount)
            container.addView(rowView, LinearLayout.LayoutParams(-1, -2))
        }

        fun bind(item: BorrowingListItem.Entry) {
            val ctx = container.context
            hairline.visibility = if (item.showTopHairline) View.VISIBLE else View.GONE
            val accentColor = if (item.isLend) 0xff9e4c37.toInt() else DEEP
            avatar.text = item.initials
            avatar.setTextColor(accentColor)
            avatar.background = rounded(ctx, if (item.isLend) 0xfff8ede6.toInt() else 0xffeef4ea.toInt(), 0, 999)
            person.text = item.counterpartyLabel
            note.text = item.noteCopy
            amount.text = item.amountLabel
            amount.setTextColor(accentColor)
            rowView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BorrowingListItem>() {
        override fun areItemsTheSame(oldItem: BorrowingListItem, newItem: BorrowingListItem): Boolean =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: BorrowingListItem, newItem: BorrowingListItem): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1

        @JvmStatic
        fun buildItems(records: List<BorrowingRecord>): List<BorrowingListItem> {
            val items = ArrayList<BorrowingListItem>()
            val groups = BorrowingDateGroup.from(records)
            for ((groupIdx, group) in groups.withIndex()) {
                items.add(
                    BorrowingListItem.DateHeader(
                        key = "hdr_${group.label}",
                        label = group.label,
                        firstGroup = groupIdx == 0
                    )
                )
                for ((rowIdx, record) in group.records.withIndex()) {
                    val isLend = record.direction == BorrowingDirection.LEND
                    val noteCopy = BorrowingRecordPresentation.directionLabel(record.direction) +
                        if (record.note.isEmpty()) "" else " · ${record.note}"
                    items.add(
                        BorrowingListItem.Entry(
                            key = "rec_${record.id}",
                            recordId = record.id,
                            initials = BorrowingRecordPresentation.initials(record.person),
                            counterpartyLabel = BorrowingRecordPresentation.counterpartyLabel(
                                record.direction,
                                record.person
                            ),
                            noteCopy = noteCopy,
                            amountLabel = BorrowingRecordPresentation.amountLabel(
                                record.direction,
                                record.amountInCents
                            ),
                            isLend = isLend,
                            showTopHairline = rowIdx > 0
                        )
                    )
                }
            }
            return items
        }
    }
}

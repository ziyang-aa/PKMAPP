package com.example.pkmapp.recurring

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pkmapp.R
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.forest.ForestSheetHelper
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.dp
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.record.CategoryIconResolver
import com.example.pkmapp.record.MoneyParser

internal data class RecurringRuleListItem(
    val rule: RecurringRule,
    val nextDueText: String
)

internal class RecurringRuleAdapter(
    private val onEdit: (RecurringRule) -> Unit,
    private val onToggleEnabled: (RecurringRule, Boolean) -> Unit
) : ListAdapter<RecurringRuleListItem, RecurringRuleAdapter.Holder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(parent.context, onEdit, onToggleEnabled)

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        context: Context,
        private val onEdit: (RecurringRule) -> Unit,
        private val onToggleEnabled: (RecurringRule, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 72f)
            setPadding(dp(context, 4f), dp(context, 12f), dp(context, 4f), dp(context, 12f))
            background = ripple(context, rounded(context, Color.TRANSPARENT, 0, 12), false)
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {
        private val root = itemView as LinearLayout
        private val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(context, 8f), dp(context, 8f), dp(context, 8f), dp(context, 8f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.paper_deep))
            }
        }
        private val title = text(context, "", 15, ForestSheetHelper.INK).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isSingleLine = true
        }
        private val meta = text(context, "", 12, MUTED).apply {
            fontFeatureSettings = "tnum"
        }
        private val amount = text(context, "", 15, DEEP).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            gravity = Gravity.END
        }
        private val modeTag = text(context, "", 11, MUTED).apply {
            gravity = Gravity.END
        }
        private val toggle = SwitchCompat(context).apply {
            background = null
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            )
            thumbTintList = ColorStateList(
                states,
                intArrayOf(ForestSheetHelper.PINE, ForestSheetHelper.PORCELAIN)
            )
            trackTintList = ColorStateList(
                states,
                intArrayOf(0x66456444, 0xffddd5c7.toInt())
            )
        }
        private var currentRule: RecurringRule? = null

        init {
            root.addView(icon, LinearLayout.LayoutParams(dp(context, 42f), dp(context, 42f)))

            val center = column(context)
            center.setPadding(dp(context, 12f), 0, dp(context, 8f), 0)
            center.addView(title)
            ForestUi.add(center, meta, 3)
            root.addView(center, LinearLayout.LayoutParams(0, -2, 1f))

            val right = column(context).apply {
                gravity = Gravity.END
                setPadding(0, 0, dp(context, 8f), 0)
            }
            right.addView(amount)
            ForestUi.add(right, modeTag, 2)
            root.addView(right, LinearLayout.LayoutParams(-2, -2))

            root.addView(toggle, LinearLayout.LayoutParams(-2, -2))
            root.setOnClickListener { currentRule?.let(onEdit) }
        }

        fun bind(item: RecurringRuleListItem) {
            val rule = item.rule
            currentRule = rule
            val ctx = root.context
            val income = rule.type == TransactionType.INCOME
            var resId = ctx.resources.getIdentifier(
                CategoryIconResolver.resourceName(rule.type, rule.category),
                "drawable",
                ctx.packageName
            )
            if (resId == 0) {
                resId = if (income) R.drawable.norm_type_income_allowance else R.drawable.norm_type_expense_custom
            }
            icon.setImageResource(resId)
            icon.alpha = if (rule.enabled) 1f else 0.45f

            title.text = rule.title
            title.setTextColor(if (rule.enabled) ForestSheetHelper.INK else MUTED)
            meta.text = if (rule.enabled) {
                "${rule.category} · ${rule.scheduleLabel()} · ${item.nextDueText}"
            } else {
                "${rule.category} · ${rule.scheduleLabel()} · 已暂停"
            }

            val color = if (!rule.enabled) {
                MUTED
            } else if (income) {
                ContextCompat.getColor(ctx, R.color.forest_green)
            } else {
                ContextCompat.getColor(ctx, R.color.expense_red)
            }
            amount.text = (if (income) "＋" else "−") + MoneyParser.formatCents(rule.amountCents)
            amount.setTextColor(color)
            modeTag.text = if (rule.autoPost) "自动入账" else "提醒确认"

            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = rule.enabled
            toggle.contentDescription = "${rule.title}启用开关"
            toggle.setOnCheckedChangeListener { _, checked ->
                currentRule?.let { onToggleEnabled(it, checked) }
            }
        }
    }

    class HairlineDecoration(density: Float) : RecyclerView.ItemDecoration() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, density * 0.75f)
            color = ForestSheetHelper.HAIRLINE
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val last = state.itemCount - 1
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos == RecyclerView.NO_POSITION || pos >= last) continue
                val y = child.bottom.toFloat()
                canvas.drawLine(0f, y, parent.width.toFloat(), y, paint)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RecurringRuleListItem>() {
        override fun areItemsTheSame(a: RecurringRuleListItem, b: RecurringRuleListItem): Boolean =
            a.rule.id == b.rule.id

        override fun areContentsTheSame(a: RecurringRuleListItem, b: RecurringRuleListItem): Boolean =
            a == b
    }
}

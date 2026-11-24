package com.example.pkmapp.exchange

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.WOOD
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.dp
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.roundToInt

data class ExchangePickerItem(
    val code: String,
    val name: String,
    val symbol: String,
    val selected: Boolean
)

class ExchangeMultiRateAdapter(
    private val onSelectQuote: Consumer<String>
) : ListAdapter<MultiCurrencyItem, ExchangeMultiRateAdapter.Holder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(parent.context, onSelectQuote)

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        ctx: Context,
        private val onSelectQuote: Consumer<String>
    ) : RecyclerView.ViewHolder(
        row(ctx).apply {
            minimumHeight = dp(ctx, 54f)
            setPadding(dp(ctx, 4f), dp(ctx, 10f), dp(ctx, 4f), dp(ctx, 10f))
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {
        private val item = itemView as LinearLayout
        private val codeTv = text(ctx, "", 14, 0xff243624.toInt())
        private val subTv = text(ctx, "", 11, MUTED).apply {
            fontFeatureSettings = "tnum"
        }
        private val valTv = text(ctx, "", 16, 0xff243624.toInt()).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        private var boundCode: String? = null

        init {
            val left = column(ctx)
            left.addView(codeTv)
            ForestUi.add(left, subTv, 3)
            item.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            item.addView(valTv)
            item.setOnClickListener { boundCode?.let(onSelectQuote::accept) }
            ForestMotion.bind(item)
        }

        fun bind(model: MultiCurrencyItem) {
            val ctx = item.context
            boundCode = model.code
            val ink = if (model.isCurrentQuote) DEEP else 0xff243624.toInt()
            item.background = ripple(
                ctx,
                rounded(ctx, if (model.isCurrentQuote) 0x16456444 else Color.TRANSPARENT, 0, 12),
                false
            )
            codeTv.text = "${model.code} · ${model.name}"
            codeTv.setTextColor(ink)
            codeTv.typeface = Typeface.create(
                "sans-serif-medium",
                if (model.isCurrentQuote) Typeface.BOLD else Typeface.NORMAL
            )
            if (model.unitRateText != null) {
                subTv.visibility = View.VISIBLE
                subTv.text = model.unitRateText
            } else {
                subTv.visibility = View.GONE
            }
            valTv.text = model.convertedText
            valTv.setTextColor(ink)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MultiCurrencyItem>() {
        override fun areItemsTheSame(oldItem: MultiCurrencyItem, newItem: MultiCurrencyItem): Boolean =
            oldItem.code == newItem.code

        override fun areContentsTheSame(oldItem: MultiCurrencyItem, newItem: MultiCurrencyItem): Boolean =
            oldItem == newItem
    }
}

class ExchangePickerAdapter(
    private val onSelectCode: Consumer<String>
) : ListAdapter<ExchangePickerItem, ExchangePickerAdapter.Holder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(parent.context, onSelectCode)

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        ctx: Context,
        private val onSelectCode: Consumer<String>
    ) : RecyclerView.ViewHolder(
        row(ctx).apply {
            minimumHeight = dp(ctx, 54f)
            setPadding(dp(ctx, 6f), dp(ctx, 10f), dp(ctx, 6f), dp(ctx, 10f))
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {
        private val rowView = itemView as LinearLayout
        private val symTv = text(ctx, "", 14, WOOD).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        private val nameTv = text(ctx, "", 15, 0xff243624.toInt())
        private val checkTv = text(ctx, "✓", 15, DEEP).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        private var boundCode: String? = null

        init {
            rowView.addView(symTv, LinearLayout.LayoutParams(dp(ctx, 42f), -2))
            val copy = column(ctx)
            copy.addView(nameTv)
            rowView.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            rowView.addView(checkTv)
            rowView.setOnClickListener { boundCode?.let(onSelectCode::accept) }
            ForestMotion.bind(rowView)
        }

        fun bind(item: ExchangePickerItem) {
            val ctx = rowView.context
            boundCode = item.code
            rowView.background = ripple(
                ctx,
                rounded(ctx, if (item.selected) 0xffeef4ea.toInt() else Color.TRANSPARENT, 0, 12),
                false
            )
            symTv.text = item.symbol
            symTv.setTextColor(if (item.selected) DEEP else WOOD)
            nameTv.text = "${item.code}  ${item.name}"
            nameTv.typeface = Typeface.create(
                "sans-serif-medium",
                if (item.selected) Typeface.BOLD else Typeface.NORMAL
            )
            checkTv.visibility = if (item.selected) View.VISIBLE else View.GONE
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ExchangePickerItem>() {
        override fun areItemsTheSame(oldItem: ExchangePickerItem, newItem: ExchangePickerItem): Boolean =
            oldItem.code == newItem.code

        override fun areContentsTheSame(oldItem: ExchangePickerItem, newItem: ExchangePickerItem): Boolean =
            oldItem == newItem
    }
}

internal class ExchangeHairlineDecoration(density: Float, hairlineColor: Int) : RecyclerView.ItemDecoration() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1, (0.75f * density).roundToInt()).toFloat()
        color = hairlineColor
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

object ExchangeCurrencyListBuilder {
    @JvmStatic
    fun filterCurrencies(
        currencies: List<CurrencyInfo>,
        query: String,
        selectedCode: String
    ): List<ExchangePickerItem> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        val out = ArrayList<ExchangePickerItem>(currencies.size)
        for (info in currencies) {
            if (normalized.isNotEmpty() &&
                !info.code.lowercase(Locale.ROOT).contains(normalized) &&
                !info.name.lowercase(Locale.ROOT).contains(normalized)
            ) {
                continue
            }
            out.add(
                ExchangePickerItem(
                    code = info.code,
                    name = info.name,
                    symbol = info.symbol,
                    selected = info.code == selectedCode
                )
            )
        }
        return out
    }
}

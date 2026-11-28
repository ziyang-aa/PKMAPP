package com.example.pkmapp.details

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pkmapp.data.Transaction
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestSheetHelper
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.dp
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.record.MoneyParser
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar
import java.util.LinkedHashSet
import kotlin.math.min

/**
 * Warm-porcelain global & multi-filter search sheet for Life Cabin transactions.
 */
object TransactionSearchSheet {

    @JvmStatic
    fun show(
        context: Context,
        sheet: BottomSheetDialog,
        ledgerName: String,
        selectedMonth: Calendar,
        transactionsSupplier: () -> List<Transaction>,
        onEdit: (Transaction) -> Unit,
        onDeleteRequested: (Transaction, () -> Unit) -> Unit
    ) {
        val monthYear = selectedMonth.get(Calendar.YEAR)
        val monthZeroBased = selectedMonth.get(Calendar.MONTH)
        val monthLabel = "${monthYear}年${monthZeroBased + 1}月"

        var scope = TransactionSearchFilter.Scope.ALL_TIME
        var filterType: TransactionType? = null
        var selectedCategory: String? = null
        var amountPanelExpanded = false

        val root = ForestSheetHelper.createSheetRoot(
            context,
            sheet,
            "搜索交易记录",
            "当前账本 · $ledgerName（支持跨月关键词、分类与金额筛选）"
        )

        // 1. Quiet inline search field + clear button
        val searchRow = row(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val searchInput = ForestSheetHelper.underlineInput(
            context,
            "搜索备注、分类或金额，如 咖啡 / 房租",
            InputType.TYPE_CLASS_TEXT
        ).apply {
            contentDescription = "搜索交易关键词"
        }
        val clearBtn = text(context, "清空", 12, MUTED).apply {
            visibility = View.GONE
            setPadding(dp(context, 10f), dp(context, 8f), dp(context, 4f), dp(context, 8f))
            background = ripple(context, rounded(context, Color.TRANSPARENT, 0, 8), false)
            contentDescription = "清空搜索词"
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, -2, 1f))
        searchRow.addView(clearBtn, LinearLayout.LayoutParams(-2, -2))
        root.addView(searchRow, LinearLayout.LayoutParams(-1, -2))

        // 2. Editorial filter chip strip (scrollable single line, zero nested boxes)
        val filterScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val filterStrip = row(context).apply { gravity = Gravity.CENTER_VERTICAL }
        filterScroll.addView(filterStrip)
        ForestUi.add(root, filterScroll, 12)

        val scopeChip = makeFilterChip(context, "全部时间")
        val typeAllChip = makeFilterChip(context, "全部")
        val typeExpenseChip = makeFilterChip(context, "支出")
        val typeIncomeChip = makeFilterChip(context, "收入")
        val amountToggleChip = makeFilterChip(context, "金额区间 ▾")
        val resetChip = text(context, "重置", 12, ForestSheetHelper.TERRACOTTA).apply {
            visibility = View.GONE
            setPadding(dp(context, 8f), dp(context, 6f), dp(context, 4f), dp(context, 6f))
            background = ripple(context, rounded(context, Color.TRANSPARENT, 0, 8), false)
        }
        val stripChips = listOf(scopeChip, typeAllChip, typeExpenseChip, typeIncomeChip, amountToggleChip)
        stripChips.forEachIndexed { idx, chip ->
            val lp = LinearLayout.LayoutParams(-2, dp(context, 32f))
            if (idx > 0) lp.marginStart = dp(context, 6f)
            filterStrip.addView(chip, lp)
        }
        filterStrip.addView(
            resetChip,
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(context, 6f) }
        )

        // 3. Category hairline strip (populated dynamically from ledger transactions)
        val categoryScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val categoryStrip = row(context).apply { gravity = Gravity.CENTER_VERTICAL }
        categoryScroll.addView(categoryStrip)
        ForestUi.add(root, categoryScroll, 8)

        // 4. Progressive disclosure: Min / Max amount inputs
        val amountRow = row(context).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER_VERTICAL
        }
        val minInput = ForestSheetHelper.underlineInput(
            context,
            "最低金额（元）",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply {
            textSize = 14f
            fontFeatureSettings = "tnum"
        }
        val dash = text(context, "至", 12, MUTED).apply {
            setPadding(dp(context, 10f), 0, dp(context, 10f), 0)
        }
        val maxInput = ForestSheetHelper.underlineInput(
            context,
            "最高金额（元）",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply {
            textSize = 14f
            fontFeatureSettings = "tnum"
        }
        amountRow.addView(minInput, LinearLayout.LayoutParams(0, -2, 1f))
        amountRow.addView(dash, LinearLayout.LayoutParams(-2, -2))
        amountRow.addView(maxInput, LinearLayout.LayoutParams(0, -2, 1f))
        ForestUi.add(root, amountRow, 8)

        // 5. Hairline live summary bar
        ForestSheetHelper.addHairline(root, 12, 8)
        val summaryRow = row(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val countText = text(context, "共 0 笔", 12, ForestSheetHelper.INK).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        val totalsText = text(context, "", 12, MUTED).apply {
            gravity = Gravity.END
            fontFeatureSettings = "tnum"
        }
        summaryRow.addView(countText, LinearLayout.LayoutParams(-2, -2))
        summaryRow.addView(totalsText, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(summaryRow, LinearLayout.LayoutParams(-1, -2))
        ForestSheetHelper.addHairline(root, 8, 6)

        // 6. Empty state + Results RecyclerView
        val emptyView = text(context, "没有找到符合条件的交易记录", 13, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 28f), 0, dp(context, 28f))
            visibility = View.GONE
        }
        root.addView(emptyView, LinearLayout.LayoutParams(-1, -2))

        lateinit var refresh: () -> Unit
        val adapter = TransactionListAdapter(
            { tx -> onDeleteRequested(tx) { refresh() } },
            { tx ->
                sheet.dismiss()
                onEdit(tx)
            }
        )
        val maxListHeight = dp(context, min(360f, context.resources.configuration.screenHeightDp * 0.44f))
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isNestedScrollingEnabled = true
        }
        root.addView(recycler, LinearLayout.LayoutParams(-1, maxListHeight))

        fun rebuildCategories(allTx: List<Transaction>) {
            categoryStrip.removeAllViews()
            val names = LinkedHashSet<String>()
            for (tx in allTx) {
                if (filterType == null || tx.type == filterType) {
                    names.add(tx.category)
                }
            }
            if (selectedCategory != null && !names.contains(selectedCategory)) {
                selectedCategory = null
            }
            if (names.isEmpty()) {
                categoryScroll.visibility = View.GONE
                return
            }
            categoryScroll.visibility = View.VISIBLE
            val options = ArrayList<String?>(names.size + 1).apply {
                add(null)
                addAll(names)
            }
            options.forEachIndexed { idx, cat ->
                val chip = makeFilterChip(context, cat ?: "全部分类")
                styleChip(context, chip, selectedCategory == cat)
                chip.setOnClickListener {
                    selectedCategory = cat
                    refresh()
                }
                val lp = LinearLayout.LayoutParams(-2, dp(context, 30f))
                if (idx > 0) lp.marginStart = dp(context, 6f)
                categoryStrip.addView(chip, lp)
            }
        }

        refresh = {
            val allTx = transactionsSupplier()
            rebuildCategories(allTx)

            val kw = searchInput.text?.toString().orEmpty()
            clearBtn.visibility = if (kw.isNotEmpty()) View.VISIBLE else View.GONE

            val minCents = TransactionSearchFilter.parseOptionalYuanToCents(minInput.text?.toString())
            val maxCents = TransactionSearchFilter.parseOptionalYuanToCents(maxInput.text?.toString())
            val query = TransactionSearchFilter.Query(
                keyword = kw,
                scope = scope,
                type = filterType,
                category = selectedCategory,
                minCents = minCents,
                maxCents = maxCents,
                monthYear = monthYear,
                monthZeroBased = monthZeroBased
            )
            val result = TransactionSearchFilter.filter(allTx, query)

            scopeChip.text = if (scope == TransactionSearchFilter.Scope.ALL_TIME) "全部时间 ▾" else "$monthLabel ▾"
            styleChip(context, scopeChip, scope == TransactionSearchFilter.Scope.CURRENT_MONTH)
            styleChip(context, typeAllChip, filterType == null)
            styleChip(context, typeExpenseChip, filterType == TransactionType.EXPENSE)
            styleChip(context, typeIncomeChip, filterType == TransactionType.INCOME)
            val amountActive = minCents != null || maxCents != null
            amountToggleChip.text = if (amountPanelExpanded || amountActive) "金额区间 ▴" else "金额区间 ▾"
            styleChip(context, amountToggleChip, amountActive)
            resetChip.visibility = if (query.hasActiveFilters || scope == TransactionSearchFilter.Scope.CURRENT_MONTH) {
                View.VISIBLE
            } else {
                View.GONE
            }

            countText.text = "共 ${result.items.size} 笔"
            val parts = ArrayList<String>(2)
            if (result.expenseCents > 0L || result.incomeCents == 0L) {
                parts.add("支出 ${MoneyParser.formatCents(result.expenseCents)}")
            }
            if (result.incomeCents > 0L) {
                parts.add("收入 ${MoneyParser.formatCents(result.incomeCents)}")
            }
            totalsText.text = parts.joinToString("  ·  ")

            if (result.items.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recycler.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recycler.visibility = View.VISIBLE
                val listItems = DetailsListItem.fromTransactions(result.items, System.currentTimeMillis())
                adapter.submit(listItems)
            }
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                refresh()
            }
        }
        searchInput.addTextChangedListener(watcher)
        minInput.addTextChangedListener(watcher)
        maxInput.addTextChangedListener(watcher)

        clearBtn.setOnClickListener {
            searchInput.setText("")
            searchInput.requestFocus()
        }
        scopeChip.setOnClickListener {
            scope = if (scope == TransactionSearchFilter.Scope.ALL_TIME) {
                TransactionSearchFilter.Scope.CURRENT_MONTH
            } else {
                TransactionSearchFilter.Scope.ALL_TIME
            }
            refresh()
        }
        typeAllChip.setOnClickListener {
            filterType = null
            refresh()
        }
        typeExpenseChip.setOnClickListener {
            filterType = TransactionType.EXPENSE
            refresh()
        }
        typeIncomeChip.setOnClickListener {
            filterType = TransactionType.INCOME
            refresh()
        }
        amountToggleChip.setOnClickListener {
            amountPanelExpanded = !amountPanelExpanded
            amountRow.visibility = if (amountPanelExpanded) View.VISIBLE else View.GONE
            refresh()
        }
        resetChip.setOnClickListener {
            scope = TransactionSearchFilter.Scope.ALL_TIME
            filterType = null
            selectedCategory = null
            amountPanelExpanded = false
            amountRow.visibility = View.GONE
            searchInput.setText("")
            minInput.setText("")
            maxInput.setText("")
            refresh()
        }

        refresh()
        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root)
        sheet.show()
    }

    private fun makeFilterChip(context: Context, label: String): TextView =
        text(context, label, 12, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(dp(context, 11f), 0, dp(context, 11f), 0)
            isSingleLine = true
            ForestMotion.bind(this)
        }

    private fun styleChip(context: Context, chip: TextView, selected: Boolean) {
        chip.setTextColor(if (selected) DEEP else MUTED)
        chip.typeface = Typeface.create(
            if (selected) "sans-serif-medium" else "sans-serif",
            if (selected) Typeface.BOLD else Typeface.NORMAL
        )
        chip.background = ripple(
            context,
            rounded(
                context,
                if (selected) 0xffeef4ea.toInt() else ForestSheetHelper.PORCELAIN,
                if (selected) 0x44315e3a else 0x245c4938,
                999
            ),
            false
        )
    }
}

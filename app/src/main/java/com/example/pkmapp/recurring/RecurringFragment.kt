package com.example.pkmapp.recurring

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pkmapp.MainActivity
import com.example.pkmapp.R
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.forest.ForestCalendarView
import com.example.pkmapp.forest.ForestDialogBuilder
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestSheetHelper
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.dp
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.life.LifeSharedViewModel
import com.example.pkmapp.record.CategoryStore
import com.example.pkmapp.record.MoneyParser
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Quiet-luxury editorial hairline page for fixed recurring bills & subscriptions.
 */
class RecurringFragment : Fragment() {

    private lateinit var viewModel: LifeSharedViewModel
    private lateinit var store: RecurringRuleStore
    private lateinit var ruleAdapter: RecurringRuleAdapter
    private var activeSheet: BottomSheetDialog? = null

    private lateinit var ledgerCaption: TextView
    private lateinit var expenseTotalView: TextView
    private lateinit var incomeTotalView: TextView
    private lateinit var countBadgeView: TextView
    private lateinit var pendingSection: LinearLayout
    private lateinit var pendingListContainer: LinearLayout
    private lateinit var emptySection: LinearLayout
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        store = RecurringRuleStore(ctx)

        val scroll = NestedScrollView(ctx).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, 0, 0, dp(ctx, 112f))
        }
        val content = column(ctx).apply {
            setPadding(dp(ctx, 16f), dp(ctx, 16f), dp(ctx, 16f), dp(ctx, 20f))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))

        // 1. Unified Top Bar (56dp return door left + 24sp bold title right)
        val topBar = row(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ctx, 68f)
        }
        val backBtn = ImageButton(ctx).apply {
            id = R.id.page_back_button
            setImageResource(R.drawable.return_home_door)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(0, 0, 0, 0)
            contentDescription = "返回上一页"
            background = ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 999), false)
            setOnClickListener { (requireActivity() as MainActivity).navigateBackFromLifeTool() }
        }
        val pageTitle = text(ctx, "周期账单", 24, DEEP).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            gravity = Gravity.END
        }
        topBar.addView(backBtn, LinearLayout.LayoutParams(dp(ctx, 56f), dp(ctx, 56f)))
        topBar.addView(pageTitle, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        // 2. Editorial Hairline Header Summary (zero nested cards)
        val headerRow = row(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 6f), 0, dp(ctx, 10f))
        }
        val headerCopy = column(ctx)
        ledgerCaption = text(ctx, "当前账本", 12, MUTED)
        countBadgeView = text(ctx, "0 条启用", 12, DEEP).apply {
            fontFeatureSettings = "tnum"
        }
        headerCopy.addView(ledgerCaption)
        ForestUi.add(headerCopy, countBadgeView, 2)
        headerRow.addView(headerCopy, LinearLayout.LayoutParams(0, -2, 1f))

        val addBtn = text(ctx, "＋ 新建规则", 13, DEEP).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumHeight = dp(ctx, 36f)
            setPadding(dp(ctx, 14f), dp(ctx, 6f), dp(ctx, 14f), dp(ctx, 6f))
            background = ripple(ctx, rounded(ctx, ForestSheetHelper.PORCELAIN, 0x38315e3a, 999), false)
            contentDescription = "新建周期账单规则"
            setOnClickListener { openRuleSheet(null) }
            ForestMotion.bind(this)
        }
        headerRow.addView(addBtn, LinearLayout.LayoutParams(-2, -2))
        content.addView(headerRow, LinearLayout.LayoutParams(-1, -2))
        ForestSheetHelper.addHairline(content, 0, 12)

        // Monthly Equivalent Fixed Totals Row
        val totalsRow = row(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 2f), 0, dp(ctx, 12f))
        }
        val expCol = column(ctx)
        expCol.addView(text(ctx, "月均固定支出", 11, MUTED))
        expenseTotalView = text(ctx, "¥0.00", 22, ContextCompat.getColor(ctx, R.color.expense_red)).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        ForestUi.add(expCol, expenseTotalView, 3)

        val midLine = View(ctx).apply { setBackgroundColor(ForestSheetHelper.HAIRLINE) }
        val incCol = column(ctx).apply { setPadding(dp(ctx, 16f), 0, 0, 0) }
        incCol.addView(text(ctx, "月均固定收入", 11, MUTED))
        incomeTotalView = text(ctx, "¥0.00", 22, DEEP).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        ForestUi.add(incCol, incomeTotalView, 3)

        totalsRow.addView(expCol, LinearLayout.LayoutParams(0, -2, 1f))
        totalsRow.addView(
            midLine,
            LinearLayout.LayoutParams(
                maxOf(1, (0.75f * resources.displayMetrics.density).toInt()),
                dp(ctx, 36f)
            )
        )
        totalsRow.addView(incCol, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(totalsRow, LinearLayout.LayoutParams(-1, -2))
        ForestSheetHelper.addHairline(content, 0, 12)

        // 3. Pending Confirmation Section (visible only when reminder-only bills are due)
        pendingSection = column(ctx).apply { visibility = View.GONE }
        pendingSection.addView(
            text(ctx, "待确认入账", 14, ForestSheetHelper.TERRACOTTA).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
        )
        pendingListContainer = column(ctx)
        ForestUi.add(pendingSection, pendingListContainer, 6)
        ForestSheetHelper.addHairline(pendingSection, 10, 14)
        content.addView(pendingSection, LinearLayout.LayoutParams(-1, -2))

        // 4. Rules List Header + RecyclerView
        content.addView(
            text(ctx, "固定收支与订阅规则", 14, MUTED).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
        )

        emptySection = column(ctx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(ctx, 36f), 0, dp(ctx, 28f))
            visibility = View.GONE
        }
        emptySection.addView(
            text(ctx, "还没有设置固定周期账单", 14, MUTED).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, -2)
        )
        emptySection.addView(
            text(ctx, "房租、会员订阅或固定工资，可按月自动记账并联动预算", 12, MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 16f), dp(ctx, 6f), dp(ctx, 16f), dp(ctx, 14f))
            }
        )
        val emptyCta = text(ctx, "新建第一条规则", 13, DEEP).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumHeight = dp(ctx, 40f)
            setPadding(dp(ctx, 18f), dp(ctx, 8f), dp(ctx, 18f), dp(ctx, 8f))
            background = ripple(ctx, rounded(ctx, ForestSheetHelper.PORCELAIN, 0x38315e3a, 999), false)
            setOnClickListener { openRuleSheet(null) }
        }
        emptySection.addView(emptyCta, LinearLayout.LayoutParams(-2, -2))
        content.addView(emptySection, LinearLayout.LayoutParams(-1, -2))

        ruleAdapter = RecurringRuleAdapter(
            onEdit = { openRuleSheet(it) },
            onToggleEnabled = { rule, checked ->
                store.setEnabled(rule.id, checked)
                if (checked) {
                    RecurringTransactionEngine.processDueRules(ctx)
                    viewModel.refresh()
                }
                renderRules()
            }
        )
        recyclerView = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = ruleAdapter
            itemAnimator = null
            isNestedScrollingEnabled = false
            addItemDecoration(RecurringRuleAdapter.HairlineDecoration(resources.displayMetrics.density))
        }
        ForestUi.add(content, recyclerView, 6)

        val baseTop = content.paddingTop
        val baseBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            content.setPadding(content.paddingLeft, baseTop + bars.top, content.paddingRight, content.paddingBottom)
            scroll.setPadding(0, 0, 0, baseBottom + bars.bottom)
            insets
        }

        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = LifeSharedViewModel.get(requireActivity())
        viewModel.observe(viewLifecycleOwner) { renderRules() }
    }

    private fun renderRules() {
        if (!isAdded) return
        val ctx = requireContext()
        val ledger = viewModel.currentSnapshot.currentLedger
        val today = RecurringTransactionEngine.todayDateKey()
        val rules = store.getForLedger(ledger.id)
        val activeCount = rules.count { it.enabled }

        ledgerCaption.text = "当前账本 · ${ledger.name}"
        countBadgeView.text = "${activeCount} 条启用 · 共 ${rules.size} 条"
        expenseTotalView.text = MoneyParser.formatCents(
            RecurringTransactionEngine.monthlyEquivalentCents(rules, TransactionType.EXPENSE)
        )
        incomeTotalView.text = MoneyParser.formatCents(
            RecurringTransactionEngine.monthlyEquivalentCents(rules, TransactionType.INCOME)
        )

        // Pending confirmation bills
        val pending = RecurringTransactionEngine.pendingForLedger(ctx, ledger.id, today)
        pendingListContainer.removeAllViews()
        if (pending.isEmpty()) {
            pendingSection.visibility = View.GONE
        } else {
            pendingSection.visibility = View.VISIBLE
            pending.forEachIndexed { idx, item ->
                val pRow = row(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(ctx, 8f), 0, dp(ctx, 8f))
                }
                val info = column(ctx)
                info.addView(
                    text(ctx, "${item.rule.title} · ${MoneyParser.formatCents(item.rule.amountCents)}", 14, ForestSheetHelper.INK).apply {
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        fontFeatureSettings = "tnum"
                    }
                )
                ForestUi.add(
                    info,
                    text(ctx, "${item.dueDateKey} · ${item.rule.category}", 12, MUTED).apply {
                        fontFeatureSettings = "tnum"
                    },
                    2
                )
                pRow.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

                val skipBtn = text(ctx, "跳过", 12, MUTED).apply {
                    setPadding(dp(ctx, 10f), dp(ctx, 6f), dp(ctx, 10f), dp(ctx, 6f))
                    background = ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 999), false)
                    setOnClickListener {
                        RecurringTransactionEngine.skipPendingItem(ctx, item.rule, item.dueDateKey)
                        renderRules()
                        Toast.makeText(ctx, "已跳过本期 ${item.rule.title}", Toast.LENGTH_SHORT).show()
                    }
                }
                val confirmBtn = text(ctx, "确认入账", 12, ForestSheetHelper.PORCELAIN).apply {
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setPadding(dp(ctx, 12f), dp(ctx, 6f), dp(ctx, 12f), dp(ctx, 6f))
                    background = ripple(ctx, rounded(ctx, ForestSheetHelper.PINE, 0, 999), true)
                    setOnClickListener {
                        RecurringTransactionEngine.confirmPendingItem(ctx, item.rule, item.dueDateKey)
                        viewModel.refresh()
                        renderRules()
                        Toast.makeText(ctx, "已入账 ${item.rule.title}", Toast.LENGTH_SHORT).show()
                    }
                }
                pRow.addView(skipBtn, LinearLayout.LayoutParams(-2, -2))
                pRow.addView(confirmBtn, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(ctx, 6f) })
                pendingListContainer.addView(pRow, LinearLayout.LayoutParams(-1, -2))
                if (idx < pending.lastIndex) {
                    ForestSheetHelper.addHairline(pendingListContainer, 0, 0)
                }
            }
        }

        if (rules.isEmpty()) {
            emptySection.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptySection.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            val items = rules.map { rule ->
                val nextKey = RecurringTransactionEngine.nextDueDateKey(rule, today)
                val days = RecurringTransactionEngine.daysUntil(today, nextKey)
                val nextText = when {
                    days <= 0 -> "今日到期"
                    days == 1 -> "明天入账"
                    else -> "${days}天后 (${nextKey.substring(5)})"
                }
                RecurringRuleListItem(rule, nextText)
            }
            ruleAdapter.submitList(items)
        }
    }

    private fun openRuleSheet(editing: RecurringRule?) {
        val ctx = requireContext()
        val ledger = viewModel.currentSnapshot.currentLedger
        val categoryStore = CategoryStore(ctx)
        val today = RecurringTransactionEngine.todayDateKey()
        val cal = Calendar.getInstance()

        var txType = editing?.type ?: TransactionType.EXPENSE
        var selectedCategory = editing?.category ?: categoryStore.getCategories(txType).firstOrNull().orEmpty()
        var frequency = editing?.frequency ?: RecurringFrequency.MONTHLY
        var dayOfMonth = editing?.dayOfMonth ?: cal.get(Calendar.DAY_OF_MONTH)
        var dayOfWeek = editing?.dayOfWeek ?: run {
            val d = cal.get(Calendar.DAY_OF_WEEK)
            if (d == Calendar.SUNDAY) 7 else d - 1
        }
        var monthOfYear = editing?.monthOfYear ?: (cal.get(Calendar.MONTH) + 1)
        var startDateKey = editing?.startDateKey?.ifBlank { today } ?: today
        var autoPost = editing?.autoPost ?: true

        activeSheet?.dismiss()
        val sheet = ForestSheetHelper.createSheet(ctx)
        activeSheet = sheet
        sheet.setOnDismissListener { if (activeSheet == sheet) activeSheet = null }

        val root = ForestSheetHelper.createSheetRoot(
            ctx,
            sheet,
            if (editing == null) "新建周期账单" else "编辑周期账单",
            "归属账本 · ${ledger.name}"
        )

        // 1. Type selector (支出 / 收入)
        val typeRow = row(ctx)
        val expenseChip = makeChoiceChip(ctx, "支出")
        val incomeChip = makeChoiceChip(ctx, "收入")
        typeRow.addView(expenseChip, LinearLayout.LayoutParams(-2, dp(ctx, 34f)))
        typeRow.addView(incomeChip, LinearLayout.LayoutParams(-2, dp(ctx, 34f)).apply { marginStart = dp(ctx, 8f) })
        root.addView(typeRow, LinearLayout.LayoutParams(-1, -2))

        // 2. Title & Amount
        ForestUi.add(root, ForestSheetHelper.fieldLabel(ctx, "规则名称"), 12)
        val titleInput = ForestSheetHelper.underlineInput(
            ctx,
            "例如：房租、iCloud 订阅、固定工资",
            InputType.TYPE_CLASS_TEXT
        ).apply {
            if (editing != null) setText(editing.title)
        }
        root.addView(titleInput, LinearLayout.LayoutParams(-1, -2))

        ForestUi.add(root, ForestSheetHelper.fieldLabel(ctx, "金额（元）"), 12)
        val amountInput = ForestSheetHelper.underlineInput(
            ctx,
            "例如：2500",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply {
            textSize = 22f
            typeface = Typeface.create("serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            if (editing != null) setText(formatPlainYuan(editing.amountCents))
        }
        root.addView(amountInput, LinearLayout.LayoutParams(-1, -2))

        // 3. Category chips
        ForestUi.add(root, ForestSheetHelper.fieldLabel(ctx, "分类"), 12)
        val catScroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        val catStrip = row(ctx)
        catScroll.addView(catStrip)
        root.addView(catScroll, LinearLayout.LayoutParams(-1, -2))

        fun renderCategories() {
            catStrip.removeAllViews()
            val cats = categoryStore.getCategories(txType)
            if (selectedCategory.isBlank() || !cats.contains(selectedCategory)) {
                selectedCategory = cats.firstOrNull() ?: if (txType == TransactionType.INCOME) "工资" else "餐饮"
            }
            cats.forEachIndexed { idx, name ->
                val chip = makeChoiceChip(ctx, name)
                styleChoiceChip(ctx, chip, name == selectedCategory)
                chip.setOnClickListener {
                    selectedCategory = name
                    renderCategories()
                }
                val lp = LinearLayout.LayoutParams(-2, dp(ctx, 32f))
                if (idx > 0) lp.marginStart = dp(ctx, 6f)
                catStrip.addView(chip, lp)
            }
        }

        // 4. Frequency & Day selection
        ForestUi.add(root, ForestSheetHelper.fieldLabel(ctx, "重复周期"), 12)
        val freqRow = row(ctx)
        lateinit var renderScheduleControls: () -> Unit
        val freqChips = RecurringFrequency.entries.map { f ->
            makeChoiceChip(ctx, f.label).also { chip ->
                chip.setOnClickListener {
                    frequency = f
                    renderScheduleControls()
                }
            }
        }
        freqChips.forEachIndexed { idx, chip ->
            val lp = LinearLayout.LayoutParams(-2, dp(ctx, 32f))
            if (idx > 0) lp.marginStart = dp(ctx, 8f)
            freqRow.addView(chip, lp)
        }
        root.addView(freqRow, LinearLayout.LayoutParams(-1, -2))

        val dayScroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        val dayStrip = row(ctx)
        dayScroll.addView(dayStrip)
        ForestUi.add(root, dayScroll, 8)

        fun renderDayStrip() {
            dayStrip.removeAllViews()
            when (frequency) {
                RecurringFrequency.MONTHLY -> {
                    for (d in 1..31) {
                        val chip = makeChoiceChip(ctx, "${d}日")
                        styleChoiceChip(ctx, chip, d == dayOfMonth)
                        chip.setOnClickListener {
                            dayOfMonth = d
                            renderDayStrip()
                        }
                        val lp = LinearLayout.LayoutParams(-2, dp(ctx, 30f))
                        if (d > 1) lp.marginStart = dp(ctx, 6f)
                        dayStrip.addView(chip, lp)
                    }
                }
                RecurringFrequency.WEEKLY -> {
                    val labels = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                    labels.forEachIndexed { idx, label ->
                        val dow = idx + 1
                        val chip = makeChoiceChip(ctx, label)
                        styleChoiceChip(ctx, chip, dow == dayOfWeek)
                        chip.setOnClickListener {
                            dayOfWeek = dow
                            renderDayStrip()
                        }
                        val lp = LinearLayout.LayoutParams(-2, dp(ctx, 30f))
                        if (idx > 0) lp.marginStart = dp(ctx, 6f)
                        dayStrip.addView(chip, lp)
                    }
                }
                RecurringFrequency.YEARLY -> {
                    for (m in 1..12) {
                        val chip = makeChoiceChip(ctx, "${m}月${dayOfMonth}日")
                        styleChoiceChip(ctx, chip, m == monthOfYear)
                        chip.setOnClickListener {
                            monthOfYear = m
                            renderDayStrip()
                        }
                        val lp = LinearLayout.LayoutParams(-2, dp(ctx, 30f))
                        if (m > 1) lp.marginStart = dp(ctx, 6f)
                        dayStrip.addView(chip, lp)
                    }
                }
            }
        }

        // 5. Start Date (self-drawn ForestCalendarView picker — zero native DatePicker!)
        ForestUi.add(root, ForestSheetHelper.fieldLabel(ctx, "起算日期"), 12)
        val startRow = row(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val startDateBtn = makeChoiceChip(ctx, "$startDateKey ▾").apply {
            fontFeatureSettings = "tnum"
        }
        val todayQuickBtn = ForestSheetHelper.quickChip(ctx, "今天") {
            startDateKey = today
            startDateBtn.text = "$startDateKey ▾"
        }
        startDateBtn.setOnClickListener {
            openForestDatePicker(ctx, startDateKey) { picked ->
                startDateKey = picked
                startDateBtn.text = "$startDateKey ▾"
            }
        }
        startRow.addView(startDateBtn, LinearLayout.LayoutParams(-2, dp(ctx, 34f)))
        startRow.addView(todayQuickBtn, LinearLayout.LayoutParams(-2, dp(ctx, 32f)).apply { marginStart = dp(ctx, 8f) })
        root.addView(startRow, LinearLayout.LayoutParams(-1, -2))

        // 6. Posting mode (Auto vs Reminder)
        ForestUi.add(root, ForestSheetHelper.fieldLabel(ctx, "到期入账方式"), 12)
        val modeRow = row(ctx)
        val autoChip = makeChoiceChip(ctx, "自动入账")
        val manualChip = makeChoiceChip(ctx, "提醒待确认")
        modeRow.addView(autoChip, LinearLayout.LayoutParams(-2, dp(ctx, 32f)))
        modeRow.addView(manualChip, LinearLayout.LayoutParams(-2, dp(ctx, 32f)).apply { marginStart = dp(ctx, 8f) })
        root.addView(modeRow, LinearLayout.LayoutParams(-1, -2))

        // 7. Optional Note
        ForestUi.add(root, ForestSheetHelper.fieldLabel(ctx, "备注（可选）"), 12)
        val noteInput = ForestSheetHelper.underlineInput(ctx, "例如：自动扣款卡尾号 8821", InputType.TYPE_CLASS_TEXT).apply {
            if (editing != null) setText(editing.note)
        }
        root.addView(noteInput, LinearLayout.LayoutParams(-1, -2))

        fun renderTypeAndMode() {
            styleChoiceChip(ctx, expenseChip, txType == TransactionType.EXPENSE)
            styleChoiceChip(ctx, incomeChip, txType == TransactionType.INCOME)
            styleChoiceChip(ctx, autoChip, autoPost)
            styleChoiceChip(ctx, manualChip, !autoPost)
        }

        renderScheduleControls = {
            RecurringFrequency.entries.forEachIndexed { idx, f ->
                styleChoiceChip(ctx, freqChips[idx], f == frequency)
            }
            renderDayStrip()
        }

        expenseChip.setOnClickListener {
            txType = TransactionType.EXPENSE
            renderTypeAndMode()
            renderCategories()
        }
        incomeChip.setOnClickListener {
            txType = TransactionType.INCOME
            renderTypeAndMode()
            renderCategories()
        }
        autoChip.setOnClickListener {
            autoPost = true
            renderTypeAndMode()
        }
        manualChip.setOnClickListener {
            autoPost = false
            renderTypeAndMode()
        }

        renderTypeAndMode()
        renderCategories()
        renderScheduleControls()

        // 8. Save CTA
        val saveBtn = ForestSheetHelper.primaryButton(ctx, if (editing == null) "保存周期规则" else "保存修改") {
            val title = titleInput.text?.toString()?.trim().orEmpty()
            if (title.isEmpty()) {
                titleInput.error = "请填写规则名称"
                titleInput.requestFocus()
                return@primaryButton
            }
            val rawAmount = amountInput.text?.toString()?.trim().orEmpty()
            val cents = try {
                MoneyParser.parseYuanToCents(rawAmount)
            } catch (e: IllegalArgumentException) {
                amountInput.error = e.message ?: "请输入有效金额"
                amountInput.requestFocus()
                return@primaryButton
            }

            val rule = RecurringRule(
                id = editing?.id ?: ("r_" + UUID.randomUUID().toString().replace("-", "").take(12)),
                ledgerId = editing?.ledgerId ?: ledger.id,
                title = title,
                type = txType,
                category = selectedCategory,
                amountCents = cents,
                frequency = frequency,
                dayOfMonth = dayOfMonth,
                dayOfWeek = dayOfWeek,
                monthOfYear = monthOfYear,
                startDateKey = startDateKey,
                lastGeneratedDateKey = editing?.lastGeneratedDateKey ?: "",
                autoPost = autoPost,
                enabled = editing?.enabled ?: true,
                note = noteInput.text?.toString()?.trim().orEmpty(),
                sortOrder = editing?.sortOrder ?: 0
            )
            store.save(rule)
            val summary = RecurringTransactionEngine.processDueRules(ctx)
            viewModel.refresh()
            sheet.dismiss()
            renderRules()
            val msg = if (summary.postedCount > 0) {
                "已保存并自动入账 ${summary.postedCount} 期"
            } else {
                "已保存周期规则「$title」"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
        ForestUi.add(root, saveBtn, 18)

        if (editing != null) {
            val deleteBtn = ForestSheetHelper.quietAction(ctx, "删除这条规则", ForestSheetHelper.TERRACOTTA) {
                newDeleteConfirm(ctx, editing, sheet)
            }
            ForestUi.add(root, deleteBtn, 6)
        }

        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root)
        sheet.show()
    }

    private fun newDeleteConfirm(ctx: Context, rule: RecurringRule, parentSheet: BottomSheetDialog) {
        ForestDialogBuilder(ctx)
            .setTitle("删除周期规则？")
            .setMessage("删除「${rule.title}」后将不再自动记账，已生成的历史流水不会被删除。")
            .setNegativeButton("保留规则", null)
            .setPositiveButton("确认删除") { _, _ ->
                store.delete(rule.id)
                parentSheet.dismiss()
                renderRules()
                Toast.makeText(ctx, "已删除周期规则", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun openForestDatePicker(ctx: Context, currentKey: String, onPicked: (String) -> Unit) {
        val pickerSheet = ForestSheetHelper.createSheet(ctx)
        val root = ForestSheetHelper.createSheetRoot(ctx, pickerSheet, "选择起算日期", "规则将从该日期起按周期生效")
        val calView = ForestCalendarView(ctx, currentKey, emptyMap()) { picked ->
            onPicked(picked)
            pickerSheet.dismiss()
        }
        root.addView(calView, LinearLayout.LayoutParams(-1, -2))
        ForestSheetHelper.attachContentWithImeAvoidance(pickerSheet, root)
        pickerSheet.show()
    }

    private fun makeChoiceChip(ctx: Context, label: String): TextView =
        text(ctx, label, 12, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 12f), 0, dp(ctx, 12f), 0)
            isSingleLine = true
            ForestMotion.bind(this)
        }

    private fun styleChoiceChip(ctx: Context, chip: TextView, selected: Boolean) {
        chip.setTextColor(if (selected) DEEP else MUTED)
        chip.typeface = Typeface.create(
            if (selected) "sans-serif-medium" else "sans-serif",
            if (selected) Typeface.BOLD else Typeface.NORMAL
        )
        chip.background = ripple(
            ctx,
            rounded(
                ctx,
                if (selected) 0xffeef4ea.toInt() else ForestSheetHelper.PORCELAIN,
                if (selected) 0x44315e3a else 0x245c4938,
                999
            ),
            false
        )
    }

    private fun formatPlainYuan(cents: Long): String {
        val bd = BigDecimal.valueOf(cents, 2).stripTrailingZeros()
        return if (bd.scale() <= 0) bd.toBigInteger().toString() else bd.toPlainString()
    }

    override fun onDestroyView() {
        activeSheet?.dismiss()
        activeSheet = null
        super.onDestroyView()
    }
}

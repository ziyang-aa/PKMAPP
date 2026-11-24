package com.example.pkmapp.profile

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.pkmapp.R
import com.example.pkmapp.data.Ledger
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.forest.ForestDialogBuilder
import com.example.pkmapp.life.LifeSharedViewModel
import com.example.pkmapp.record.MoneyParser
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.math.BigDecimal

internal class AssetAndLedgerDialogs(
    private val contextProvider: () -> Context,
    private val viewModel: LifeSharedViewModel
) {
    private var activeSheet: BottomSheetDialog? = null

    fun dismissActiveSheet() {
        activeSheet?.dismiss()
        activeSheet = null
    }

    fun showAssetManager() {
        val context = contextProvider()
        val dialog = BottomSheetDialog(context)
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_profile_assets, null)
        val initialAssets = root.findViewById<EditText>(R.id.profile_initial_assets_input)
        val initialLiabilities = root.findViewById<EditText>(R.id.profile_initial_liabilities_input)
        val current = root.findViewById<TextView>(R.id.profile_asset_dialog_current)
        val netWorth = root.findViewById<TextView>(R.id.profile_asset_dialog_net_worth)
        val liabilities = root.findViewById<TextView>(R.id.profile_asset_dialog_liability)

        val snapshot = viewModel.currentSnapshot.assetSnapshot
        initialAssets.setText(BigDecimal.valueOf(snapshot.initialAssetsInCents, 2).toPlainString())
        initialLiabilities.setText(BigDecimal.valueOf(snapshot.liabilitiesInCents, 2).toPlainString())
        current.text = MoneyParser.formatCents(snapshot.currentAssetsInCents)
        netWorth.text = MoneyParser.formatCents(snapshot.netWorthInCents)
        liabilities.text = MoneyParser.formatCents(snapshot.liabilitiesInCents)

        root.findViewById<View>(R.id.profile_asset_dialog_close).setOnClickListener {
            dialog.dismiss()
        }
        root.findViewById<View>(R.id.profile_asset_dialog_cancel).setOnClickListener {
            dialog.dismiss()
        }
        root.findViewById<View>(R.id.profile_asset_dialog_save).setOnClickListener {
            val initialAssetsCents = try {
                MoneyParser.parseYuanToCentsAllowZero(initialAssets.text.toString())
            } catch (e: IllegalArgumentException) {
                initialAssets.error = e.message
                return@setOnClickListener
            }
            val initialLiabilitiesCents = try {
                MoneyParser.parseYuanToCentsAllowZero(initialLiabilities.text.toString())
            } catch (e: IllegalArgumentException) {
                initialLiabilities.error = e.message
                return@setOnClickListener
            }
            viewModel.saveStartingAssets(initialAssetsCents, initialLiabilitiesCents)
            dialog.dismiss()
            Toast.makeText(context, "资产底子已保存，之后会随收支更新", Toast.LENGTH_SHORT).show()
        }
        showPaperSheet(dialog, root)
    }

    fun showCategoryManager() {
        val context = contextProvider()
        val dialog = BottomSheetDialog(context)
        val root = managementRoot(
            context,
            "CATEGORY MANAGEMENT",
            "管理记账分类",
            "删除只影响以后记账，历史账单会保留原分类。\n在记账页点击“自定义”可以新增分类。"
        )
        val listScroll = ScrollView(context)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val rebuildList = {
            list.removeAllViews()
            addCategorySection(context, list, TransactionType.EXPENSE, "支出分类")
            addCategorySection(context, list, TransactionType.INCOME, "收入分类")
        }
        rebuildList()
        listScroll.addView(
            list,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val scrollParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 430)
        ).apply { topMargin = dp(context, 12) }
        root.addView(listScroll, scrollParams)
        val done = managementAction(context, "完成", R.color.forest_green).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val doneParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 48)
        ).apply { topMargin = dp(context, 8) }
        root.addView(done, doneParams)
        showPaperSheet(dialog, root)
    }

    private fun addCategorySection(
        context: Context,
        list: LinearLayout,
        type: TransactionType,
        title: String
    ) {
        val section = managementLabel(context, title, 14, R.color.wood_brown)
        val sectionParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 32)
        ).apply { topMargin = dp(context, 8) }
        list.addView(section, sectionParams)

        for (category in viewModel.getCategories(type)) {
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 12), 0, dp(context, 8), 0)
                setBackgroundResource(R.drawable.bg_details_picker_option)
            }
            val name = managementLabel(context, category, 14, R.color.ink)
            row.addView(name, LinearLayout.LayoutParams(0, dp(context, 52), 1f))
            val delete = managementAction(context, "删除", R.color.expense_red).apply {
                contentDescription = "删除分类$category"
                setOnClickListener { confirmDeleteCategory(context, list, row, type, category) }
            }
            row.addView(delete, LinearLayout.LayoutParams(dp(context, 56), dp(context, 48)))
            val rowParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 52)
            ).apply { topMargin = dp(context, 6) }
            list.addView(row, rowParams)
        }
    }

    private fun confirmDeleteCategory(
        context: Context,
        list: LinearLayout,
        row: View,
        type: TransactionType,
        category: String
    ) {
        ForestDialogBuilder(context)
            .setTitle("删除分类？")
            .setMessage("“$category”会从以后记账的分类列表中移除，历史账单不会删除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认删除") { _, _ ->
                if (viewModel.deleteCategory(type, category)) {
                    list.removeView(row)
                    Toast.makeText(context, "分类已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    fun showLedgerManager() {
        val context = contextProvider()
        val dialog = BottomSheetDialog(context)
        val root = managementRoot(
            context,
            "FOREST LEDGERS",
            "管理账本",
            "点击账本名称可切换。删除账本会同时删除它的交易、资产和借还记录。"
        )
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val snap = viewModel.currentSnapshot
        for (ledger in snap.ledgers) {
            addLedgerRow(context, list, dialog, ledger, snap.currentLedger.id)
        }
        root.addView(
            list,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val create = managementAction(context, "＋  新建账本", R.color.forest_green).apply {
            setBackgroundResource(R.drawable.bg_details_picker_selected)
            setOnClickListener {
                dialog.dismiss()
                showCreateLedgerDialog(context)
            }
        }
        val createParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 48)
        ).apply { topMargin = dp(context, 12) }
        root.addView(create, createParams)
        val done = managementAction(context, "完成", R.color.wood_brown).apply {
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(
            done,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48))
        )
        showPaperSheet(dialog, root)
    }

    private fun addLedgerRow(
        context: Context,
        list: LinearLayout,
        dialog: BottomSheetDialog,
        ledger: Ledger,
        currentLedgerId: String
    ) {
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 12), 0, dp(context, 8), 0)
            setBackgroundResource(R.drawable.bg_details_picker_option)
        }
        val suffix = if (ledger.id == currentLedgerId) "  · 当前" else ""
        val name = managementLabel(context, ledger.name + suffix, 14, R.color.ink).apply {
            contentDescription = "切换到${ledger.name}"
            setOnClickListener {
                viewModel.switchLedger(ledger.id)
                dialog.dismiss()
            }
        }
        row.addView(name, LinearLayout.LayoutParams(0, dp(context, 52), 1f))
        val delete = managementAction(context, "删除", R.color.expense_red).apply {
            contentDescription = "删除账本${ledger.name}"
            setOnClickListener { confirmDeleteLedger(context, dialog, ledger) }
        }
        row.addView(delete, LinearLayout.LayoutParams(dp(context, 56), dp(context, 48)))
        val rowParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 52)
        ).apply { topMargin = dp(context, 6) }
        list.addView(row, rowParams)
    }

    private fun confirmDeleteLedger(
        context: Context,
        managerDialog: BottomSheetDialog,
        ledger: Ledger
    ) {
        val txCount = viewModel.currentSnapshot.transactionCountByLedger[ledger.id] ?: 0
        ForestDialogBuilder(context)
            .setTitle("删除账本？")
            .setMessage("将删除“${ledger.name}”及其中 $txCount 笔交易、资产和借还记录，此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认删除") { _, _ ->
                try {
                    viewModel.deleteLedger(ledger.id)
                    managerDialog.dismiss()
                    Toast.makeText(context, "账本已删除", Toast.LENGTH_SHORT).show()
                } catch (e: IllegalStateException) {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showCreateLedgerDialog(context: Context) {
        val input = EditText(context).apply {
            isSingleLine = true
            hint = "例如：北京旅行账本"
        }
        ForestDialogBuilder(context)
            .setTitle("新建账本")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建并切换") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "请输入账本名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.createAndSwitchLedger(name)
                Toast.makeText(context, "账本已创建", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun managementRoot(
        context: Context,
        eyebrowText: String,
        titleText: String,
        hintText: String
    ): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 16))
            setBackgroundResource(R.drawable.bg_details_picker_sheet)
        }
        val eyebrow = managementLabel(context, eyebrowText, 9, R.color.forest_green).apply {
            letterSpacing = 0.12f
        }
        root.addView(
            eyebrow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 20))
        )
        val title = managementLabel(context, titleText, 21, R.color.wood_brown).apply {
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 34))
        )
        val hint = managementLabel(context, hintText, 12, R.color.wood_brown)
        root.addView(
            hint,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return root
    }

    private fun managementLabel(context: Context, value: String, size: Int, color: Int): TextView =
        TextView(context).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(ContextCompat.getColor(context, color))
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun managementAction(context: Context, value: String, color: Int): TextView =
        managementLabel(context, value, 13, color).apply {
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            isClickable = true
            isFocusable = true
        }

    private fun showPaperSheet(dialog: BottomSheetDialog, root: View) {
        dismissActiveSheet()
        activeSheet = dialog
        dialog.setOnDismissListener {
            if (activeSheet === dialog) activeSheet = null
        }
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    private fun dp(context: Context, value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)
}

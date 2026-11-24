package com.example.pkmapp.study

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.pkmapp.MainActivity
import com.example.pkmapp.R
import com.example.pkmapp.forest.CrayonCard
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.LIGHT
import com.example.pkmapp.forest.ForestUi.LINE
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.heading
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.navigation.AppDestination
import com.example.pkmapp.todo.TodoStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

abstract class BaseStudyFragment : Fragment() {

    internal lateinit var store: StudyStore
    protected val sharedViewModel: StudySharedViewModel by activityViewModels()

    internal var scroll: ScrollView? = null
    private var topBarTitle: TextView? = null
    internal var body: LinearLayout? = null
    internal var activeSheet: StudySheet? = null

    private val exportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
        this::exportStudy
    )

    private val importPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
        this::importStudy
    )

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (this::store.isInitialized && isAdded) {
            StudyNotifications.refresh(ctx(), store)
            render()
            if (!granted) toast("可以在系统设置里开启学习通知")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        saved: Bundle?
    ): View {
        val context = requireContext()
        store = StudyStore.get(context)
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, ForestUi.dp(context, 18f))
            setBackgroundColor(Color.TRANSPARENT)
        }
        scroll = scrollView
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { _, insets ->
            applyScrollInsets(insets)
            insets
        }
        val initialInsets = ViewCompat.getRootWindowInsets(requireActivity().window.decorView)
        if (initialInsets != null) {
            applyScrollInsets(initialInsets)
        }

        val host = column(context).apply { clipChildren = false }
        scrollView.addView(host)

        val bar = row(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ForestUi.dp(context, 68f)
            setPadding(
                ForestUi.dp(context, 18f),
                ForestUi.dp(context, 10f),
                ForestUi.dp(context, 20f),
                0
            )
        }
        val back = ImageButton(context).apply {
            id = R.id.page_back_button
            setImageResource(R.drawable.return_home_door)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(0, 0, 0, 0)
            contentDescription = "返回森林入口"
            setOnClickListener { open(AppDestination.HOME) }
            ForestMotion.bind(this)
            background = ripple(context, rounded(context, 0, 0, 14), false)
        }
        bar.addView(
            back,
            LinearLayout.LayoutParams(ForestUi.dp(context, 56f), ForestUi.dp(context, 56f))
        )
        val titleView = heading(context, "", 24).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(DEEP)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            isSingleLine = true
        }
        topBarTitle = titleView
        val titleLp = LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = ForestUi.dp(context, 12f)
        }
        bar.addView(titleView, titleLp)
        host.addView(bar)

        val pageBody = column(context).apply { clipChildren = false }
        body = pageBody
        host.addView(pageBody)
        render()
        return scrollView
    }

    override fun onResume() {
        super.onResume()
        StudyNotifications.refresh(ctx(), store)
        render()
    }

    override fun onDestroyView() {
        activeSheet?.let { sheet ->
            sheet.setDismissWithAnimation(false)
            sheet.dismiss()
        }
        activeSheet = null
        onBeforeDestroyViews()
        topBarTitle = null
        body = null
        scroll = null
        super.onDestroyView()
    }

    protected open fun onBeforeRender() {}
    protected abstract fun buildPage(body: LinearLayout)
    protected open fun onAfterRender() {}
    protected open fun onBeforeDestroyViews() {}

    internal open fun render() {
        val currentBody = body ?: return
        val scrollView = scroll ?: return
        val y = scrollView.scrollY
        onBeforeRender()
        currentBody.removeAllViews()
        buildPage(currentBody)
        onAfterRender()
        if (isAdded) {
            sharedViewModel.syncFromStores(ctx())
        }
        scrollView.post {
            scroll?.scrollTo(0, y)
        }
    }

    private fun applyScrollInsets(insets: WindowInsetsCompat) {
        val scrollView = scroll ?: return
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        scrollView.setPadding(0, bars.top, 0, bars.bottom + dp(112))
    }

    internal fun ctx(): Context = requireContext()

    internal fun dp(value: Int): Int = ForestUi.dp(ctx(), value.toFloat())

    internal fun dp(value: Float): Int = ForestUi.dp(ctx(), value)

    internal fun setBarTitle(title: String) {
        topBarTitle?.text = title
    }

    internal fun open(destination: AppDestination) {
        (requireActivity() as MainActivity).showDestination(destination)
    }

    internal fun openHistory(planId: String) {
        sharedViewModel.requestPlanHistory(planId)
        open(AppDestination.STUDY_STATS)
    }

    internal fun newSheet(): StudySheet {
        activeSheet?.dismiss()
        val sheet = StudySheet(ctx())
        sheet.setOnDismissListener {
            if (activeSheet === sheet) activeSheet = null
        }
        activeSheet = sheet
        return sheet
    }

    internal fun sheetPane(title: String, detail: String?, glyph: Int): StudySheet.Pane =
        StudySheet.Pane(ctx(), title, detail, glyph)

    internal fun planDialogs(): StudyPlanDialogs =
        StudyPlanDialogs(
            ctx(),
            store,
            newSheet(),
            { render() },
            { planId -> openHistory(planId) },
            { sheet -> backupMenu(sheet) }
        )

    internal fun workDialogs(sheet: StudySheet): StudyWorkDialogs =
        StudyWorkDialogs(ctx(), parentFragmentManager, store, sheet)

    internal fun backupMenu(sheet: StudySheet) {
        val pane = sheetPane(
            "学习备份",
            "备份包含计划、科目、记录、计时与小林地进度。",
            R.drawable.ic_leaf
        )
        ForestUi.add(
            pane.body,
            StudySheet.item(
                ctx(),
                "导出学习备份",
                "保存为一个 JSON 文件",
                R.drawable.ic_study_book,
                StudySheet.MOSS,
                "›",
                false
            ) {
                sheet.dismiss()
                exportPicker.launch(
                    "芽叶记-学习备份-${StudyNotebook.dayKey(System.currentTimeMillis())}.json"
                )
            },
            0
        )
        ForestUi.add(
            pane.body,
            StudySheet.item(
                ctx(),
                "恢复学习备份",
                "选取文件，合并两份学习记录",
                R.drawable.ic_study_trail,
                StudySheet.OAT,
                "›",
                false
            ) {
                sheet.dismiss()
                importPicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            },
            8
        )
        sheet.push(pane)
    }

    private fun exportStudy(uri: Uri?) {
        if (uri == null || !isAdded) return
        try {
            ctx().contentResolver.openOutputStream(uri, "wt").use { out ->
                if (out == null) throw IOException()
                out.write(store.exportBackup().toByteArray(StandardCharsets.UTF_8))
            }
        } catch (_: IOException) {
            toast("学习备份导出失败，请检查文件位置和存储空间")
            return
        } catch (_: IllegalArgumentException) {
            toast("学习备份导出失败，请检查文件位置和存储空间")
            return
        } catch (_: IllegalStateException) {
            toast("学习备份导出失败，请检查文件位置和存储空间")
            return
        } catch (_: SecurityException) {
            toast("学习备份导出失败，请检查文件位置和存储空间")
            return
        }
        toast("已导出学习备份")
    }

    private fun importStudy(uri: Uri?) {
        if (uri == null || !isAdded) return
        val backup: StudyNotebook = try {
            ctx().contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw IOException()
                val bytes = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val length = input.read(chunk)
                    if (length == -1) break
                    if (bytes.size() + length > 32 * 1024 * 1024) {
                        throw IllegalArgumentException("学习备份超过 32 MB，无法读取")
                    }
                    bytes.write(chunk, 0, length)
                }
                StudyNotebook.fromJson(String(bytes.toByteArray(), StandardCharsets.UTF_8))
            }
        } catch (e: IllegalArgumentException) {
            toast(e.message)
            return
        } catch (_: IOException) {
            toast("无法读取这个学习备份")
            return
        } catch (_: SecurityException) {
            toast("无法读取这个学习备份")
            return
        }
        val sheet = newSheet()
        val pane = sheetPane(
            "恢复学习备份",
            "恢复将合并学习记录，请选择保留哪份计划设置和林地。",
            R.drawable.ic_study_trail
        )
        StudySheet.addHairline(pane.body, 0, 14)
        val stats = row(ctx())
        stat(stats, "备份中的计划", "${backup.plans().size} 个", 1f)
        stat(stats, "备份中的记录", "${backup.records().size} 条", 1f)
        pane.body.addView(stats)
        StudySheet.addHairline(pane.body, 14, 0)
        pane.primary("保留本机设置和林地") { restoreLearning(sheet, backup, false) }
        pane.secondary("使用备份设置和林地") { restoreLearning(sheet, backup, true) }
        sheet.push(pane)
    }

    private fun restoreLearning(dialog: StudySheet, backup: StudyNotebook, preferBackup: Boolean) {
        perform {
            val added = store.importBackup(backup, preferBackup)
            dialog.dismiss()
            render()
            toast("学习备份已恢复，新增 $added 条记录")
        }
    }

    internal fun allowStudyNotifications() {
        if (StudyNotifications.permitted(ctx())) return
        val flags = ctx().getSharedPreferences("study_ui", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && (
                !flags.getBoolean("askedNotifications", false) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                )
        ) {
            flags.edit().putBoolean("askedNotifications", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx().packageName)
        )
    }

    internal fun toast(value: String?) {
        if (value.isNullOrEmpty()) return
        Toast.makeText(ctx(), value, Toast.LENGTH_SHORT).show()
    }

    internal inline fun perform(action: () -> Unit) {
        try {
            action()
        } catch (error: IllegalArgumentException) {
            toast(error.message)
        } catch (error: IllegalStateException) {
            toast(error.message)
        }
    }

    internal fun content(): LinearLayout = column(ctx()).apply {
        setPadding(dp(19), 0, dp(19), 0)
    }

    internal fun card(): LinearLayout = column(ctx()).apply {
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = CrayonCard()
    }

    internal fun statsPanel(color: Int, border: Int): LinearLayout = column(ctx()).apply {
        val resolvedFill = if (color == LIGHT) 0xfffffefa.toInt() else color
        val resolvedStroke = if (border == LINE) 0x1c5c4938 else border
        background = rounded(ctx(), resolvedFill, resolvedStroke, 20)
        elevation = dp(1).toFloat()
        setPadding(dp(16), dp(15), dp(16), dp(14))
    }

    internal fun section(parent: LinearLayout, title: String, detail: String?) {
        val headerRow = row(ctx())
        headerRow.addView(heading(ctx(), title, 17), LinearLayout.LayoutParams(0, -2, 1f))
        if (detail != null) {
            headerRow.addView(text(ctx(), detail, 10, MUTED))
        }
        ForestUi.add(parent, headerRow, 20)
    }

    internal fun note(value: String): TextView = text(ctx(), value, 12, MUTED).apply {
        setLineSpacing(dp(4).toFloat(), 1f)
    }

    internal fun stat(summary: LinearLayout, title: String, value: String, weight: Float) {
        val col = column(ctx())
        col.addView(text(ctx(), title, 10, MUTED))
        val number = heading(ctx(), value, 15).apply { maxLines = 2 }
        ForestUi.add(col, number, 5)
        summary.addView(col, LinearLayout.LayoutParams(0, -2, weight))
    }

    internal fun addEqual(row: LinearLayout, child: View, height: Int) {
        val lp = LinearLayout.LayoutParams(0, dp(height), 1f)
        lp.setMargins(dp(3), 0, dp(3), 0)
        row.addView(child, lp)
    }

    internal fun tintedIcon(resId: Int, sizeDp: Int, tintColor: Int): Drawable? {
        val raw = ContextCompat.getDrawable(ctx(), resId) ?: return null
        val icon = DrawableCompat.wrap(raw.mutate())
        DrawableCompat.setTint(icon, tintColor)
        val px = dp(sizeDp)
        icon.setBounds(0, 0, px, px)
        return icon
    }

    internal fun bottomHairlineBg(color: Int): Drawable {
        val line = GradientDrawable().apply { setColor(color) }
        return LayerDrawable(arrayOf<Drawable>(line)).apply {
            setLayerGravity(0, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
            setLayerHeight(0, max(1, dp(0.75f)))
        }
    }

    internal fun editorialChartTabs(
        labels: Array<String>,
        ids: IntArray,
        selected: Int,
        change: (Int) -> Unit
    ): LinearLayout {
        val strip = row(ctx()).apply {
            gravity = Gravity.BOTTOM
            val hairline = GradientDrawable().apply { setColor(0x1f5c4938) }
            background = LayerDrawable(arrayOf<Drawable>(hairline)).apply {
                setLayerGravity(0, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
                setLayerHeight(0, max(1, dp(1)))
            }
        }
        val density = ctx().resources.displayMetrics.density
        val indicatorHeight = max(3, (2.5f * density).roundToInt())
        val tabGap = dp(22)
        for (i in labels.indices) {
            val isSelected = i == selected
            val tab = column(ctx()).apply {
                id = ids[i]
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                minimumHeight = dp(42)
                isClickable = true
                isFocusable = true
                this.isSelected = isSelected
                contentDescription = labels[i]
                background = ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 8), false)
            }
            val label = text(
                ctx(),
                labels[i],
                13,
                if (isSelected) 0xff283f2c.toInt() else 0xff736c5d.toInt()
            ).apply {
                typeface = Typeface.create(
                    if (isSelected) "sans-serif-medium" else "sans-serif",
                    if (isSelected) Typeface.BOLD else Typeface.NORMAL
                )
                setPadding(dp(2), dp(8), dp(2), dp(9))
            }
            tab.addView(label, LinearLayout.LayoutParams(-2, -2))
            val indicator = View(ctx()).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = rounded(
                    ctx(),
                    if (isSelected) 0xff315e3a.toInt() else Color.TRANSPARENT,
                    0,
                    99
                )
            }
            tab.addView(indicator, LinearLayout.LayoutParams(-1, indicatorHeight))
            tab.setOnClickListener { change(i) }
            ForestMotion.bind(tab)
            val tabLp = LinearLayout.LayoutParams(-2, -2).apply {
                if (i > 0) marginStart = tabGap
            }
            strip.addView(tab, tabLp)
        }
        return strip
    }

    companion object {
        internal fun partnerDrawable(partner: StudyPlan.Partner?): Int =
            StudyUi.partnerDrawable(partner)

        internal fun shortMonthDay(dayKey: String?): String {
            if (dayKey == null || dayKey.length != 10) return dayKey.orEmpty()
            val c = TodoStore.parseDay(dayKey)
            return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
        }

        internal fun formatTodoDayHeader(
            dayKey: String?,
            todayKey: String,
            tomorrowKey: String,
            yesterdayKey: String,
            doneGroup: Boolean
        ): String {
            if (dayKey.isNullOrEmpty()) return "随时可做 · 未限日期"
            val c = TodoStore.parseDay(dayKey)
            val md = "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
            if (dayKey == todayKey) return "今天 · $md"
            if (dayKey == tomorrowKey) return "明天 · $md"
            if (doneGroup && dayKey == yesterdayKey) return "昨天 · $md"
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (c.get(Calendar.YEAR) != currentYear) {
                return "${c.get(Calendar.YEAR)}年$md"
            }
            val weeks = arrayOf("", "周日", "周一", "周二", "周三", "周四", "周五", "周六")
            return "$md · ${weeks[c.get(Calendar.DAY_OF_WEEK)]}"
        }
    }
}

internal object StudyUi {
    @JvmStatic
    fun partnerDrawable(partner: StudyPlan.Partner?): Int = when (partner) {
        StudyPlan.Partner.BEAR -> R.drawable.crayon_partner_bear
        StudyPlan.Partner.BUNNY -> R.drawable.crayon_partner_bunny
        else -> R.drawable.crayon_study_companion
    }
}

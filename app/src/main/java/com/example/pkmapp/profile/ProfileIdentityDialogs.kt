package com.example.pkmapp.profile

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.pkmapp.R
import com.example.pkmapp.forest.ForestDialogBuilder
import com.example.pkmapp.life.LifeSharedViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal class ProfileIdentityDialogs(
    private val contextProvider: () -> Context,
    private val viewModel: LifeSharedViewModel,
    private val onAvatarReset: () -> Unit
) {
    private var activeSheet: BottomSheetDialog? = null

    fun dismissActiveSheet() {
        activeSheet?.dismiss()
        activeSheet = null
    }

    fun showNicknameEditor() {
        val context = contextProvider()
        val dialog = BottomSheetDialog(context)
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_profile_nickname, null)
        val input = root.findViewById<EditText>(R.id.profile_nickname_input)
        input.setText(viewModel.currentSnapshot.nickname)
        input.setSelection(input.length())
        root.findViewById<View>(R.id.profile_nickname_dialog_close).setOnClickListener {
            dialog.dismiss()
        }
        root.findViewById<View>(R.id.profile_nickname_dialog_cancel).setOnClickListener {
            dialog.dismiss()
        }
        root.findViewById<View>(R.id.profile_nickname_dialog_save).setOnClickListener {
            val nickname = input.text.toString().trim()
            if (nickname.isEmpty()) {
                input.error = "昵称不能为空"
                return@setOnClickListener
            }
            viewModel.updateNickname(nickname)
            dialog.dismiss()
            Toast.makeText(context, "森林昵称已更新", Toast.LENGTH_SHORT).show()
        }
        showPaperSheet(dialog, root)
    }

    fun showCheckInDialog() {
        val context = contextProvider()
        val dialog = BottomSheetDialog(context)
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_checkin, null)
        val close = root.findViewById<TextView>(R.id.checkin_close)
        val previous = root.findViewById<TextView>(R.id.checkin_previous_month)
        val next = root.findViewById<TextView>(R.id.checkin_next_month)
        val monthLabel = root.findViewById<TextView>(R.id.checkin_month_label)
        val levelLabel = root.findViewById<TextView>(R.id.checkin_level_text)
        val progressLabel = root.findViewById<TextView>(R.id.checkin_progress_label)
        val progressBar = root.findViewById<ProgressBar>(R.id.checkin_progress)
        val nextLevelLabel = root.findViewById<TextView>(R.id.checkin_next)
        val checkIn = root.findViewById<TextView>(R.id.checkin_action)
        val calendar = root.findViewById<GridLayout>(R.id.checkin_calendar_grid)
        val weekdays = root.findViewById<LinearLayout>(R.id.checkin_weekdays)

        for (label in arrayOf("一", "二", "三", "四", "五", "六", "日")) {
            val weekday = TextView(context).apply {
                text = label
                textSize = 9f
                setTextColor(ContextCompat.getColor(context, R.color.wood_brown))
                gravity = Gravity.CENTER
            }
            weekdays.addView(weekday, LinearLayout.LayoutParams(0, dp(context, 24), 1f))
        }

        close.setOnClickListener { dialog.dismiss() }
        val visibleMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val redraw = {
            renderCheckInProgress(context, levelLabel, progressLabel, progressBar, nextLevelLabel, checkIn)
            renderCalendar(context, calendar, monthLabel, visibleMonth)
        }
        previous.setOnClickListener {
            visibleMonth.add(Calendar.MONTH, -1)
            redraw()
        }
        next.setOnClickListener {
            visibleMonth.add(Calendar.MONTH, 1)
            redraw()
        }
        checkIn.setOnClickListener {
            val today = formatDate(Calendar.getInstance())
            if (viewModel.checkInToday(today)) {
                Toast.makeText(context, "今天的森林足迹已留下", Toast.LENGTH_SHORT).show()
                redraw()
            } else {
                Toast.makeText(context, "今天已经打卡，明天再来吧", Toast.LENGTH_SHORT).show()
            }
        }
        redraw()
        showPaperSheet(dialog, root)
    }

    fun confirmClearData() {
        val context = contextProvider()
        ForestDialogBuilder(context)
            .setTitle("清空全部数据？")
            .setMessage("账本、交易、攒钱目标、资产、负债、借还记录和小屋资料都会恢复到初始状态，此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认清空") { _, _ ->
                viewModel.clearAllUserData()
                onAvatarReset()
                Toast.makeText(context, "已恢复初始状态", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun renderCheckInProgress(
        context: Context,
        levelLabel: TextView,
        progressLabel: TextView,
        progressBar: ProgressBar,
        nextLevelLabel: TextView,
        checkIn: TextView
    ) {
        val checkedDates = viewModel.currentSnapshot.checkedDates
        val days = checkedDates.size
        val level = CheckInProgress.levelForDays(days)
        val nextThreshold = CheckInProgress.nextThresholdForLevel(level)
        val capped = level >= CheckInProgress.maxLevel()
        val currentThreshold = CheckInProgress.currentThresholdForLevel(level)
        val progress = if (capped) {
            100
        } else {
            Math.round((days - currentThreshold) * 100f / maxOf(1, nextThreshold - currentThreshold))
        }
        levelLabel.text = "Lv.$level · ${CheckInProgress.nameForLevel(level)}"
        progressLabel.text = if (capped) "$days 天 · 已达上限" else "$days / $nextThreshold 天"
        progressBar.progress = progress.coerceIn(0, 100)
        nextLevelLabel.text = if (capped) {
            "已达到最高等级，森林会一直记得你的脚印。"
        } else {
            "再坚持 ${nextThreshold - days} 天，升级为 Lv.${level + 1} · ${CheckInProgress.nameForLevel(level + 1)}"
        }
        val checkedToday = checkedDates.contains(formatDate(Calendar.getInstance()))
        checkIn.text = if (checkedToday) "今日已签到" else "今日签到"
        checkIn.setTextColor(
            ContextCompat.getColor(
                context,
                if (checkedToday) R.color.wood_brown else R.color.paper_light
            )
        )
        checkIn.isEnabled = !checkedToday
    }

    private fun renderCalendar(
        context: Context,
        calendar: GridLayout,
        monthLabel: TextView,
        visibleMonth: Calendar
    ) {
        calendar.removeAllViews()
        monthLabel.text = String.format(
            Locale.CHINA,
            "%d 年 %02d 月",
            visibleMonth.get(Calendar.YEAR),
            visibleMonth.get(Calendar.MONTH) + 1
        )
        val dates = viewModel.currentSnapshot.checkedDates
        val cursor = visibleMonth.clone() as Calendar
        var firstDay = cursor.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        if (firstDay < 0) firstDay += 7
        val daysInMonth = cursor.getActualMaximum(Calendar.DAY_OF_MONTH)
        var previousMonthDays = 0
        if (firstDay > 0) {
            val previousMonth = visibleMonth.clone() as Calendar
            previousMonth.add(Calendar.MONTH, -1)
            previousMonthDays = previousMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        val today = Calendar.getInstance()
        for (index in 0 until 42) {
            val day = index - firstDay + 1
            val cellDate = visibleMonth.clone() as Calendar
            var outside = false
            when {
                day < 1 -> {
                    cellDate.add(Calendar.MONTH, -1)
                    cellDate.set(Calendar.DAY_OF_MONTH, previousMonthDays + day)
                    outside = true
                }
                day > daysInMonth -> {
                    cellDate.add(Calendar.MONTH, 1)
                    cellDate.set(Calendar.DAY_OF_MONTH, day - daysInMonth)
                    outside = true
                }
                else -> cellDate.set(Calendar.DAY_OF_MONTH, day)
            }
            val shownDay = cellDate.get(Calendar.DAY_OF_MONTH)
            val key = formatDate(cellDate)
            val checked = !outside && dates.contains(key)
            val isToday = !outside &&
                today.get(Calendar.YEAR) == cellDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == cellDate.get(Calendar.DAY_OF_YEAR)

            val cell = TextView(context).apply {
                text = shownDay.toString()
                textSize = 10f
                gravity = Gravity.CENTER
                contentDescription = "${shownDay}日${if (checked) "，已签到" else "，未签到"}"
                val textColorRes = when {
                    outside -> R.color.wood_brown
                    checked -> R.color.paper_light
                    else -> R.color.ink
                }
                setTextColor(ContextCompat.getColor(context, textColorRes))
                alpha = if (outside) 0.5f else 1f
                background = calendarCell(context, checked, isToday)
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(context, 48)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2))
            }
            calendar.addView(cell, params)
        }
    }

    private fun calendarCell(context: Context, checked: Boolean, today: Boolean): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(context, 14).toFloat()
            setColor(
                ContextCompat.getColor(
                    context,
                    if (checked) R.color.forest_green else android.R.color.transparent
                )
            )
            if (today) {
                setStroke(dp(context, 2), ContextCompat.getColor(context, R.color.warm_orange))
            }
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

    companion object {
        internal fun formatDate(calendar: Calendar): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }
}

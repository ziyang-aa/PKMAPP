package com.example.pkmapp.study

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.example.pkmapp.R
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.LIGHT
import com.example.pkmapp.forest.ForestUi.LINE
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.WOOD
import com.example.pkmapp.forest.ForestUi.button
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.google.android.material.button.MaterialButton
import kotlin.math.max
import kotlin.math.roundToInt

internal object StudyFocusTimerCard {

    internal class GoalTabSlot(
        val minutes: Int?, // null represents the custom duration slot
        val label: TextView,
        val indicator: View
    )

    internal class Holder(
        val root: LinearLayout,
        val subjectSelector: MaterialButton,
        val focusStatusPill: LinearLayout,
        val focusStatusDot: View,
        val focusStatusLabel: TextView,
        val statusPulseAnimator: ObjectAnimator?,
        val clock: TextView,
        val dial: StudyClockFace,
        val dialGoalCaption: TextView,
        val dialRightCaption: TextView,
        val activeStatusText: TextView?,
        val startPauseButton: MaterialButton,
        val focusGentleNote: TextView,
        private val goalSlots: List<GoalTabSlot>
    ) {
        fun syncSessionGoal(goalMinutes: Int) {
            if (goalSlots.isEmpty()) return
            val isCustom = goalMinutes != 25 && goalMinutes != 45 && goalMinutes != 60
            for (slot in goalSlots) {
                val selected = if (slot.minutes != null) {
                    slot.minutes == goalMinutes
                } else {
                    isCustom
                }
                if (slot.minutes == null) {
                    slot.label.text = if (isCustom) "${goalMinutes}分钟 ✎" else "自定义"
                }
                styleGoalSlot(slot, selected)
            }
        }
    }

    fun build(
        fragment: StudyFocusFragment,
        n: StudyNotebook,
        active: Boolean,
        initialCountdownMode: Boolean,
        initialKeepScreenOn: Boolean,
        onToggleCountdown: () -> Boolean,
        onToggleKeepScreenOn: () -> Boolean,
        onSelectGoalMinutes: (Int) -> Unit,
        onStartOrPause: () -> Unit
    ): Holder {
        val ctx = fragment.ctx()
        val timer = fragment.card().apply {
            background = buildFocusCardBackground(ctx, fragment)
            setPadding(fragment.dp(20), fragment.dp(16), fragment.dp(20), fragment.dp(20))
            elevation = fragment.dp(2).toFloat()
            if (Build.VERSION.SDK_INT >= 28) {
                outlineAmbientShadowColor = 0x245C4938
                outlineSpotShadowColor = 0x2E5C4938
            }
        }

        val currentSubject = if (active) n.activeSubject() else n.selected()
        val subjectLabel = currentSubject.title + if (active) " · 本次科目" else "  切换 ›"
        val selectorText = SpannableString(subjectLabel).apply {
            setSpan(RelativeSizeSpan(0.8f), currentSubject.title.length, subjectLabel.length, 0)
            setSpan(ForegroundColorSpan(MUTED), currentSubject.title.length, subjectLabel.length, 0)
        }
        val timerTopRow = row(ctx)
        val selector = button(ctx, subjectLabel, false).apply {
            text = selectorText
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            iconPadding = fragment.dp(10)
            id = R.id.study_subject_selector
            contentDescription = "切换学习科目"
            setIconResource(R.drawable.ic_study_book)
            iconTint = ColorStateList.valueOf(DEEP)
            iconSize = fragment.dp(19)
            backgroundTintList = ColorStateList.valueOf(0)
            strokeWidth = 0
            isEnabled = !active
            alpha = if (active) 0.88f else 1f
            setOnClickListener { StudyFocusDialogs.showSubjectSheet(fragment) }
        }
        timerTopRow.addView(selector, LinearLayout.LayoutParams(0, fragment.dp(42), 1f))

        lateinit var gentleNoteRef: TextView
        lateinit var modeToggle: TextView
        modeToggle = StudySheet.link(
            ctx,
            if (initialCountdownMode) "倒计时 ⇄" else "正计时 ⇄",
            if (initialCountdownMode) 0xff2c5132.toInt() else WOOD
        ) {
            val isCountdown = onToggleCountdown()
            modeToggle.text = if (isCountdown) "倒计时 ⇄" else "正计时 ⇄"
            modeToggle.setTextColor(if (isCountdown) 0xff2c5132.toInt() else WOOD)
            modeToggle.background = ripple(
                ctx,
                rounded(
                    ctx,
                    if (isCountdown) 0xffeef4ea.toInt() else 0xfffffefa.toInt(),
                    if (isCountdown) 0xffa7c2a0.toInt() else 0x265c4938,
                    999
                ),
                false
            )
            if (!fragment.store.notebook().currentHasSession()) {
                gentleNoteRef.text = if (isCountdown) {
                    "倒计时模式 · 归零后自动转为加时计数"
                } else {
                    "正向计时 · 随时暂停 · 到达目标仍可继续"
                }
            }
        }.apply {
            textSize = 11f
            minimumHeight = fragment.dp(30)
            setPadding(fragment.dp(11), fragment.dp(4), fragment.dp(11), fragment.dp(4))
            background = ripple(
                ctx,
                rounded(
                    ctx,
                    if (initialCountdownMode) 0xffeef4ea.toInt() else 0xfffffefa.toInt(),
                    if (initialCountdownMode) 0xffa7c2a0.toInt() else 0x265c4938,
                    999
                ),
                false
            )
        }
        timerTopRow.addView(modeToggle)
        timer.addView(timerTopRow, LinearLayout.LayoutParams(-1, -2))

        val activeTask = if (active) n.task(n.activeTaskId()) else null
        if (activeTask != null) ForestUi.add(timer, fragment.note("本次目标 · ${activeTask.title}"), 0)

        val clockBlock = column(ctx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(fragment.dp(4), fragment.dp(18), fragment.dp(4), fragment.dp(10))
        }
        val isRunning = active && n.running()
        val isPaused = active && !n.running()
        val pill = row(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(fragment.dp(8), fragment.dp(3), fragment.dp(8), fragment.dp(3))
            background = null
        }
        val dot = View(ctx).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = rounded(
                ctx,
                when {
                    isRunning -> 0xff456444.toInt()
                    isPaused -> 0xffb8894f.toInt()
                    else -> 0xffa79e8d.toInt()
                },
                0,
                99
            )
        }
        pill.addView(
            dot,
            LinearLayout.LayoutParams(fragment.dp(6), fragment.dp(6)).apply { marginEnd = fragment.dp(7) }
        )
        var pulseAnimator: ObjectAnimator? = null
        if (isRunning && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())) {
            pulseAnimator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.35f).apply {
                duration = 1100
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
        val statusLbl = text(
            ctx,
            when {
                isRunning -> "正在专注"
                isPaused -> "休息一下"
                else -> "准备好就开始"
            },
            12,
            when {
                isRunning -> 0xff315e3a.toInt()
                isPaused -> 0xff7b5c3b.toInt()
                else -> 0xff847a69.toInt()
            }
        ).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.08f
        }
        pill.addView(statusLbl)
        clockBlock.addView(pill, LinearLayout.LayoutParams(-2, -2))

        val clockView = text(
            ctx,
            StudyFormat.clock(if (active) fragment.store.elapsedMillis() else 0),
            50,
            0xff1f3424.toInt()
        ).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            letterSpacing = 0.04f
            maxLines = 1
            id = R.id.study_timer
            gravity = Gravity.CENTER
            contentDescription = "本次学习计时"
        }
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            clockView, 30, 52, 2, TypedValue.COMPLEX_UNIT_SP
        )
        clockBlock.addView(
            clockView,
            LinearLayout.LayoutParams(-1, fragment.dp(66)).apply { topMargin = fragment.dp(10) }
        )

        val dialDrawable = StudyClockFace()
        val progressRail = View(ctx).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = dialDrawable
        }
        clockBlock.addView(
            progressRail,
            LinearLayout.LayoutParams(-1, fragment.dp(5)).apply {
                setMargins(fragment.dp(8), fragment.dp(16), fragment.dp(8), 0)
            }
        )

        val focusGoal = if (active) n.activeGoal() else n.sessionGoal()
        val progressMeta = row(ctx)
        val goalCap = text(
            ctx,
            (if (initialCountdownMode) "倒计时目标 " else "目标 ") + "$focusGoal 分钟",
            12,
            MUTED
        ).apply { fontFeatureSettings = "tnum" }
        progressMeta.addView(goalCap, LinearLayout.LayoutParams(0, -2, 1f))
        val rightCap = text(ctx, "0%", 12, 0xff315e3a.toInt()).apply {
            fontFeatureSettings = "tnum"
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        progressMeta.addView(rightCap)
        clockBlock.addView(
            progressMeta,
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(fragment.dp(8), fragment.dp(8), fragment.dp(8), 0) }
        )
        timer.addView(clockBlock, LinearLayout.LayoutParams(-1, -2))

        val goalSlots = ArrayList<GoalTabSlot>(4)
        var statusTxt: TextView? = null
        if (!active) {
            val density = ctx.resources.displayMetrics.density
            val indicatorH = max(3, (2.2f * density).roundToInt())
            val hairline = GradientDrawable().apply { setColor(0x1f5c4938) }
            val goals = row(ctx).apply {
                gravity = Gravity.BOTTOM
                background = LayerDrawable(arrayOf<Drawable>(hairline)).apply {
                    setLayerGravity(0, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
                    setLayerHeight(0, max(1, fragment.dp(0.75f)))
                }
            }
            val presetGoals = intArrayOf(25, 45, 60)
            val isCustomGoal = focusGoal != 25 && focusGoal != 45 && focusGoal != 60

            fun buildSlot(labelText: String, minutes: Int?, selected: Boolean, onClick: () -> Unit): LinearLayout {
                val tab = column(ctx).apply {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                    minimumHeight = fragment.dp(40)
                    isClickable = true
                    isFocusable = true
                    background = ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 8), false)
                    setOnClickListener { onClick() }
                }
                val lbl = text(ctx, labelText, 13, 0xff827b6d.toInt()).apply {
                    gravity = Gravity.CENTER
                    fontFeatureSettings = "tnum"
                    maxLines = 1
                    setPadding(fragment.dp(2), fragment.dp(8), fragment.dp(2), fragment.dp(7))
                }
                val bar = View(ctx).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                tab.addView(lbl, LinearLayout.LayoutParams(-2, -2))
                tab.addView(bar, LinearLayout.LayoutParams(fragment.dp(22), indicatorH))
                val slot = GoalTabSlot(minutes, lbl, bar)
                styleGoalSlot(slot, selected)
                goalSlots.add(slot)
                return tab
            }

            for (minutes in presetGoals) {
                val tab = buildSlot("${minutes}分钟", minutes, focusGoal == minutes) {
                    onSelectGoalMinutes(minutes)
                }
                goals.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
            }
            val customTab = buildSlot(
                if (isCustomGoal) "${focusGoal}分钟 ✎" else "自定义",
                null,
                isCustomGoal
            ) {
                StudyFocusDialogs.showCustomSessionGoalDialog(fragment)
            }
            goals.addView(customTab, LinearLayout.LayoutParams(0, -2, 1f))
            ForestUi.add(timer, goals, 6)
        } else {
            StudySheet.addHairline(timer, 6, 8)
            val activeBar = row(ctx).apply {
                setPadding(fragment.dp(4), fragment.dp(2), fragment.dp(2), fragment.dp(2))
            }
            statusTxt = text(
                ctx,
                if (n.running()) "静心计时中 · 随时可暂停" else "已暂停 · 暂停时长不计入",
                12,
                MUTED
            )
            activeBar.addView(statusTxt, LinearLayout.LayoutParams(0, -2, 1f))
            lateinit var wakeBtn: TextView
            wakeBtn = StudySheet.link(
                ctx,
                if (initialKeepScreenOn) "常亮已开" else "屏幕常亮",
                if (initialKeepScreenOn) 0xff2a4e2f.toInt() else MUTED
            ) {
                val keep = onToggleKeepScreenOn()
                wakeBtn.text = if (keep) "常亮已开" else "屏幕常亮"
                wakeBtn.setTextColor(if (keep) 0xff2a4e2f.toInt() else MUTED)
                wakeBtn.background = ripple(
                    ctx,
                    rounded(ctx, if (keep) 0xffeef4ea.toInt() else 0xfffffefa.toInt(), 0x225c4938, 999),
                    false
                )
            }.apply {
                textSize = 11f
                minimumHeight = fragment.dp(28)
                setPadding(fragment.dp(11), fragment.dp(4), fragment.dp(11), fragment.dp(4))
                background = ripple(
                    ctx,
                    rounded(
                        ctx,
                        if (initialKeepScreenOn) 0xffeef4ea.toInt() else 0xfffffefa.toInt(),
                        0x225c4938,
                        999
                    ),
                    false
                )
            }
            activeBar.addView(wakeBtn)
            timer.addView(activeBar, LinearLayout.LayoutParams(-1, -2))
        }

        val controls = row(ctx).apply {
            setPadding(fragment.dp(6), 0, fragment.dp(6), 0)
        }
        val mainBtnLabel = when {
            active -> if (n.running()) "暂停一下" else "继续学习"
            n.hasSession() -> "返回原计划保存计时"
            else -> "开始专注，种下今天"
        }
        val mainBtn = button(ctx, mainBtnLabel, true).apply {
            id = R.id.study_start_pause_button
            backgroundTintList = null
            background = buildSculptedFocusCta(ctx, fragment)
            setTextColor(0xfffffdf8.toInt())
            textSize = 15f
            letterSpacing = 0.05f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            stateListAnimator = null
            elevation = fragment.dp(4).toFloat()
            if (Build.VERSION.SDK_INT >= 28) {
                outlineAmbientShadowColor = 0x40365436
                outlineSpotShadowColor = 0x58365436
            }
            setIconResource(if (active && n.running()) R.drawable.ic_study_pause else R.drawable.ic_study_play)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconSize = fragment.dp(16)
            iconPadding = fragment.dp(8)
            iconTint = ColorStateList.valueOf(0xfffffdf8.toInt())
            insetTop = 0
            insetBottom = 0
            setOnClickListener { onStartOrPause() }
            ForestMotion.bind(this)
        }
        controls.addView(mainBtn, LinearLayout.LayoutParams(0, fragment.dp(50), 1f))
        if (active) {
            val finish = button(ctx, "完成学习", false).apply {
                id = R.id.study_finish_button
                cornerRadius = fragment.dp(25)
                backgroundTintList = ColorStateList.valueOf(0xfffffefa.toInt())
                strokeColor = ColorStateList.valueOf(0x36456444)
                strokeWidth = fragment.dp(1)
                setTextColor(0xff315e3a.toInt())
                insetTop = 0
                insetBottom = 0
                setOnClickListener { fragment.perform { StudyFocusDialogs.showFinishDialog(fragment) } }
                ForestMotion.bind(this)
            }
            controls.addView(
                finish,
                LinearLayout.LayoutParams(fragment.dp(98), fragment.dp(50)).apply { leftMargin = fragment.dp(10) }
            )
        }
        ForestUi.add(timer, controls, 18)

        if (active) {
            if (fragment.store.elapsedMillis() < 3 * 60_000L) {
                val withdraw = StudySheet.link(ctx, "误触开始？撤回本次专注", 0xff8d4836.toInt()) {
                    StudyFocusDialogs.showConfirmDiscard(fragment, fragment.newSheet())
                }.apply {
                    textSize = 12f
                    minimumHeight = fragment.dp(34)
                    setPadding(fragment.dp(12), fragment.dp(5), fragment.dp(12), fragment.dp(5))
                    background = ripple(ctx, rounded(ctx, 0xfff9efea.toInt(), 0xffe6cfc5.toInt(), 999), false)
                }
                ForestUi.add(timer, withdraw, 10)
            } else {
                val withdraw = StudySheet.link(ctx, "放弃本次专注", 0xff8f8578.toInt()) {
                    StudyFocusDialogs.showConfirmDiscard(fragment, fragment.newSheet())
                }.apply {
                    textSize = 11f
                    minimumHeight = fragment.dp(32)
                    setPadding(fragment.dp(10), fragment.dp(4), fragment.dp(10), fragment.dp(4))
                }
                ForestUi.add(timer, withdraw, 6)
            }
        }

        val gentleNote = text(
            ctx,
            when {
                active -> "完成后可自由切换科目 · 满 25 分钟累计 1 水滴"
                initialCountdownMode -> "倒计时模式 · 归零后自动转为加时计数"
                else -> "正向计时 · 随时暂停 · 到达目标仍可继续"
            },
            11,
            0xff8a8172.toInt()
        ).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.02f
        }
        gentleNoteRef = gentleNote
        ForestUi.add(timer, gentleNote, 12)

        if (!n.hasSession()) {
            ForestUi.add(
                timer,
                StudySheet.link(ctx, "添加本次目标", DEEP) { StudyFocusDialogs.showSessionGoalDialog(fragment) }
                    .apply { minimumHeight = fragment.dp(36) },
                2
            )
        } else if (active && !StudyNotifications.permitted(ctx)) {
            ForestUi.add(
                timer,
                StudySheet.link(ctx, "开启锁屏与通知栏计时", DEEP) { fragment.allowStudyNotifications() }
                    .apply { minimumHeight = fragment.dp(36) },
                2
            )
        }

        return Holder(
            root = timer,
            subjectSelector = selector,
            focusStatusPill = pill,
            focusStatusDot = dot,
            focusStatusLabel = statusLbl,
            statusPulseAnimator = pulseAnimator,
            clock = clockView,
            dial = dialDrawable,
            dialGoalCaption = goalCap,
            dialRightCaption = rightCap,
            activeStatusText = statusTxt,
            startPauseButton = mainBtn,
            focusGentleNote = gentleNote,
            goalSlots = goalSlots
        )
    }

    private fun styleGoalSlot(slot: GoalTabSlot, selected: Boolean) {
        slot.label.setTextColor(if (selected) 0xff284d2f.toInt() else 0xff827b6d.toInt())
        slot.label.typeface = Typeface.create(
            if (selected) "sans-serif-medium" else "sans-serif",
            if (selected) Typeface.BOLD else Typeface.NORMAL
        )
        slot.indicator.background = rounded(
            slot.label.context,
            if (selected) 0xff456444.toInt() else Color.TRANSPARENT,
            0,
            99
        )
    }

    private fun buildFocusCardBackground(ctx: Context, fragment: BaseStudyFragment): Drawable {
        val radius = fragment.dp(22).toFloat()
        val body = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xf2fffefa.toInt(), 0xe8f9f5eb.toInt())
        ).apply {
            cornerRadius = radius
            setStroke(max(1, fragment.dp(0.85f)), 0x265c4938)
        }
        val innerHighlight = GradientDrawable().apply {
            cornerRadius = radius - fragment.dp(1)
            setColor(Color.TRANSPARENT)
            setStroke(max(1, fragment.dp(0.75f)), 0x88ffffff.toInt())
        }
        return LayerDrawable(arrayOf(body, InsetDrawable(innerHighlight, fragment.dp(1))))
    }

    private fun buildSculptedFocusCta(ctx: Context, fragment: BaseStudyFragment): Drawable {
        val radius = fragment.dp(25).toFloat()
        val normalBase = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xff52764f.toInt(), 0xff3b5b3a.toInt())
        ).apply {
            cornerRadius = radius
            setStroke(max(1, fragment.dp(1)), 0xff324f31.toInt())
        }
        val normalHighlight = GradientDrawable().apply {
            cornerRadius = radius - fragment.dp(1)
            setColor(Color.TRANSPARENT)
            setStroke(max(1, fragment.dp(0.8f)), 0x33ffffff)
        }
        val normal = LayerDrawable(arrayOf(normalBase, InsetDrawable(normalHighlight, fragment.dp(1))))

        val pressed = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xff3f5e3e.toInt(), 0xff2f4a2e.toInt())
        ).apply {
            cornerRadius = radius
            setStroke(max(1, fragment.dp(1)), 0xff284027.toInt())
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    internal class AgendaPreviewHolder(
        val today: TextView,
        val dailyProgressBar: ProgressBar,
        val dailyProgressText: TextView
    )

    fun attachAgendaPreview(
        fragment: StudyFocusFragment,
        parent: LinearLayout,
        plan: StudyPlan,
        next: StudyWork.Task?,
        upcoming: List<StudySubject>
    ): AgendaPreviewHolder {
        val ctx = fragment.ctx()
        val preview = fragment.statsPanel(LIGHT, LINE)
        val summary = row(ctx)
        val todayView = text(ctx, "", 13, 0xff243624.toInt()).apply {
            id = R.id.study_today_summary
            setLineSpacing(fragment.dp(4).toFloat(), 1f)
        }
        summary.addView(todayView, LinearLayout.LayoutParams(0, -2, 1f))
        val goal = StudySheet.link(ctx, "调整目标", 0xff355336.toInt()) {
            StudyFocusDialogs.showDailyGoalDialog(fragment)
        }.apply {
            id = R.id.study_daily_goal_button
            textSize = 12f
            minimumHeight = fragment.dp(34)
            setPadding(fragment.dp(12), fragment.dp(6), fragment.dp(12), fragment.dp(6))
            background = ripple(ctx, rounded(ctx, 0xfff3efe3.toInt(), 0xffdcd8c8.toInt(), 10), false)
        }
        summary.addView(goal)
        preview.addView(summary)

        val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressDrawable = roundedProgressDrawable(fragment)
        }
        preview.addView(pb, LinearLayout.LayoutParams(-1, fragment.dp(7)).apply { topMargin = fragment.dp(10) })
        val dpText = text(ctx, "", 11, MUTED)
        ForestUi.add(preview, dpText, 6)

        if (next != null) {
            ForestUi.add(
                preview,
                StudySheet.item(
                    ctx, "接着学 · ${plan.book(next.subjectId).title}", next.title,
                    R.drawable.ic_study_book, StudySheet.MOSS, "›", false
                ) { fragment.perform { fragment.startTask(next.id) } },
                6
            )
        }
        if (upcoming.isNotEmpty()) {
            val closest = upcoming[0]
            ForestUi.add(
                preview,
                StudySheet.item(ctx, "最近截止 · ${closest.title}", closest.due, 0, 0xfff5f1e6.toInt(), "›", false) {
                    StudyAgendaDialogs.showAgendaSheet(fragment)
                },
                6
            )
        }
        ForestUi.add(parent, preview, 12)
        return AgendaPreviewHolder(todayView, pb, dpText)
    }

    private fun roundedProgressDrawable(fragment: BaseStudyFragment): Drawable {
        val bg = GradientDrawable().apply { setColor(0xffe7e3d6.toInt()); cornerRadius = fragment.dp(99).toFloat() }
        val fill = GradientDrawable().apply { setColor(0xff456444.toInt()); cornerRadius = fragment.dp(99).toFloat() }
        val clip = ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)
        return LayerDrawable(arrayOf<Drawable>(bg, clip)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }
}

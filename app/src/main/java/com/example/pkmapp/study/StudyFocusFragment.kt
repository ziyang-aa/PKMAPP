package com.example.pkmapp.study

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import com.example.pkmapp.R
import com.example.pkmapp.core.datastore.AppPreferences
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestSheetHelper
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.GREEN
import com.example.pkmapp.forest.ForestUi.LIGHT
import com.example.pkmapp.forest.ForestUi.LINE
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.button
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.heading
import com.example.pkmapp.forest.ForestUi.image
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class StudyFocusFragment : BaseStudyFragment() {

    private var timerHolder: StudyFocusTimerCard.Holder? = null
    private var statusPulseAnimator: ObjectAnimator? = null
    private var today: TextView? = null
    private var dailyProgressText: TextView? = null
    private var dailyProgressBar: ProgressBar? = null
    internal val subjectSelector: MaterialButton?
        get() = timerHolder?.subjectSelector

    private var countdownMode = false
    private var keepScreenOn = false
    private var renderedToday: String? = null
    private var renderedRunning = false
    private var renderedSession = ""
    private var lastControlClick = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!isAdded || body == null) return
            val notebook = store.notebook()
            if (notebook.sessionId() != renderedSession) {
                render()
            } else if (notebook.running() != renderedRunning) {
                if (timerHolder != null && notebook.currentHasSession()) {
                    syncRunningStateInPlace(notebook.running())
                } else {
                    render()
                }
            } else {
                updateTimer()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBeforeRender() {
        statusPulseAnimator?.cancel()
        statusPulseAnimator = null
        timerHolder = null
        today = null
        dailyProgressText = null
        dailyProgressBar = null
        renderedToday = null
        val uiSnapshot = AppPreferences.getInstance(ctx()).current
        countdownMode = uiSnapshot.countdownMode
        keepScreenOn = uiSnapshot.keepScreenOn
        val notebook = store.notebook()
        renderedRunning = notebook.running()
        renderedSession = notebook.sessionId()
    }

    override fun onAfterRender() {
        updateTimer()
    }

    override fun onResume() {
        super.onResume()
        val args = arguments
        if (args != null) {
            val action = args.getString("notificationAction")
            args.remove("notificationAction")
            if (StudyNotifications.FINISH_TIMER == action &&
                store.notebook().sessionId() == args.getString(StudyNotifications.SESSION)
            ) {
                perform { StudyFocusDialogs.showFinishDialog(this) }
            } else if (StudyNotifications.OPEN_AGENDA == action) {
                StudyAgendaDialogs.showAgendaSheet(this)
            }
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        statusPulseAnimator?.cancel()
        statusPulseAnimator = null
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onBeforeDestroyViews() {
        handler.removeCallbacks(tick)
        statusPulseAnimator?.cancel()
        statusPulseAnimator = null
        timerHolder = null
        today = null
        dailyProgressText = null
        dailyProgressBar = null
    }

    override fun buildPage(body: LinearLayout) {
        val n = store.notebook()
        if (n.hasSession() && !n.currentHasSession()) {
            val banner = content()
            val activePlan = n.plan(n.activePlanId())
            val back = button(ctx(), "计时归属 · ${activePlan.title} ›", false).apply {
                setOnClickListener { returnToTimer(activePlan.id) }
            }
            banner.addView(back)
            body.addView(banner)
        }

        val plan = n.currentPlan()
        val partner = plan?.partner ?: StudyPlan.Partner.SQUIRREL
        val active = n.currentHasSession()
        setBarTitle("森林自习室")

        val content = content()
        body.addView(content)

        val heroRow = row(ctx()).apply {
            gravity = Gravity.BOTTOM
            setPadding(0, dp(2), 0, dp(10))
            background = bottomHairlineBg(0x1f5c4938)
        }
        val heroCopy = column(ctx())
        val datePartner = text(
            ctx(),
            SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(Date()) + " · 伙伴·${partner.title}",
            13,
            0xff6b563f.toInt()
        ).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        heroCopy.addView(datePartner, LinearLayout.LayoutParams(-1, -2))

        if (plan != null) {
            val planWrap = row(ctx()).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                minimumHeight = dp(40)
                setPadding(0, dp(4), dp(6), dp(4))
                background = ripple(ctx(), rounded(ctx(), Color.TRANSPARENT, 0, 10), false)
                setOnClickListener { planDialogs().manager() }
                ForestMotion.bind(this)
            }
            val planDot = View(ctx()).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = rounded(ctx(), GREEN, 0, 99)
            }
            planWrap.addView(planDot, LinearLayout.LayoutParams(dp(7.5f), dp(7.5f)).apply { marginEnd = dp(8) })
            val plans = StudySheet.link(ctx(), "当前计划 · ${plan.title} ▾", 0xff243624.toInt()) {
                planDialogs().manager()
            }.apply {
                textSize = 18f
                typeface = Typeface.create("serif", Typeface.BOLD)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 0)
                background = null
            }
            planWrap.addView(plans, LinearLayout.LayoutParams(-2, -2))
            heroCopy.addView(planWrap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        heroRow.addView(heroCopy, LinearLayout.LayoutParams(0, -2, 1f))
        heroRow.addView(
            image(ctx(), partnerDrawable(partner), null),
            LinearLayout.LayoutParams(dp(70), dp(66)).apply { marginStart = dp(10) }
        )
        ForestUi.add(content, heroRow, 4)

        if (plan == null) {
            val empty = card()
            empty.addView(heading(ctx(), "还没有学习计划", 19))
            ForestUi.add(empty, note("新建计划并添加科目后，就可以开始计时。"), 8)
            val create = button(ctx(), "新建计划", true).apply { setOnClickListener { planDialogs().newPlan() } }
            ForestUi.add(empty, create, 14)
            empty.addView(StudySheet.link(ctx(), "学习备份", DEEP) { backupMenu(newSheet()) })
            ForestUi.add(content, empty, 8)
            return
        }

        if (plan.due.isNotEmpty() || plan.previous.isNotEmpty()) {
            val metaRow = row(ctx()).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val countdown = ExamCountdownFormatter.evaluate(plan.title, plan.due, System.currentTimeMillis())
            if (countdown != null) {
                val countdownView = text(ctx(), countdown.focusHeadline, 12, countdown.accentColor).apply {
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    fontFeatureSettings = "tnum"
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    setOnClickListener { planDialogs().manager() }
                }
                metaRow.addView(countdownView, LinearLayout.LayoutParams(0, -2, 1f))
            } else {
                metaRow.addView(View(ctx()), LinearLayout.LayoutParams(0, 1, 1f))
            }
            if (plan.previous.isNotEmpty()) {
                val previous = n.plan(plan.previous)
                val prior = StudySheet.link(ctx(), "上一轮 · ${previous.title} ›", DEEP) {
                    openHistory(previous.id)
                }.apply {
                    textSize = 11f; minimumHeight = dp(28)
                    setPadding(dp(10), dp(3), dp(10), dp(3))
                    background = ripple(ctx(), rounded(ctx(), 0xfff3efe3.toInt(), 0xffdcd8c8.toInt(), 999), false)
                }
                metaRow.addView(prior)
            }
            ForestUi.add(content, metaRow, 4)
        }

        if (plan.archived != 0L) {
            ForestUi.add(content, note("此计划已归档，可以查看足迹或恢复学习。"), 12)
            ForestUi.add(content, button(ctx(), "查看学习足迹", false).apply { setOnClickListener { openHistory(plan.id) } }, 8)
            val restore = button(ctx(), "恢复计划", true).apply {
                setOnClickListener {
                    perform {
                        store.update { book -> book.restore(plan.id); null }
                        render()
                    }
                }
            }
            ForestUi.add(content, restore, 8)
            return
        }

        val next = if (!n.hasSession()) n.nextTask(plan.id) else null
        val upcoming = StudyWorkDialogs.deadlines(plan)
        val builtTimer = StudyFocusTimerCard.build(
            fragment = this,
            n = n,
            active = active,
            initialCountdownMode = countdownMode,
            initialKeepScreenOn = keepScreenOn,
            onToggleCountdown = {
                countdownMode = !countdownMode
                AppPreferences.getInstance(ctx()).setCountdownMode(countdownMode)
                updateTimer()
                countdownMode
            },
            onToggleKeepScreenOn = {
                keepScreenOn = !keepScreenOn
                AppPreferences.getInstance(ctx()).setKeepScreenOn(keepScreenOn)
                syncKeepScreenOn()
                keepScreenOn
            },
            onSelectGoalMinutes = { minutes ->
                updateSessionGoalInPlace(minutes)
            },
            onOpenSoundSheet = {
                showSoundSheet()
            },
            onStartOrPause = {
                perform {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastControlClick < 280) return@perform
                    lastControlClick = now
                    if (store.notebook().hasSession() && !store.notebook().currentHasSession()) {
                        returnToTimer(store.notebook().activePlanId())
                        return@perform
                    }
                    if (!store.notebook().hasSession()) {
                        ensureNotificationBeforeStart {
                            perform {
                                if (!store.notebook().hasSession()) {
                                    store.start()
                                    render()
                                }
                            }
                        }
                    } else {
                        if (store.notebook().running()) store.pause() else store.resume()
                        syncRunningStateInPlace(store.notebook().running())
                    }
                }
            }
        )
        timerHolder = builtTimer
        statusPulseAnimator = builtTimer.statusPulseAnimator
        content.addView(builtTimer.root, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (active) syncKeepScreenOn()
        val agendaHolder = StudyFocusTimerCard.attachAgendaPreview(
            this, content, plan, next, if (active) Collections.emptyList() else upcoming
        )
        today = agendaHolder.today
        dailyProgressBar = agendaHolder.dailyProgressBar
        dailyProgressText = agendaHolder.dailyProgressText
    }

    private fun returnToTimer(planId: String) {
        perform {
            store.update { n -> n.switchPlan(planId); null }
            render()
        }
    }

    internal fun startTask(id: String) {
        ensureNotificationBeforeStart {
            perform {
                store.start(id, "")
                activeSheet?.dismiss()
                render()
                scroll?.post {
                    val scrollView = scroll ?: return@post
                    val selector = subjectSelector ?: return@post
                    val bounds = Rect()
                    selector.getDrawingRect(bounds)
                    scrollView.offsetDescendantRectToMyCoords(selector, bounds)
                    scrollView.scrollTo(0, max(0, bounds.top - scrollView.paddingTop - dp(14)))
                }
            }
        }
    }

    internal fun updateSessionGoalInPlace(minutes: Int, alreadySaved: Boolean = false) {
        perform {
            if (!alreadySaved) {
                store.update { book -> book.setSessionGoal(minutes); null }
            }
            val holder = timerHolder
            if (holder != null && !store.notebook().currentHasSession()) {
                holder.syncSessionGoal(minutes)
                updateTimer()
            } else {
                render()
            }
        }
    }

    internal fun pauseUiInPlaceOrRender() {
        if (timerHolder != null && store.notebook().currentHasSession()) {
            syncRunningStateInPlace(false)
        } else {
            render()
        }
    }

    private fun updateTimer() {
        val holder = timerHolder
        val notebook = store.notebook()
        val currentPlan = notebook.currentPlan() ?: return
        val active = notebook.currentHasSession()
        val elapsed = if (active) store.elapsedMillis() else 0L
        val goalMin = if (active) notebook.activeGoal() else notebook.sessionGoal()
        val goalMillis = max(1L, goalMin * 60_000L)
        if (holder != null) {
            holder.clock.text = if (countdownMode) {
                when {
                    !active -> StudyFormat.clock(goalMillis)
                    elapsed <= goalMillis -> StudyFormat.clock(goalMillis - elapsed)
                    else -> "+${StudyFormat.clock(elapsed - goalMillis)}"
                }
            } else {
                StudyFormat.clock(elapsed)
            }
            holder.dialGoalCaption.text = (if (countdownMode) "倒计时目标 " else "目标 ") + "$goalMin 分钟"
            holder.dialGoalCaption.setTextColor(MUTED)
            when {
                !active -> {
                    holder.dialRightCaption.text = "0%"
                    holder.dialRightCaption.setTextColor(0xff8f8675.toInt())
                }
                elapsed > goalMillis -> {
                    val overMin = max(1L, (elapsed - goalMillis) / 60_000L)
                    holder.dialRightCaption.text = "已超额 +$overMin 分钟"
                    holder.dialRightCaption.setTextColor(0xff315e3a.toInt())
                }
                else -> {
                    val pct = min(100L, (elapsed * 100L) / goalMillis).toInt()
                    holder.dialRightCaption.text = "已完成 $pct%"
                    holder.dialRightCaption.setTextColor(0xff315e3a.toInt())
                }
            }
            holder.activeStatusText?.text = if (notebook.running()) "静心计时中 · 随时可暂停" else "已暂停 · 暂停时长不计入"
            holder.dial.setProgress(elapsed.toFloat() / goalMillis)
        }
        today?.let { todayView ->
            val planId = currentPlan.id
            val now = System.currentTimeMillis()
            val day = StudyNotebook.dayKey(now)
            if (day == renderedToday) return
            renderedToday = day
            val count = notebook.records().count { it.planId == planId && StudyNotebook.dayKey(it.completedAt) == day }
            val todayMillis = notebook.totalForDay(planId, now)
            val dailyGoalMin = max(1, notebook.dailyGoal())
            val todayMin = todayMillis / 60_000L
            val dailyPct = min(100L, (todayMin * 100L) / dailyGoalMin).toInt()
            todayView.text = "今日已专注 ${StudyFormat.duration(todayMillis)}\n每日目标 ${dailyGoalMin}分钟 · ${count}次学习"
            dailyProgressBar?.progress = dailyPct
            dailyProgressText?.apply {
                if (todayMin >= dailyGoalMin) {
                    text = "今日目标已达成 ✓（100%）"
                    setTextColor(0xff34603a.toInt())
                } else {
                    text = "今日进度 $dailyPct% · 还差 ${dailyGoalMin - todayMin} 分钟达标"
                    setTextColor(MUTED)
                }
            }
        }
    }

    private fun syncRunningStateInPlace(running: Boolean) {
        renderedRunning = running
        val holder = timerHolder
        statusPulseAnimator?.cancel()
        statusPulseAnimator = null
        if (holder != null) {
            holder.focusStatusPill.background = null
            holder.focusStatusDot.alpha = 1f
            holder.focusStatusDot.background = rounded(
                ctx(), if (running) 0xff456444.toInt() else 0xffb8894f.toInt(), 0, 99
            )
            if (running && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())) {
                statusPulseAnimator = ObjectAnimator.ofFloat(holder.focusStatusDot, View.ALPHA, 1f, 0.35f).apply {
                    duration = 1100
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
            holder.focusStatusLabel.text = if (running) "正在专注" else "休息一下"
            holder.focusStatusLabel.setTextColor(if (running) 0xff315e3a.toInt() else 0xff7b5c3b.toInt())
            holder.activeStatusText?.text = if (running) "静心计时中 · 随时可暂停" else "已暂停 · 暂停时长不计入"
            holder.startPauseButton.text = if (running) "暂停一下" else "继续学习"
            holder.startPauseButton.setIconResource(
                if (running) R.drawable.ic_study_pause else R.drawable.ic_study_play
            )
        }
        syncKeepScreenOn()
        updateTimer()
        if (isAdded) sharedViewModel.syncFromStores(ctx())
    }

    private fun showSoundSheet() {
        val context = ctx()
        val sheet = ForestSheetHelper.createSheet(context)
        val root = ForestSheetHelper.createSheetRoot(
            context,
            sheet,
            "森林白噪音",
            "专注开始时自动轻声伴随，暂停或结束时自动停止"
        )

        var selected = ForestSoundEngine.selectedSound(context)
        val rowHolders = ArrayList<Pair<ForestSoundEngine.AmbientSound, Pair<View, TextView>>>()
        val previewButtons = LinkedHashMap<ForestSoundEngine.AmbientSound, TextView>()

        fun refreshSelection() {
            for ((sound, pair) in rowHolders) {
                val (dot, titleView) = pair
                val isChosen = sound == selected
                dot.background = rounded(
                    context,
                    if (isChosen) 0xff456444.toInt() else 0x265c4938,
                    0,
                    99
                )
                titleView.setTextColor(if (isChosen) DEEP else ForestSheetHelper.INK)
                titleView.typeface = Typeface.create(
                    if (isChosen) "sans-serif-medium" else "sans-serif",
                    if (isChosen) Typeface.BOLD else Typeface.NORMAL
                )
            }
            timerHolder?.syncSoundLabel(selected)
        }

        val sounds = ForestSoundEngine.AmbientSound.entries
        sounds.forEachIndexed { index, sound ->
            val item = row(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(2), dp(8), dp(2), dp(8))
                background = ripple(context, rounded(context, Color.TRANSPARENT, 0, 10), false)
                setOnClickListener {
                    selected = sound
                    ForestSoundEngine.setSelectedSound(context, sound)
                    refreshSelection()
                }
            }
            val dot = View(context)
            item.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(12) })

            val copy = column(context)
            val title = text(context, sound.title, 15, ForestSheetHelper.INK)
            val sub = text(context, sound.subtitle, 11, MUTED)
            copy.addView(title)
            ForestUi.add(copy, sub, 2)
            item.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            rowHolders.add(sound to (dot to title))

            if (sound != ForestSoundEngine.AmbientSound.NONE) {
                val previewBtn = ForestSheetHelper.quickChip(context, "试听 5s") {
                    for ((s, btn) in previewButtons) {
                        btn.text = if (s == sound) "试听中…" else "试听 5s"
                    }
                    ForestSoundEngine.preview(context, sound, 5_000L) {
                        previewButtons[sound]?.post {
                            previewButtons[sound]?.text = "试听 5s"
                        }
                    }
                }
                previewButtons[sound] = previewBtn
                item.addView(previewBtn, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            }
            root.addView(item, LinearLayout.LayoutParams(-1, -2))
            if (index < sounds.lastIndex) {
                ForestSheetHelper.addHairline(root, 0, 0)
            }
        }

        ForestSheetHelper.addHairline(root, 10, 12)
        val initialVol = ForestSoundEngine.volume(context)
        val volHeader = row(context)
        volHeader.addView(ForestSheetHelper.fieldLabel(context, "白噪音音量"), LinearLayout.LayoutParams(0, -2, 1f))
        val volValue = text(context, "${(initialVol * 100).toInt()}%", 12, DEEP).apply {
            fontFeatureSettings = "tnum"
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        volHeader.addView(volValue)
        root.addView(volHeader, LinearLayout.LayoutParams(-1, -2))

        val seekBar = SeekBar(context).apply {
            max = 95 // maps to 5%..100%
            progress = ((initialVol * 100).toInt() - 5).coerceIn(0, 95)
            progressTintList = ColorStateList.valueOf(ForestSheetHelper.PINE)
            thumbTintList = ColorStateList.valueOf(ForestSheetHelper.PINE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    val pct = (value + 5).coerceIn(5, 100)
                    volValue.text = "$pct%"
                    if (fromUser) {
                        ForestSoundEngine.setVolume(context, pct / 100f)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }
        ForestUi.add(root, seekBar, 8)

        refreshSelection()
        sheet.setOnDismissListener {
            ForestSoundEngine.stopPreviewAndRestore(context)
            timerHolder?.syncSoundLabel(ForestSoundEngine.selectedSound(context))
        }
        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root)
        sheet.show()
    }

    private fun syncKeepScreenOn() {
        val act = activity ?: return
        if (!isAdded) return
        val keep = keepScreenOn && store.notebook().currentHasSession()
        if (keep) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        @JvmStatic
        fun fromNotification(action: String, id: String?): StudyFocusFragment =
            StudyFocusFragment().apply {
                arguments = Bundle().apply {
                    putString("notificationAction", action)
                    putString(StudyNotifications.SESSION, id)
                }
            }
    }
}

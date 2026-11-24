package com.example.pkmapp.study

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.pkmapp.R
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.GREEN
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.WOOD
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.heading
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.navigation.AppDestination
import com.google.android.material.checkbox.MaterialCheckBox

internal object StudyFocusDialogs {

    fun showSubjectSheet(fragment: StudyFocusFragment) {
        val notebook = fragment.store.notebook()
        if (notebook.currentHasSession()) return
        val plan = notebook.currentPlan() ?: return
        val sheet = fragment.newSheet()
        val pane = fragment.sheetPane(
            "今天学哪一门？",
            "${plan.title} · ${plan.books().size} 门科目",
            R.drawable.ic_study_book
        )
        pane.body.id = R.id.study_subject_picker
        for (subject in plan.books()) {
            ForestUi.add(pane.body, subjectChoice(fragment, sheet, subject), 5)
        }
        sheet.push(pane)
    }

    private fun subjectChoice(
        fragment: StudyFocusFragment,
        sheet: StudySheet,
        subject: StudySubject
    ): View {
        val ctx = fragment.ctx()
        val n = fragment.store.notebook()
        var details = subject.hint
        if (subject.due.isNotEmpty()) details += (if (details.isEmpty()) "" else " · ") + subject.due
        if (subject.assessment.isNotEmpty()) details += (if (details.isEmpty()) "" else " · ") + subject.assessment
        val selected = subject == n.selected()
        val choice = StudySheet.item(
            ctx,
            subject.title,
            details.ifEmpty { null },
            0,
            if (selected) StudySheet.MOSS else Color.TRANSPARENT,
            if (selected) "✓" else null,
            selected
        ) {
            fragment.perform {
                fragment.store.update { book -> book.select(subject); null }
                sheet.dismiss()
                fragment.render()
                fragment.subjectSelector?.let { ForestMotion.reveal(it) }
            }
        }
        choice.contentDescription = "选择${subject.title}" + if (selected) "，当前科目" else ""
        val dot = View(ctx).apply {
            background = rounded(ctx, subject.color, 0, 5)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        choice.addView(dot, 0, LinearLayout.LayoutParams(fragment.dp(9), fragment.dp(9)).apply { marginEnd = fragment.dp(12) })
        return choice
    }

    fun showCustomSessionGoalDialog(fragment: StudyFocusFragment) {
        if (fragment.store.notebook().currentHasSession()) return
        val ctx = fragment.ctx()
        val sheet = fragment.newSheet()
        val pane = fragment.sheetPane("自定义专注时长", "单次目标 5 至 360 分钟，满 25 分钟累计水滴。", 0)
        StudySheet.addHairline(pane.body, 0, 12)

        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(fragment.store.notebook().sessionGoal().toString())
            setSelectAllOnFocus(true)
            contentDescription = "自定义专注目标分钟"
            StudySheet.styleUnderlineField(this)
            textSize = 22f
            typeface = Typeface.create("serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        pane.body.addView(StudySheet.section(ctx, "单次目标时长（分钟）"))
        ForestUi.add(pane.body, input, 4)

        val presets = row(ctx)
        val presetValues = intArrayOf(15, 30, 90, 120)
        val presetChips = ArrayList<TextView>(4)
        val syncPresets = {
            val cur = input.text.toString().trim()
            for (i in presetValues.indices) {
                StudySheet.styleChoiceChip(presetChips[i], cur == presetValues[i].toString())
            }
        }
        for (i in presetValues.indices) {
            val minutes = presetValues[i]
            val option = StudySheet.link(ctx, "$minutes 分钟", DEEP) {
                input.setText(minutes.toString())
                input.setSelection(input.text.length)
            }.apply {
                textSize = 12f; fontFeatureSettings = "tnum"; minimumHeight = fragment.dp(36)
            }
            presets.addView(option, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = fragment.dp(8) })
            presetChips.add(option)
        }
        ForestUi.add(pane.body, presets, 14)

        val errorMessage = text(ctx, "", 12, 0xff8d4836.toInt()).apply { visibility = View.GONE }
        ForestUi.add(pane.body, errorMessage, 8)
        input.addTextChangedListener(StudyTodoDialogs.simpleWatcher {
            errorMessage.visibility = View.GONE
            syncPresets()
        })
        syncPresets()

        pane.primary("保存时长") {
            try {
                val value = input.text.toString().trim().toInt()
                fragment.store.update { n -> n.setSessionGoal(value); null }
                sheet.dismiss()
                fragment.render()
            } catch (error: RuntimeException) {
                if (error !is IllegalArgumentException && error !is IllegalStateException) throw error
                errorMessage.text = if (error is NumberFormatException) "请输入 5 至 360 之间的分钟数" else error.message
                errorMessage.visibility = View.VISIBLE
            }
        }
        sheet.push(pane)
    }

    fun showSessionGoalDialog(fragment: StudyFocusFragment) {
        val sheet = fragment.newSheet()
        val work = fragment.workDialogs(sheet)
        val pane = work.pane("本次目标", fragment.store.notebook().selected().title)
        val goal = work.input(pane.body, "这次要做什么", "例如 图的遍历 · 第 5–8 题", "", 120)
        pane.primary("以此开始专注") {
            work.save {
                val title = StudyPlan.required(StudyWorkDialogs.value(goal), 120, "学习目标")
                fragment.store.start("", title)
                sheet.dismiss()
                fragment.render()
            }
        }
        sheet.push(pane)
    }

    fun showDailyGoalDialog(fragment: StudyFocusFragment) {
        val ctx = fragment.ctx()
        val sheet = fragment.newSheet()
        val pane = fragment.sheetPane("每日学习目标", "每天首次达标时获得一颗种子。", 0)
        StudySheet.addHairline(pane.body, 0, 12)

        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(fragment.store.notebook().dailyGoal().toString())
            setSelectAllOnFocus(true)
            contentDescription = "每日学习目标分钟"
            StudySheet.styleUnderlineField(this)
            textSize = 22f
            typeface = Typeface.create("serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        pane.body.addView(StudySheet.section(ctx, "目标时长（分钟）"))
        ForestUi.add(pane.body, input, 4)

        val presets = row(ctx)
        val presetValues = intArrayOf(60, 120, 180)
        val presetChips = ArrayList<TextView>(3)
        val syncPresets = {
            val cur = input.text.toString().trim()
            for (i in presetValues.indices) {
                StudySheet.styleChoiceChip(presetChips[i], cur == presetValues[i].toString())
            }
        }
        for (i in presetValues.indices) {
            val minutes = presetValues[i]
            val option = StudySheet.link(ctx, "$minutes 分钟", DEEP) {
                input.setText(minutes.toString())
                input.setSelection(input.text.length)
            }.apply {
                textSize = 12f; fontFeatureSettings = "tnum"; minimumHeight = fragment.dp(38)
            }
            presets.addView(option, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = fragment.dp(8) })
            presetChips.add(option)
        }
        ForestUi.add(pane.body, presets, 14)

        val errorMessage = text(ctx, "", 12, 0xff8d4836.toInt()).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        ForestUi.add(pane.body, errorMessage, 8)
        input.addTextChangedListener(StudyTodoDialogs.simpleWatcher {
            errorMessage.visibility = View.GONE
            syncPresets()
        })
        syncPresets()

        pane.primary("保存目标") {
            try {
                val value = input.text.toString().trim().toInt()
                fragment.store.update { n -> n.setDailyGoal(value); null }
                sheet.dismiss()
                fragment.render()
            } catch (error: RuntimeException) {
                if (error !is IllegalArgumentException && error !is IllegalStateException) throw error
                errorMessage.text = if (error is NumberFormatException) "请输入10至720之间的分钟数" else error.message
                errorMessage.visibility = View.VISIBLE
            }
        }
        sheet.push(pane)
    }

    fun showFinishDialog(fragment: StudyFocusFragment) {
        if (fragment.store.notebook().running()) {
            fragment.store.pause()
            fragment.pauseUiInPlaceOrRender()
        }
        val elapsed = fragment.store.elapsedMillis()
        if (elapsed < 1000) {
            showConfirmDiscard(fragment, fragment.newSheet())
            return
        }
        val ctx = fragment.ctx()
        val sheet = fragment.newSheet()
        val work = fragment.workDialogs(sheet)
        val pane = fragment.sheetPane("保存这段专注", null, 0)
        val notebook = fragment.store.notebook()
        val sessionId = notebook.sessionId()
        val plan = notebook.plan(notebook.activePlanId())
        val task = notebook.task(notebook.activeTaskId())
        var chosenSubject = notebook.activeSubject()
        var chosenDuration = elapsed

        StudySheet.addHairline(pane.body, 0, 14)
        val receipt = row(ctx)
        val receiptCopy = column(ctx)
        val subjectName = text(ctx, "", 13, 0xff355336.toInt()).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            text = ForestUi.badgeText(ctx, chosenSubject.hint, chosenSubject.title, "")
        }
        val length = heading(ctx, StudyFormat.duration(elapsed), 30).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            setTextColor(0xff243624.toInt())
        }
        receiptCopy.addView(subjectName)
        ForestUi.add(receiptCopy, length, 5)
        receipt.addView(receiptCopy, LinearLayout.LayoutParams(0, -2, 1f))

        val correctBtn = StudySheet.link(ctx, "修正时长或科目", 0xff355336.toInt()) {
            val correction = work.pane("修正本次学习", "时长计入保存当天")
            val selected = work.chooseSubject(correction.body, plan, chosenSubject)
            val time = work.input(
                correction.body, "学习时长（时:分:秒）", "例如 00:35:00", StudyFormat.clock(chosenDuration), 16
            )
            correction.primary("应用修正") {
                work.save {
                    chosenDuration = StudyWorkDialogs.duration(time, chosenDuration)
                    chosenSubject = selected.selected
                    subjectName.text = ForestUi.badgeText(ctx, chosenSubject.hint, chosenSubject.title, "")
                    length.text = StudyFormat.duration(chosenDuration)
                    sheet.pop()
                }
            }
            sheet.push(correction)
        }.apply {
            textSize = 12f; minimumHeight = fragment.dp(34)
            setPadding(fragment.dp(12), fragment.dp(6), fragment.dp(12), fragment.dp(6))
            background = ripple(ctx, rounded(ctx, 0xfff2eee3.toInt(), 0, 999), false)
        }
        receipt.addView(correctBtn, LinearLayout.LayoutParams(-2, -2).apply { marginStart = fragment.dp(10) })
        pane.body.addView(receipt, LinearLayout.LayoutParams(-1, -2))
        StudySheet.addHairline(pane.body, 14, 4)

        var doneBox: MaterialCheckBox? = null
        if (task != null) {
            doneBox = ForestUi.flatCheckWithSubtitle(ctx, "本次目标已完成", task.title)
            pane.body.addView(doneBox, LinearLayout.LayoutParams(-1, -2))
            StudySheet.addHairline(pane.body, 2, 10)
        }

        ForestUi.add(pane.body, StudySheet.section(ctx, "学习手记（选填）"), if (task != null) 0 else 8)
        val input = EditText(ctx).apply {
            id = R.id.study_note_input
            hint = "留一句今天的收获或卡点"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(120))
            minLines = 1
            StudySheet.styleUnderlineField(this)
        }
        ForestUi.add(pane.body, input, 2)

        ForestUi.add(pane.body, StudySheet.section(ctx, "下次从哪继续（选填）"), 14)
        val next = EditText(ctx).apply {
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "例如 接着做第 5–8 题"
            contentDescription = "下次从哪继续（选填）"
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(120))
            StudySheet.styleUnderlineField(this)
        }
        ForestUi.add(pane.body, next, 2)

        val metaBar = row(ctx)
        metaBar.addView(text(ctx, "满 25 分钟自动累计水滴，未满也会留存", 12, MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        val discardLink = StudySheet.link(ctx, "放弃本次", 0xff9b4c38.toInt()) { showConfirmDiscard(fragment, sheet) }.apply {
            textSize = 12f; minimumHeight = fragment.dp(32)
            setPadding(fragment.dp(8), fragment.dp(2), fragment.dp(2), fragment.dp(2))
        }
        metaBar.addView(discardLink)
        ForestUi.add(pane.body, metaBar, 12)

        pane.primary("保存学习") {
            fragment.perform {
                if (fragment.store.notebook().running() || sessionId != fragment.store.notebook().sessionId()) {
                    sheet.dismiss()
                    fragment.render()
                    fragment.toast("计时状态已变化，请重新结束这段学习")
                    return@perform
                }
                val taskDone = doneBox?.isChecked == true
                val reward = fragment.store.finish(
                    input.text.toString(),
                    chosenSubject.id,
                    if (chosenDuration == elapsed) 0 else chosenDuration,
                    taskDone,
                    StudyWorkDialogs.value(next)
                )
                fragment.render()
                val result = fragment.sheetPane("学习已保存", null, 0)
                result.body.addView(rewardReceipt(fragment, reward))
                ForestUi.add(
                    result.body,
                    StudySheet.link(ctx, "去看看我的小树", DEEP) {
                        sheet.dismiss()
                        fragment.open(AppDestination.STUDY_GARDEN)
                    },
                    4
                )
                result.primary("继续学习") { sheet.dismiss() }
                result.secondary("切换科目") {
                    sheet.dismiss()
                    showSubjectSheet(fragment)
                }
                sheet.replace(result)
            }
        }
        pane.secondary("稍后继续") { sheet.dismiss() }
        sheet.push(pane)
    }

    fun showConfirmDiscard(fragment: BaseStudyFragment, sheet: StudySheet) {
        val pane = fragment.sheetPane("放弃这段学习？", "放弃后，本次计时将被清除。", 0)
        pane.primary("保留计时") { sheet.pop() }
        pane.secondary("确认放弃") {
            fragment.perform {
                fragment.store.update { n -> n.discard(); null }
                sheet.dismiss()
                fragment.render()
            }
        }
        sheet.push(pane)
    }

    private fun rewardReceipt(fragment: BaseStudyFragment, reward: StudyNotebook.Completion): View {
        val ctx = fragment.ctx()
        val receipt = column(ctx)
        StudySheet.addHairline(receipt, 0, 14)
        val summary = row(ctx)
        summary.addView(text(ctx, reward.record.subject.title, 13, WOOD), LinearLayout.LayoutParams(0, -2, 1f))
        val stamp = text(ctx, "✓ 已收录", 11, GREEN).apply {
            setPadding(fragment.dp(10), fragment.dp(4), fragment.dp(10), fragment.dp(4))
            background = rounded(ctx, 0xffeef4ea.toInt(), 0, 999)
        }
        summary.addView(stamp)
        ForestMotion.stamp(stamp)
        receipt.addView(summary)
        val dur = heading(ctx, StudyFormat.duration(reward.record.durationMillis), 30).apply {
            fontFeatureSettings = "tnum"
        }
        ForestUi.add(receipt, dur, 10)
        StudySheet.addHairline(receipt, 14, 14)

        val rewards = row(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val drop = ImageView(ctx).apply { setImageResource(R.drawable.ic_study_drop) }
        rewards.addView(drop, LinearLayout.LayoutParams(fragment.dp(22), fragment.dp(22)))
        val earned = text(
            ctx,
            "  +${reward.water} 水滴" + if (reward.seeds > 0) "    +1 种子" else "",
            16,
            DEEP
        ).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        rewards.addView(earned)
        receipt.addView(rewards)
        ForestUi.add(
            receipt,
            fragment.note(
                if (reward.water == 0) "时间已积攒，累计满 25 分钟就能收到水滴。"
                else "把水滴带去林地，给小树一点滋养吧。"
            ),
            10
        )
        return receipt
    }
}

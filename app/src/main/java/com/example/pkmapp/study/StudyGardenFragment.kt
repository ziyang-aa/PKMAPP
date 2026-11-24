package com.example.pkmapp.study

import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.example.pkmapp.R
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.button
import com.example.pkmapp.forest.ForestUi.heading
import com.example.pkmapp.forest.ForestUi.image
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.example.pkmapp.navigation.AppDestination
import com.google.android.material.button.MaterialButton
import kotlin.math.min

class StudyGardenFragment : BaseStudyFragment() {

    private var garden: StudyGardenView? = null
    private var growthProgress: StudyGrowthProgress? = null
    private var wallet: TextView? = null
    private var waterButton: MaterialButton? = null

    override fun onBeforeRender() {
        garden?.setActive(false)
        garden = null; growthProgress = null; wallet = null; waterButton = null
    }

    override fun onAfterRender() {
        if (isResumed) garden?.setActive(true)
    }

    override fun onPause() {
        garden?.setActive(false)
        super.onPause()
    }

    override fun onBeforeDestroyViews() {
        garden?.setActive(false)
        garden = null; growthProgress = null; wallet = null; waterButton = null
    }

    override fun buildPage(body: LinearLayout) {
        val n = store.notebook()
        setBarTitle("我的小林地")
        val content = content()
        body.addView(content)

        val introRow = row(ctx()).apply { gravity = Gravity.CENTER_VERTICAL }
        introRow.addView(note("你在认真生活，森林就在悄悄长大。"), LinearLayout.LayoutParams(0, -2, 1f))
        val walletView = text(ctx(), "水滴 ${n.water()} · 种子 ${n.seeds()}", 13, 0xff2e4f2f.toInt()).apply {
            id = R.id.study_wallet
            fontFeatureSettings = "tnum"
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(ctx(), 0xffe9efe3.toInt(), 0xffbfd0b9.toInt(), 999)
        }
        wallet = walletView
        introRow.addView(walletView, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        ForestUi.add(content, introRow, 8)

        val gardenView = StudyGardenView(ctx()).apply {
            id = R.id.study_garden_view
            setCompanion(partnerDrawable(n.currentPlan()?.partner ?: StudyPlan.Partner.SQUIRREL))
            setState(n.planted(), n.growth())
            setOnClickListener {
                if (busy()) return@setOnClickListener
                if (store.notebook().planted()) {
                    greet()
                    toast("叶子轻轻晃了晃：今天也一起加油。摸摸不消耗水滴。")
                } else {
                    toast("这片小小的空地，正等你种下一颗种子。")
                }
            }
        }
        garden = gardenView
        content.addView(gardenView, LinearLayout.LayoutParams(-1, dp(248)).apply { topMargin = dp(12) })

        val name = heading(ctx(), if (n.planted()) StudyGrowth.name(n.growth()) else "种下一点新的期待", 22)
            .apply { gravity = Gravity.CENTER }
        ForestUi.add(content, name, 17)

        val subText = if (n.planted()) {
            "这是你的第${n.trees() + 1}棵树 · 成长值 ${n.growth()} / ${StudyGrowth.REQUIRED_WATER}\n" +
                "再浇${StudyGrowth.REQUIRED_WATER - n.growth()}次，就能住进小森林。"
        } else {
            "用1颗种子，开始照顾一棵小树。\n只要愿意开始，什么时候都不算晚。"
        }
        ForestUi.add(content, note(subText).apply { gravity = Gravity.CENTER }, 9)

        val progress = StudyGrowthProgress(ctx(), n.growth())
        growthProgress = progress
        content.addView(progress, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(11) })

        if (n.planted()) {
            val milestone = StudyGrowth.milestone(StudyGrowth.stage(n.growth()) + 1)
            val next = note("距离「${StudyGrowth.name(milestone)}」还差 ${milestone - n.growth()} 滴水")
                .apply { gravity = Gravity.CENTER }
            ForestUi.add(content, next, 5)
        }

        val canPlantNow = !n.planted() && n.seeds() > 0
        val canExchange = !n.planted() && n.seeds() <= 0 && n.water() >= StudyNotebook.SEED_WATER_COST
        val actionLabel = when {
            n.planted() -> "给小树浇水 · 1水滴"
            canPlantNow -> "种下一颗种子 · 1种子"
            canExchange -> "用 ${StudyNotebook.SEED_WATER_COST} 水滴兑换种子并种下"
            else -> "去专注达标领种子（每日目标 ${n.dailyGoal()} 分钟）"
        }
        val actionBtn = button(ctx(), actionLabel, n.planted() || canPlantNow || canExchange).apply {
            id = R.id.study_water_button
            setOnClickListener {
                val live = store.notebook()
                if (!live.planted() && live.seeds() <= 0) {
                    if (live.water() >= StudyNotebook.SEED_WATER_COST) {
                        perform {
                            store.update { book -> book.exchangeWaterForSeed(); null }
                            care()
                        }
                    } else {
                        open(AppDestination.STUDY_FOCUS)
                    }
                } else {
                    perform { care() }
                }
            }
        }
        waterButton = actionBtn
        ForestUi.add(content, actionBtn, 14)

        val gentleCopy = when {
            n.planted() -> "休息不会枯萎，小树会一直等你。"
            canExchange -> "当前已有 ${n.water()} 滴水，可直接兑换 1 颗种子开始种树。"
            n.seeds() <= 0 -> "达成每日 ${n.dailyGoal()} 分钟目标，或累计满 ${StudyNotebook.SEED_WATER_COST} 滴水即可获得新种子。"
            n.trees() == 0 -> "第一颗种子，送给愿意开始的你。"
            else -> "种下新种子，继续扩大你的小森林。"
        }
        ForestUi.add(content, note(gentleCopy).apply { gravity = Gravity.CENTER }, 10)

        section(content, "一点点，长成了森林", "${n.trees()}棵收藏")
        val collection = row(ctx()).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(ctx(), 0xfffffdfa.toInt(), 0x1e5c4938, 16)
            elevation = dp(1).toFloat()
        }
        for (i in 0 until min(3, n.trees())) {
            val treeLp = LinearLayout.LayoutParams(dp(42), dp(48)).apply { marginEnd = dp(6) }
            collection.addView(image(ctx(), R.drawable.savings_tree_stage_3, "已长成的小树"), treeLp)
        }
        collection.addView(
            text(ctx(), if (n.trees() == 0) "森林的故事，等你写下。" else "每一棵，都有你的时间。", 12, DEEP),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        ForestUi.add(content, collection, 12)

        section(content, "森林的小小约定", "约 1~2 周养成一棵")
        val goal = if (n.currentPlan() == null) "达到任一计划的每日学习目标" else "每天达到 ${n.dailyGoal()} 分钟目标"
        ForestUi.add(
            content,
            note(
                "每累计专注 25 分钟  →  +1 水滴\n跨次累计，短短一段也不会浪费。\n\n" +
                    "$goal  →  +1 种子\n空地无种子时也可用 ${StudyNotebook.SEED_WATER_COST} 滴水兑换 1 颗。\n\n" +
                    "浇满 ${StudyGrowth.REQUIRED_WATER} 次水（约 42 小时，每天 5 小时约 8~9 天）  →  收藏小树\n小树长大后，可以再种下新的期待。"
            ),
            12
        )
    }

    private fun care() {
        val gardenView = garden ?: return
        if (gardenView.busy()) return
        val n = store.notebook()
        val wasPlanted = n.planted()
        val from = n.growth()
        val mature = if (wasPlanted) {
            store.update { it.waterTree() }
        } else {
            store.update { book -> book.plant(); false }
        }
        gardenView.setState(store.notebook().planted(), store.notebook().growth())
        wallet?.text = "水滴 ${store.notebook().water()} · 种子 ${store.notebook().seeds()}"
        growthProgress?.advanceTo(if (wasPlanted) from + 1 else 0)
        waterButton?.isEnabled = false
        waterButton?.text = "小树正在吸收你的照顾…"
        val done = Runnable {
            val currentBody = body ?: return@Runnable
            if (!isAdded) return@Runnable
            ForestMotion.confirm(currentBody)
            render()
            if (mature) {
                val sheet = newSheet()
                val pane = sheetPane("小树长成了", null, R.drawable.ic_study_sprout)
                ForestUi.add(pane.body, heading(ctx(), "第 ${store.notebook().trees()} 棵小树", 28), 4)
                ForestUi.add(pane.body, note("已收进你的小林地。"), 8)
                pane.primary("把这份成长收好") { sheet.dismiss() }
                sheet.push(pane)
            }
        }
        if (wasPlanted) gardenView.water(from, done) else gardenView.plant(done)
    }
}

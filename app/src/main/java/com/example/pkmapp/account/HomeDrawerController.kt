package com.example.pkmapp.account

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.lifecycle.lifecycleScope
import com.example.pkmapp.R
import com.example.pkmapp.core.account.AuthRepository
import com.example.pkmapp.core.sync.SyncRepository
import com.example.pkmapp.forest.ForestDialogBuilder
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.home.HomeFragment
import com.example.pkmapp.life.LifeSharedViewModel
import com.example.pkmapp.navigation.AppDestination
import com.example.pkmapp.profile.AvatarCropSheetController
import com.example.pkmapp.profile.AvatarStore
import com.example.pkmapp.profile.CheckInProgress
import com.example.pkmapp.profile.ProfileIdentityDialogs
import com.google.android.material.checkbox.MaterialCheckBox
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeDrawerController(
    private val activity: AppCompatActivity,
    private val scrim: View,
    private val panel: ScrollView,
    private val content: LinearLayout,
    private val lifeViewModel: LifeSharedViewModel,
    private val avatarController: AvatarCropSheetController,
    private val identityDialogs: ProfileIdentityDialogs,
    private val accountSheets: AccountSheets,
    private val onNavigate: (AppDestination) -> Unit,
    private val onAccountChanged: () -> Unit
) {
    private val interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    private var open = false

    init {
        panel.background = GradientDrawable().apply {
            setColor(0xFFFFFEFA.toInt())
            val r = dp(28).toFloat()
            cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
        panel.clipToPadding = false
        scrim.setOnClickListener { close() }
    }

    fun isOpen(): Boolean = open

    fun applyInsets(bars: Insets) {
        content.setPadding(
            dp(18),
            bars.top + dp(14),
            dp(18),
            bars.bottom + dp(20)
        )
    }

    fun open() {
        render()
        open = true
        scrim.visibility = View.VISIBLE
        panel.visibility = View.VISIBLE
        scrim.animate().cancel()
        panel.animate().cancel()
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(220).start()
        val width = panel.layoutParams.width.takeIf { it > 0 } ?: dp(308)
        panel.translationX = -width.toFloat()
        panel.animate()
            .translationX(0f)
            .setDuration(240)
            .setInterpolator(interpolator)
            .start()
    }

    fun close() {
        if (!open && panel.visibility != View.VISIBLE) return
        open = false
        scrim.animate().cancel()
        panel.animate().cancel()
        val width = panel.width.takeIf { it > 0 } ?: dp(308)
        scrim.animate().alpha(0f).setDuration(180).withEndAction {
            if (!open) scrim.visibility = View.GONE
        }.start()
        panel.animate()
            .translationX(-width.toFloat())
            .setDuration(200)
            .setInterpolator(interpolator)
            .withEndAction {
                if (!open) panel.visibility = View.GONE
            }
            .start()
    }

    fun refreshIfVisible() {
        if (open) render()
    }

    private fun render() {
        content.removeAllViews()
        val appCtx = activity.applicationContext
        val authRepo = AuthRepository(appCtx)
        val account = authRepo.currentAccount()
        val snapshot = lifeViewModel.currentSnapshot
        val checkedDays = snapshot.checkedDates.size
        val level = CheckInProgress.levelForDays(checkedDays)
        val levelName = CheckInProgress.nameForLevel(level)
        val todayKey = ProfileIdentityDialogs.formatDate(Calendar.getInstance())
        val checkedToday = snapshot.checkedDates.contains(todayKey)
        val pendingSync = SyncRepository(appCtx).pendingCount(account.ownerId)

        // 1. Header: Avatar + Nickname + Account Badge + Close
        val headRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        val avatarView = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            val bmp: Bitmap? = AvatarStore.load(appCtx, account.ownerId)
            if (bmp != null) setImageBitmap(bmp) else setImageResource(R.drawable.ic_launcher_character)
            setOnClickListener { avatarController.showAvatarPreview() }
        }
        headRow.addView(avatarView, LinearLayout.LayoutParams(dp(50), dp(50)))

        val identityCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        val nameRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nickView = TextView(activity).apply {
            text = snapshot.nickname
            textSize = 18f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(0xFF315E3A.toInt())
        }
        val pencil = ImageView(activity).apply {
            setImageResource(R.drawable.profile_pencil)
            contentDescription = "修改昵称"
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { identityDialogs.showNicknameEditor() }
        }
        nameRow.addView(nickView)
        nameRow.addView(pencil, LinearLayout.LayoutParams(dp(28), dp(28)))
        identityCol.addView(nameRow)

        val badgeView = TextView(activity).apply {
            text = if (account.isGuest) "本机游客 · 未登录" else account.identifier
            textSize = 11f
            setTextColor(if (account.isGuest) 0xFF7B5C3B.toInt() else 0xFF315E3A.toInt())
            background = ForestUi.rounded(
                activity,
                if (account.isGuest) 0xFFF4EAD9.toInt() else 0xFFE9EEDC.toInt(),
                0,
                999
            )
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        identityCol.addView(
            badgeView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
        )
        headRow.addView(identityCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = TextView(activity).apply {
            text = "✕"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF7B5C3B.toInt())
            background = ForestUi.rounded(activity, 0xFFFAF6EC.toInt(), 0x225C4938, 999)
            setOnClickListener { close() }
        }
        headRow.addView(closeBtn, LinearLayout.LayoutParams(dp(32), dp(32)))
        content.addView(headRow)
        addHairline()

        // 2. Auth / Check-in quick bar
        val authBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ForestUi.rounded(activity, 0xFFFAF6EC.toInt(), 0x1E5C4938, 16)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val levelCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        levelCol.addView(TextView(activity).apply {
            text = "Lv.$level · $levelName"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF315E3A.toInt())
        })
        levelCol.addView(TextView(activity).apply {
            text = "累计打卡 $checkedDays 天 · ${if (checkedToday) "今日已签" else "待签到"}"
            textSize = 11f
            fontFeatureSettings = "tnum"
            setTextColor(0xFF7B5C3B.toInt())
        })
        authBar.addView(levelCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val ctaPill = TextView(activity).apply {
            text = if (account.isGuest) "登录 / 注册" else if (checkedToday) "看日历" else "去打卡"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF315E3A.toInt())
            background = ForestUi.rounded(activity, 0xFFFFFEFA.toInt(), 0x55456444, 999)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                close()
                if (account.isGuest) {
                    (activity as? com.example.pkmapp.MainActivity)?.showAuthSheet(false)
                        ?: accountSheets.showAuthSheet(false)
                } else {
                    identityDialogs.showCheckInDialog()
                }
            }
        }
        authBar.addView(ctaPill)
        content.addView(
            authBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
                bottomMargin = dp(6)
            }
        )

        // 3. Space Navigation
        addSectionLabel("双空间直达")
        addMenuRow("生活小屋", rightText = "›") {
            close()
            onNavigate(AppDestination.DETAILS)
        }
        addHairline()
        addMenuRow("学习小屋", rightText = "›") {
            close()
            onNavigate(AppDestination.STUDY_FOCUS)
        }

        // 4. Personal & Sync Services
        addSectionLabel("个人与设置")
        addMenuRow("打卡日历与等级", rightText = "$checkedDays 天 ›") {
            close()
            identityDialogs.showCheckInDialog()
        }
        addHairline()
        addMenuRow("意见反馈中心", rightText = "›", highlight = true) {
            close()
            onNavigate(AppDestination.FEEDBACK)
        }
        addHairline()
        addMenuRow(
            "数据备份与云同步",
            rightText = if (pendingSync > 0) "待同步 $pendingSync ›" else "›"
        ) {
            close()
            accountSheets.showBackupAndSyncSheet()
        }
        addHairline()

        val rememberEnabled = HomeFragment.isRememberLastCottage(activity)
        val rememberCheck = ForestUi.flatSwitchRow(
            activity,
            "记住上次进入的小屋",
            null
        ).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(10))
            minHeight = dp(48)
            minimumHeight = dp(48)
            isChecked = rememberEnabled
            setOnCheckedChangeListener { _, isChecked ->
                HomeFragment.setRememberLastCottage(activity, isChecked)
            }
        }
        content.addView(
            rememberCheck,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        addHairline()

        addMenuRow(
            "切换或管理本机账号",
            rightText = if (account.isGuest) "游客 ›" else "已登录 ›"
        ) {
            close()
            accountSheets.showAccountSwitcherSheet()
        }

        // 5. Footer
        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(22), 0, dp(6))
        }
        footer.addView(
            TextView(activity).apply {
                text = "小松果 v4.0"
                textSize = 11f
                setTextColor(0xFF847462.toInt())
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        if (account.isGuest) {
            val clearGuest = TextView(activity).apply {
                text = "清空游客数据"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF9E4C37.toInt())
                setPadding(dp(6), dp(6), 0, dp(6))
                setOnClickListener {
                    close()
                    identityDialogs.confirmClearData()
                }
            }
            footer.addView(clearGuest)
        } else {
            val logout = TextView(activity).apply {
                text = "退出登录"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF7B5C3B.toInt())
                setPadding(dp(8), dp(6), dp(10), dp(6))
                setOnClickListener {
                    activity.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            AuthRepository(appCtx).logoutToGuest()
                        }
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            close()
                            val main = activity as? com.example.pkmapp.MainActivity
                            if (main != null) {
                                main.logoutAndOpenAuth()
                            } else {
                                onAccountChanged()
                                Toast.makeText(activity, "已切回本机游客模式", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            val deleteAcc = TextView(activity).apply {
                text = "注销账号"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF9E4C37.toInt())
                setPadding(dp(4), dp(6), 0, dp(6))
                setOnClickListener {
                    ForestDialogBuilder(activity)
                        .setTitle("注销当前账号？")
                        .setMessage("将删除账号「${account.nickname}」的本机（及已连接云端）所有账本、学习记录与反馈，无法撤销。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确认注销") { _, _ ->
                            activity.lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    AuthRepository(appCtx).deleteCurrentAccount()
                                }
                                if (!activity.isFinishing && !activity.isDestroyed) {
                                    close()
                                    onAccountChanged()
                                    Toast.makeText(activity, "账号已注销并回到游客空间", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .show()
                }
            }
            footer.addView(logout)
            footer.addView(deleteAcc)
        }
        content.addView(footer)
    }

    private fun addSectionLabel(label: String) {
        content.addView(
            TextView(activity).apply {
                text = label
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF847462.toInt())
                setPadding(0, dp(14), 0, dp(4))
            }
        )
    }

    private fun addMenuRow(
        title: String,
        subtitle: String? = null,
        rightText: String,
        highlight: Boolean = false,
        onClick: () -> Unit
    ) {
        val hasSubtitle = !subtitle.isNullOrBlank()
        val vPad = if (hasSubtitle) dp(11) else dp(14)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, vPad, 0, vPad)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val left = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(activity).apply {
            text = title
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (highlight) 0xFF315E3A.toInt() else 0xFF243624.toInt())
        })
        if (hasSubtitle) {
            left.addView(TextView(activity).apply {
                text = subtitle
                textSize = 11f
                setTextColor(0xFF7B5C3B.toInt())
            })
        }
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(activity).apply {
            text = rightText
            textSize = 12.5f
            fontFeatureSettings = "tnum"
            setTextColor(if (highlight) 0xFF315E3A.toInt() else 0xFF7B5C3B.toInt())
        })
        content.addView(row)
    }

    private fun addHairline() {
        val line = View(activity).apply { setBackgroundColor(0x1E5C4938) }
        content.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun dp(value: Int): Int =
        Math.round(value * activity.resources.displayMetrics.density)
}

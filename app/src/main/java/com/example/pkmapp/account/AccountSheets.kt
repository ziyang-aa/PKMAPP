package com.example.pkmapp.account

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.pkmapp.R
import com.example.pkmapp.core.account.AuthRepository
import com.example.pkmapp.core.account.AuthResult
import com.example.pkmapp.core.account.GuestMergeSummary
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.LocalAccountEntity
import com.example.pkmapp.core.security.CryptoSessionStore
import com.example.pkmapp.core.sync.SyncOutcome
import com.example.pkmapp.core.sync.SyncRepository
import com.example.pkmapp.forest.ForestDialogBuilder
import com.example.pkmapp.forest.ForestUi
import com.example.pkmapp.profile.AvatarStore
import com.example.pkmapp.profile.BackupCoordinator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountSheets(
    private val contextProvider: () -> Context,
    private val scopeProvider: () -> LifecycleCoroutineScope,
    private val isAlive: () -> Boolean,
    private val backupCoordinator: BackupCoordinator,
    private val onAccountChanged: () -> Unit
) {
    private var activeSheet: BottomSheetDialog? = null

    fun dismissActiveSheet() {
        activeSheet?.dismiss()
        activeSheet = null
    }

    fun showAuthSheet(initialRegister: Boolean = false) {
        val context = contextProvider()
        val sheet = createSheet(context)
        var isRegister = initialRegister

        val root = sheetRoot(context)
        val header = addSheetHeader(root, sheet, if (isRegister) "创建小松果账号" else "登录小松果")
        val titleView = header

        val tabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 4), 0, 0)
        }
        val loginTab = tabButton(context, "密码登录")
        val registerTab = tabButton(context, "注册账号")
        tabsRow.addView(loginTab, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 40)))
        tabsRow.addView(
            registerTab,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 40)).apply {
                marginStart = dp(context, 22)
            }
        )
        root.addView(tabsRow)
        addHairline(root)

        val scroll = ScrollView(context)
        val form = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(form)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val identifierInput = underlineField(
            context,
            form,
            "手机号或邮箱",
            "例如 13800138000 或 name@example.com",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val nicknameContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        form.addView(nicknameContainer)
        val nicknameInput = underlineField(
            context,
            nicknameContainer,
            "昵称",
            "可留空自动生成",
            InputType.TYPE_CLASS_TEXT
        )
        val passwordInput = underlineField(
            context,
            form,
            "密码（至少 8 位）",
            "请输入至少 8 位密码",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )

        val hintText = TextView(context).apply {
            text = "支持离线使用，配置服务器地址后可同步数据"
            textSize = 11.5f
            setTextColor(0xFF7B5C3B.toInt())
            setPadding(0, dp(context, 12), 0, 0)
        }
        form.addView(hintText)

        val errorText = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF9E4C37.toInt())
            visibility = View.GONE
            setPadding(0, dp(context, 8), 0, 0)
        }
        form.addView(errorText)

        val submitBtn = primaryButton(context, "")
        form.addView(
            submitBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)).apply {
                topMargin = dp(context, 16)
            }
        )

        fun syncTabState() {
            titleView.text = if (isRegister) "创建小松果账号" else "登录小松果"
            styleTab(loginTab, !isRegister)
            styleTab(registerTab, isRegister)
            nicknameContainer.visibility = if (isRegister) View.VISIBLE else View.GONE
            submitBtn.text = if (isRegister) "注册并进入森林" else "登录"
            errorText.visibility = View.GONE
        }

        loginTab.setOnClickListener {
            isRegister = false
            syncTabState()
        }
        registerTab.setOnClickListener {
            isRegister = true
            syncTabState()
        }
        syncTabState()

        submitBtn.setOnClickListener {
            val idVal = identifierInput.text?.toString().orEmpty()
            val passVal = passwordInput.text?.toString().orEmpty()
            val nickVal = nicknameInput.text?.toString().orEmpty()
            submitBtn.isEnabled = false
            errorText.visibility = View.GONE
            val appCtx = context.applicationContext
            scopeProvider().launch {
                val res = withContext(Dispatchers.IO) {
                    val repo = AuthRepository(appCtx)
                    if (isRegister) repo.register(idVal, passVal, nickVal) else repo.login(idVal, passVal)
                }
                if (!isAlive()) return@launch
                submitBtn.isEnabled = true
                when (res) {
                    is AuthResult.Error -> {
                        errorText.text = res.message
                        errorText.visibility = View.VISIBLE
                    }
                    is AuthResult.Success -> {
                        sheet.dismiss()
                        onAccountChanged()
                        val merge = res.guestMergeSummary
                        if (merge != null) {
                            showGuestMergeSheet(res.account, merge)
                        } else {
                            Toast.makeText(
                                contextProvider(),
                                "欢迎回来，${res.account.nickname}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        presentSheet(sheet, root)
    }

    fun showGuestMergeSheet(account: LocalAccountEntity, summary: GuestMergeSummary) {
        val context = contextProvider()
        val sheet = createSheet(context)
        val root = sheetRoot(context)
        addSheetHeader(root, sheet, "发现本机游客数据")
        addHairline(root)

        val parts = ArrayList<String>()
        if (summary.ledgerCount > 1 || summary.transactionCount > 0) {
            parts.add("${summary.ledgerCount} 本账本、${summary.transactionCount} 笔收支流水")
        }
        if (summary.savingsCount > 0) parts.add("${summary.savingsCount} 个攒钱目标")
        if (summary.borrowingCount > 0) parts.add("${summary.borrowingCount} 条借还记录")
        if (summary.studyRecordCount > 0) parts.add("${summary.studyRecordCount} 条学习记录")
        if (summary.todoCount > 0) parts.add("${summary.todoCount} 项待办")
        if (summary.checkedDateCount > 0) parts.add("${summary.checkedDateCount} 天打卡足迹")

        val body = TextView(context).apply {
            text = "你在本机游客空间里记录了：\n• ${parts.joinToString("\n• ")}\n\n是否将这些游客数据一键合并归属到「${account.nickname}」？"
            textSize = 13.5f
            setTextColor(0xFF243624.toInt())
            setLineSpacing(dp(context, 4).toFloat(), 1f)
            setPadding(0, dp(context, 12), 0, dp(context, 16))
        }
        root.addView(body)

        val mergeBtn = primaryButton(context, "合并到当前账号（零丢数）")
        mergeBtn.setOnClickListener {
            mergeBtn.isEnabled = false
            val appCtx = context.applicationContext
            scopeProvider().launch {
                withContext(Dispatchers.IO) {
                    AuthRepository(appCtx).mergeGuestDataTo(account.ownerId)
                }
                if (!isAlive()) return@launch
                sheet.dismiss()
                onAccountChanged()
                Toast.makeText(contextProvider(), "游客数据已合并到当前账号", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(mergeBtn)

        val skipBtn = outlineButton(context, "暂不合并，分开保存")
        skipBtn.setOnClickListener { sheet.dismiss() }
        root.addView(
            skipBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 44)).apply {
                topMargin = dp(context, 10)
            }
        )
        presentSheet(sheet, root)
    }

    fun showAccountSwitcherSheet() {
        val context = contextProvider()
        val sheet = createSheet(context)
        val root = sheetRoot(context)
        addSheetHeader(root, sheet, "切换本机账号")
        addHairline(root)

        val hint = TextView(context).apply {
            text = "各账号的数据独立保存，切换后立即生效"
            textSize = 11.5f
            setTextColor(0xFF7B5C3B.toInt())
            setPadding(0, dp(context, 8), 0, dp(context, 8))
        }
        root.addView(hint)

        val appCtx = context.applicationContext
        val activeId = AccountScope.currentOwnerId(appCtx)
        val accounts = AuthRepository(appCtx).listAccounts()

        val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (acc in accounts) {
            val isCurrent = acc.ownerId == activeId
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 10))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (isCurrent) {
                        sheet.dismiss()
                        return@setOnClickListener
                    }
                    scopeProvider().launch {
                        withContext(Dispatchers.IO) {
                            AuthRepository(appCtx).switchLocalAccount(acc.ownerId)
                        }
                        if (!isAlive()) return@launch
                        sheet.dismiss()
                        onAccountChanged()
                        Toast.makeText(contextProvider(), "已切换至：${acc.nickname}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val avatar = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                val bmp = AvatarStore.load(appCtx, acc.ownerId)
                if (bmp != null) setImageBitmap(bmp) else setImageResource(R.drawable.ic_launcher_character)
            }
            row.addView(avatar, LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)))

            val meta = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 12), 0, dp(context, 8), 0)
            }
            val nameView = TextView(context).apply {
                text = acc.nickname + if (isCurrent) " · 当前" else ""
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF243624.toInt())
            }
            val idView = TextView(context).apply {
                text = if (acc.isGuest) "本机游客空间" else acc.identifier
                textSize = 11.5f
                setTextColor(0xFF7B5C3B.toInt())
            }
            meta.addView(nameView)
            meta.addView(idView)
            row.addView(meta, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val status = TextView(context).apply {
                text = if (isCurrent) "✓" else "切换"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isCurrent) 0xFF315E3A.toInt() else 0xFF7B5C3B.toInt())
            }
            row.addView(status)
            listContainer.addView(row)
            addHairline(listContainer)
        }
        val scroll = ScrollView(context)
        scroll.addView(listContainer)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val addBtn = primaryButton(context, "＋ 登录或注册新账号")
        addBtn.setOnClickListener {
            sheet.dismiss()
            val main = context as? com.example.pkmapp.MainActivity
            if (main != null) {
                main.showAuthSheet(false)
            } else {
                showAuthSheet(initialRegister = false)
            }
        }
        root.addView(
            addBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)).apply {
                topMargin = dp(context, 14)
            }
        )
        presentSheet(sheet, root)
    }

    fun showBackupAndSyncSheet() {
        val context = contextProvider()
        val appCtx = context.applicationContext
        val sheet = createSheet(context)
        val root = sheetRoot(context)
        addSheetHeader(root, sheet, "数据备份与云同步")
        addHairline(root)

        val sessionStore = CryptoSessionStore.get(appCtx)
        val syncRepo = SyncRepository(appCtx)
        val pendingCount = syncRepo.pendingCount()

        fun actionRow(title: String, subtitle: String?, right: String, onClick: () -> Unit) {
            val hasSubtitle = !subtitle.isNullOrBlank()
            val vPad = if (hasSubtitle) dp(context, 12) else dp(context, 14)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, vPad, 0, vPad)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
            val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            left.addView(TextView(context).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF243624.toInt())
            })
            if (hasSubtitle) {
                left.addView(TextView(context).apply {
                    text = subtitle
                    textSize = 11.5f
                    setTextColor(0xFF7B5C3B.toInt())
                })
            }
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(context).apply {
                text = right
                textSize = 12.5f
                setTextColor(0xFF315E3A.toInt())
                setTypeface(null, Typeface.BOLD)
            })
            root.addView(row)
            addHairline(root)
        }

        actionRow("导出 JSON 备份", null, "导出 ›") {
            sheet.dismiss()
            backupCoordinator.startExport()
        }
        actionRow("导入 JSON 恢复", "支持合并去重或覆盖", "导入 ›") {
            sheet.dismiss()
            backupCoordinator.startImport()
        }

        val urlInput = underlineField(
            context,
            root,
            "云同步服务器地址（选填）",
            "例如 http://127.0.0.1:8080（留空仅离线使用）",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
        urlInput.setText(sessionStore.currentServerBaseUrl())

        val statusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 12), 0, dp(context, 4))
        }
        val syncSpinner = com.example.pkmapp.forest.ForestLoadingView(context).apply {
            setMode(com.example.pkmapp.forest.ForestLoadingView.Mode.LEAF_ORBIT)
            visibility = View.GONE
        }
        statusRow.addView(
            syncSpinner,
            LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
                marginEnd = dp(context, 8)
            }
        )
        val statusLine = TextView(context).apply {
            text = "本账号待同步变更：$pendingCount 条"
            textSize = 12f
            fontFeatureSettings = "tnum"
            setTextColor(0xFF7B5C3B.toInt())
        }
        statusRow.addView(statusLine)
        root.addView(statusRow)

        val syncBtn = primaryButton(context, "保存地址并检查同步")
        syncBtn.setOnClickListener {
            val entered = urlInput.text?.toString().orEmpty()
            sessionStore.saveServerBaseUrl(entered)
            urlInput.setText(sessionStore.currentServerBaseUrl())
            val latestSession = sessionStore.currentSession()
            if (!latestSession.isGuest && latestSession.serverBaseUrl.isNotEmpty() &&
                (latestSession.accessToken.isBlank() || latestSession.accessToken.startsWith("offline_session_"))
            ) {
                promptUpgradeOfflineSessionAndSync(
                    context = context,
                    appCtx = appCtx,
                    statusLine = statusLine,
                    syncSpinner = syncSpinner,
                    syncBtn = syncBtn
                )
                return@setOnClickListener
            }
            runCloudSyncNow(appCtx, statusLine, syncSpinner, syncBtn)
        }
        root.addView(
            syncBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)).apply {
                topMargin = dp(context, 12)
            }
        )

        presentSheet(sheet, root)
    }

    private fun runCloudSyncNow(
        appCtx: Context,
        statusLine: TextView,
        syncSpinner: View,
        syncBtn: View
    ) {
        syncBtn.isEnabled = false
        syncSpinner.visibility = View.VISIBLE
        statusLine.text = "正在连接服务器同步…"
        scopeProvider().launch {
            val outcome = withContext(Dispatchers.IO) { SyncRepository(appCtx).syncNow() }
            if (!isAlive()) return@launch
            syncBtn.isEnabled = true
            syncSpinner.visibility = View.GONE
            val msg = when (outcome) {
                is SyncOutcome.Synced -> "已同步 ${outcome.pushedCount} 条变更至服务器"
                is SyncOutcome.LocalOnly -> outcome.reason
                is SyncOutcome.Failed -> outcome.message
            }
            statusLine.text = msg
            onAccountChanged()
            Toast.makeText(contextProvider(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptUpgradeOfflineSessionAndSync(
        context: Context,
        appCtx: Context,
        statusLine: TextView,
        syncSpinner: View,
        syncBtn: View
    ) {
        val currentAcc = AuthRepository(appCtx).currentAccount()
        val input = EditText(context).apply {
            hint = "输入「${currentAcc.identifier}」的登录密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFF243624.toInt())
            setHintTextColor(0xFF9A8B7A.toInt())
            setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 10))
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 4))
            addView(TextView(context).apply {
                text = "该账号之前为离线登录，验证一次密码即可绑定云端并立即同步数据。"
                textSize = 12.5f
                setTextColor(0xFF7B5C3B.toInt())
                setPadding(0, 0, 0, dp(context, 10))
            })
            addView(input)
        }
        ForestDialogBuilder(context)
            .setTitle("验证密码并开启云同步")
            .setView(box)
            .setNegativeButton("取消", null)
            .setPositiveButton("验证并同步") { _, _ ->
                val pwd = input.text?.toString().orEmpty()
                if (pwd.length < 8) {
                    Toast.makeText(contextProvider(), "请输入至少 8 位密码", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                syncBtn.isEnabled = false
                syncSpinner.visibility = View.VISIBLE
                statusLine.text = "正在登录云服务器…"
                scopeProvider().launch {
                    val result = withContext(Dispatchers.IO) {
                        AuthRepository(appCtx).login(currentAcc.identifier, pwd, reloadRuntimeStores = false)
                    }
                    if (!isAlive()) return@launch
                    when (result) {
                        is AuthResult.Error -> {
                            syncBtn.isEnabled = true
                            syncSpinner.visibility = View.GONE
                            statusLine.text = result.message
                            Toast.makeText(contextProvider(), result.message, Toast.LENGTH_SHORT).show()
                        }
                        is AuthResult.Success -> {
                            val refreshed = CryptoSessionStore.get(appCtx).currentSession()
                            if (refreshed.accessToken.isBlank() ||
                                refreshed.accessToken.startsWith("offline_session_")
                            ) {
                                syncBtn.isEnabled = true
                                syncSpinner.visibility = View.GONE
                                val failMsg = "无法连接服务器（${refreshed.serverBaseUrl}），请确认后端服务已启动且地址正确"
                                statusLine.text = failMsg
                                Toast.makeText(contextProvider(), failMsg, Toast.LENGTH_LONG).show()
                            } else {
                                runCloudSyncNow(appCtx, statusLine, syncSpinner, syncBtn)
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun createSheet(context: Context): BottomSheetDialog {
        dismissActiveSheet()
        return BottomSheetDialog(context, R.style.ThemeOverlay_PkmAPP_StudySheet).apply {
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                )
            }
            setOnDismissListener {
                if (activeSheet === this) activeSheet = null
            }
            activeSheet = this
        }
    }

    private fun presentSheet(dialog: BottomSheetDialog, content: View) {
        val context = content.context
        val topRadius = dp(context, 24).toFloat()
        val sheetBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0xFFFFFEFA.toInt())
            cornerRadii = floatArrayOf(topRadius, topRadius, topRadius, topRadius, 0f, 0f, 0f, 0f)
        }
        val scrollHost = NestedScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = sheetBg
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(scrollHost) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottomPad = maxOf(imeBottom, navBottom)
            if (view.paddingBottom != bottomPad) {
                view.setPadding(0, 0, 0, bottomPad)
            }
            if (imeBottom > 0) {
                view.post {
                    val focused = dialog.currentFocus
                    if (focused != null) {
                        val rect = android.graphics.Rect(0, 0, focused.width, focused.height + dp(context, 24))
                        focused.requestRectangleOnScreen(rect, false)
                    }
                }
            }
            insets
        }
        dialog.setContentView(scrollHost)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            if (sheet != null) {
                sheet.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            ViewCompat.requestApplyInsets(scrollHost)
        }
        dialog.show()
    }

    private fun sheetRoot(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(context, 20), dp(context, 10), dp(context, 20), dp(context, 24))
            val handle = View(context).apply {
                background = ForestUi.rounded(context, 0x335C4938, 0, 999)
            }
            addView(
                handle,
                LinearLayout.LayoutParams(dp(context, 38), dp(context, 4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(context, 10)
                }
            )
        }

    private fun addSheetHeader(root: LinearLayout, dialog: BottomSheetDialog, title: String): TextView {
        val row = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(root.context, 2), 0, dp(root.context, 10))
        }
        val titleView = TextView(root.context).apply {
            text = title
            textSize = 20f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(0xFF315E3A.toInt())
        }
        val close = TextView(root.context).apply {
            text = "✕"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF7B5C3B.toInt())
            background = ForestUi.rounded(root.context, 0xFFFAF6EC.toInt(), 0x225C4938, 999)
            setOnClickListener { dialog.dismiss() }
        }
        row.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(close, LinearLayout.LayoutParams(dp(root.context, 32), dp(root.context, 32)))
        root.addView(row)
        return titleView
    }

    private fun underlineField(
        context: Context,
        parent: LinearLayout,
        label: String,
        hintText: String,
        inputTypeFlags: Int
    ): EditText {
        val labelView = TextView(context).apply {
            text = label
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF7B5C3B.toInt())
            setPadding(0, dp(context, 12), 0, dp(context, 2))
        }
        val line = View(context).apply { setBackgroundColor(0x385C4938) }
        val input = EditText(context).apply {
            background = null
            hint = hintText
            inputType = inputTypeFlags
            textSize = 15f
            setTextColor(0xFF243624.toInt())
            setHintTextColor(0xFFB4A693.toInt())
            setPadding(0, dp(context, 6), 0, dp(context, 8))
            setOnFocusChangeListener { _, hasFocus ->
                line.setBackgroundColor(if (hasFocus) 0xFF456444.toInt() else 0x385C4938)
            }
        }
        parent.addView(labelView)
        parent.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)))
        return input
    }

    private fun tabButton(context: Context, label: String): TextView =
        TextView(context).apply {
            text = label
            textSize = 14.5f
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
        }

    private fun styleTab(view: TextView, active: Boolean) {
        view.setTextColor(if (active) 0xFF315E3A.toInt() else 0xFF847462.toInt())
        view.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        if (active) {
            val line = GradientDrawable().apply {
                setColor(0xFF456444.toInt())
            }
            val layers = android.graphics.drawable.LayerDrawable(arrayOf(line))
            val thickness = dp(view.context, 2)
            layers.setLayerGravity(0, Gravity.BOTTOM)
            layers.setLayerHeight(0, thickness)
            view.background = layers
        } else {
            view.background = null
        }
    }

    private fun primaryButton(context: Context, text: String): MaterialButton =
        MaterialButton(context).apply {
            this.text = text
            textSize = 14.5f
            setTextColor(0xFFFFFEFA.toInt())
            backgroundTintList = ColorStateList.valueOf(0xFF456444.toInt())
            cornerRadius = dp(context, 12)
            isAllCaps = false
            minHeight = dp(context, 48)
        }

    private fun outlineButton(context: Context, text: String): MaterialButton =
        MaterialButton(context).apply {
            this.text = text
            textSize = 13.5f
            setTextColor(0xFF315E3A.toInt())
            backgroundTintList = ColorStateList.valueOf(0xFFFFFEFA.toInt())
            strokeColor = ColorStateList.valueOf(0x55456444)
            strokeWidth = dp(context, 1)
            cornerRadius = dp(context, 12)
            isAllCaps = false
            minHeight = dp(context, 44)
        }

    private fun addHairline(parent: LinearLayout) {
        val v = View(parent.context).apply { setBackgroundColor(0x1E5C4938) }
        parent.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(parent.context, 1)))
    }

    private fun dp(context: Context, value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)
}

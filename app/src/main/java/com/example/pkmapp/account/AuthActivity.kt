package com.example.pkmapp.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.pkmapp.MainActivity
import com.example.pkmapp.core.account.AuthRepository
import com.example.pkmapp.core.account.AuthResult
import com.example.pkmapp.core.database.LocalAccountEntity
import com.example.pkmapp.core.database.PkmStorageBootstrap
import com.example.pkmapp.databinding.ActivityAuthBinding
import com.example.pkmapp.forest.ForestDialogBuilder
import com.example.pkmapp.forest.ForestMotion
import com.example.pkmapp.forest.SquirrelMascotView
import com.example.pkmapp.forest.SquirrelSpeechBubble
import com.example.pkmapp.profile.AvatarStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen Chinese authentication flow with an animated waving squirrel mascot, contextual
 * speech-bubble prompts for wrong password / welcome, separated floating-label inputs, and a
 * botanical loading animation for verification/network calls.
 */
class AuthActivity : AppCompatActivity() {

    enum class Step {
        PICK,
        CHOICE,
        LOGIN,
        REGISTER
    }

    private lateinit var binding: ActivityAuthBinding
    private var currentStep: Step = Step.CHOICE
    private var returnToCaller: Boolean = false
    private var managingAccounts: Boolean = false
    private var submitting: Boolean = false
    private var savedNonGuestAccounts: List<LocalAccountEntity> = emptyList()

    private val tapGreetings = arrayOf(
        "嗨～今天也要开心呀！",
        "小松果随时陪着你～",
        "招招手，一起出发吧！"
    )
    private var tapIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        PkmStorageBootstrap.ensureInitialized(applicationContext)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        returnToCaller = intent.getBooleanExtra(EXTRA_RETURN_TO_CALLER, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.authRoot) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime))
            insets
        }

        bindAgreementText()
        bindMascotInteractions()
        bindInteractions()
        bindBackPress()

        loadSavedAccounts()
        val requested = intent.getStringExtra(EXTRA_INITIAL_STEP)
        val initialStep = when (requested) {
            STEP_PICK -> if (savedNonGuestAccounts.isNotEmpty()) Step.PICK else Step.CHOICE
            STEP_CHOICE -> Step.CHOICE
            STEP_LOGIN -> Step.LOGIN
            STEP_REGISTER -> Step.REGISTER
            else -> if (savedNonGuestAccounts.isNotEmpty()) Step.PICK else Step.CHOICE
        }
        showStep(initialStep)
    }

    private fun loadSavedAccounts() {
        savedNonGuestAccounts = AuthRepository(applicationContext)
            .listAccounts()
            .filter { !it.isGuest }
    }

    private fun bindMascotInteractions() {
        binding.authChoiceMascot.setOnMascotTapListener {
            binding.authChoiceBubble.showMessage(nextTapGreeting(), SquirrelSpeechBubble.Tone.GREETING)
        }
        binding.authPickMascot.setOnMascotTapListener {
            binding.authPickBubble.showMessage(nextTapGreeting(), SquirrelSpeechBubble.Tone.GREETING)
        }
        binding.authFormMascot.setOnMascotTapListener {
            if (!submitting) {
                binding.authFormBubble.showMessage(nextTapGreeting(), SquirrelSpeechBubble.Tone.GREETING)
            }
        }
    }

    private fun nextTapGreeting(): String {
        val text = tapGreetings[tapIndex % tapGreetings.size]
        tapIndex++
        return text
    }

    private fun bindInteractions() {
        binding.authPickBack.setOnClickListener { handleBackNavigation() }
        binding.authAddAnotherAccount.setOnClickListener {
            managingAccounts = false
            showStep(Step.CHOICE)
        }
        binding.authManageAccountsButton.setOnClickListener {
            managingAccounts = !managingAccounts
            renderAccountPicker()
        }
        binding.authPickGuestButton.setOnClickListener { continueAsGuest() }

        binding.authChoiceBack.setOnClickListener { handleBackNavigation() }
        binding.authGoLoginButton.setOnClickListener { showStep(Step.LOGIN) }
        binding.authGoRegisterButton.setOnClickListener { showStep(Step.REGISTER) }
        binding.authChoiceGuestButton.setOnClickListener { continueAsGuest() }

        binding.authFormBack.setOnClickListener { handleBackNavigation() }
        binding.authFormGuestButton.setOnClickListener { continueAsGuest() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (binding.authFormMascot.currentMood() == SquirrelMascotView.Mood.ERROR) {
                    binding.authFormMascot.showIdle()
                    showFormDefaultBubble(animated = false)
                }
                syncSubmitButtonState()
            }
        }
        binding.authIdentifierInput.addTextChangedListener(watcher)
        binding.authPasswordInput.addTextChangedListener(watcher)
        binding.authPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && binding.authSubmitButton.isEnabled) {
                submitCredentials()
                true
            } else {
                false
            }
        }

        binding.authSubmitButton.setOnClickListener { submitCredentials() }
        binding.authForgotOrSwitch.setOnClickListener {
            if (currentStep == Step.LOGIN) {
                showForgotPasswordDialog()
            } else {
                showStep(Step.LOGIN)
            }
        }
        binding.authAgreementText.setOnClickListener { showAgreementDialog() }
    }

    private fun bindBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (submitting) return
                handleBackNavigation()
            }
        })
    }

    private fun handleBackNavigation() {
        when (currentStep) {
            Step.LOGIN, Step.REGISTER -> {
                val requested = intent.getStringExtra(EXTRA_INITIAL_STEP)
                if (returnToCaller && (requested == STEP_LOGIN || requested == STEP_REGISTER)) {
                    finish()
                } else {
                    showStep(Step.CHOICE)
                }
            }
            Step.CHOICE -> {
                if (savedNonGuestAccounts.isNotEmpty()) {
                    showStep(Step.PICK)
                } else if (returnToCaller) {
                    finish()
                } else {
                    moveTaskToBack(true)
                }
            }
            Step.PICK -> {
                if (returnToCaller) {
                    finish()
                } else {
                    moveTaskToBack(true)
                }
            }
        }
    }

    private fun showStep(step: Step) {
        currentStep = step
        binding.authStepPick.visibility = if (step == Step.PICK) View.VISIBLE else View.GONE
        binding.authStepChoice.visibility = if (step == Step.CHOICE) View.VISIBLE else View.GONE
        binding.authStepForm.visibility = if (step == Step.LOGIN || step == Step.REGISTER) View.VISIBLE else View.GONE

        when (step) {
            Step.PICK -> {
                binding.authPickBack.visibility = if (returnToCaller) View.VISIBLE else View.INVISIBLE
                binding.authPickBubble.hideBubble()
                binding.authPickMascot.playWave(scheduleNext = true)
                renderAccountPicker()
            }
            Step.CHOICE -> {
                val canGoBack = savedNonGuestAccounts.isNotEmpty() || returnToCaller
                binding.authChoiceBack.visibility = if (canGoBack) View.VISIBLE else View.INVISIBLE
                binding.authChoiceBubble.showMessage("嗨～我是小松果！", SquirrelSpeechBubble.Tone.GREETING)
                binding.authChoiceMascot.playWave(scheduleNext = true)
            }
            Step.LOGIN -> {
                binding.authFormTitle.text = "输入账号信息"
                binding.authNicknameLayout.visibility = View.GONE
                binding.authPasswordLayout.hint = "密码"
                binding.authSubmitButton.text = "登录"
                binding.authForgotOrSwitch.text = "忘记密码"
                binding.authFormMascot.playWave(scheduleNext = true)
                showFormDefaultBubble(animated = true)
                syncSubmitButtonState()
            }
            Step.REGISTER -> {
                binding.authFormTitle.text = "创建小松果账号"
                binding.authNicknameLayout.visibility = View.VISIBLE
                binding.authPasswordLayout.hint = "密码（至少 8 位）"
                binding.authSubmitButton.text = "注册并进入"
                binding.authForgotOrSwitch.text = "已有账号？去登录"
                binding.authFormMascot.playWave(scheduleNext = true)
                showFormDefaultBubble(animated = true)
                syncSubmitButtonState()
            }
        }
    }

    private fun showFormDefaultBubble(animated: Boolean) {
        val greeting = if (currentStep == Step.REGISTER) {
            "新朋友你好！填好就能出发啦"
        } else {
            "嗨～填好账号密码就能进森林啦"
        }
        binding.authFormBubble.showMessage(greeting, SquirrelSpeechBubble.Tone.GREETING, animated)
    }

    private fun renderAccountPicker() {
        val list = binding.authAccountList
        list.removeAllViews()
        binding.authManageAccountsButton.text = if (managingAccounts) "完成管理" else "管理本机账号"

        savedNonGuestAccounts.forEachIndexed { index, account ->
            if (index > 0) {
                val divider = View(this).apply { setBackgroundColor(0xFFEBE4D8.toInt()) }
                list.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(14), dp(18), dp(14))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (managingAccounts) {
                        confirmDeleteSavedAccount(account)
                    } else {
                        quickSignIn(account)
                    }
                }
            }

            val avatarBmp: Bitmap? = AvatarStore.load(applicationContext, account.ownerId)
            if (avatarBmp != null) {
                val avatarImg = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setOval(0, 0, view.width, view.height)
                        }
                    }
                    setImageBitmap(avatarBmp)
                }
                row.addView(avatarImg, LinearLayout.LayoutParams(dp(40), dp(40)))
            } else {
                val initial = account.nickname.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "松"
                val circle = TextView(this).apply {
                    text = initial
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(0xFFFFFEFA.toInt())
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0xFF456444.toInt())
                    }
                }
                row.addView(circle, LinearLayout.LayoutParams(dp(40), dp(40)))
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
            }
            infoCol.addView(TextView(this).apply {
                text = account.nickname
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF243624.toInt())
                maxLines = 1
            })
            infoCol.addView(TextView(this).apply {
                text = account.identifier
                textSize = 12.5f
                setTextColor(0xFF8E8475.toInt())
                maxLines = 1
            })
            row.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (managingAccounts) {
                val removeTag = TextView(this).apply {
                    text = "移除"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(0xFF9E4C37.toInt())
                    setPadding(dp(10), dp(6), dp(4), dp(6))
                }
                row.addView(removeTag)
            } else {
                val chevron = TextView(this).apply {
                    text = "›"
                    textSize = 22f
                    setTextColor(0xFFA79D8E.toInt())
                }
                row.addView(chevron)
            }
            list.addView(row)
        }
    }

    private fun quickSignIn(account: LocalAccountEntity) {
        if (submitting) return
        val sessionStore = com.example.pkmapp.core.security.CryptoSessionStore.get(applicationContext)
        if (sessionStore.currentServerBaseUrl().isNotEmpty()) {
            // Cloud server configured: prompt for password to obtain a real cloud JWT instead of offline_session.
            showStep(Step.LOGIN)
            binding.authIdentifierInput.setText(account.identifier)
            binding.authPasswordInput.setText("")
            binding.authPasswordInput.requestFocus()
            binding.authFormBubble.showMessage(
                "已连接云服务器，输入密码即可同步上云～",
                SquirrelSpeechBubble.Tone.GREETING
            )
            syncSubmitButtonState()
            return
        }
        submitting = true
        showLoadingOverlay("正在登录…")
        binding.authPickMascot.playLoading()
        val appCtx = applicationContext
        lifecycleScope.launch {
            val startAt = System.currentTimeMillis()
            val shouldPromptMerge = withContext(Dispatchers.IO) {
                val repo = AuthRepository(appCtx)
                val wasGuest = repo.currentAccount().isGuest
                val mergeSummary = if (wasGuest) repo.inspectGuestMergeCandidate() else null
                repo.switchLocalAccount(account.ownerId)
                mergeSummary?.hasMergeableData == true
            }
            awaitMinimumLoadingBeat(startAt)
            if (isFinishing || isDestroyed) return@launch
            hideLoadingOverlay()
            binding.authPickMascot.playWelcome()
            binding.authPickBubble.showMessage(
                "欢迎回来，${account.nickname}！",
                SquirrelSpeechBubble.Tone.WELCOME
            )
            if (ForestMotion.enabled()) {
                delay(900L)
            }
            submitting = false
            if (!isFinishing && !isDestroyed) {
                completeAuthSuccess(account.nickname, shouldPromptMerge)
            }
        }
    }

    private fun confirmDeleteSavedAccount(account: LocalAccountEntity) {
        ForestDialogBuilder(this)
            .setTitle("移除本机账号？")
            .setMessage("将清除「${account.nickname}」（${account.identifier}）在此设备上的所有数据，无法恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认移除") { _, _ ->
                val appCtx = applicationContext
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AuthRepository(appCtx).deleteLocalAccountByOwnerId(account.ownerId)
                    }
                    if (!isFinishing && !isDestroyed) {
                        loadSavedAccounts()
                        if (savedNonGuestAccounts.isEmpty()) {
                            managingAccounts = false
                            showStep(Step.CHOICE)
                        } else {
                            renderAccountPicker()
                        }
                    }
                }
            }
            .show()
    }

    private fun syncSubmitButtonState() {
        val idReady = !binding.authIdentifierInput.text?.toString().orEmpty().trim().isEmpty()
        val passReady = !binding.authPasswordInput.text?.toString().orEmpty().isEmpty()
        val active = idReady && passReady && !submitting
        binding.authSubmitButton.isEnabled = active
        binding.authSubmitButton.setTextColor(if (active) 0xFFFFFEFA.toInt() else 0xFF9E9587.toInt())
    }

    private fun submitCredentials() {
        if (submitting || !binding.authSubmitButton.isEnabled) return
        val identifier = binding.authIdentifierInput.text?.toString().orEmpty()
        val password = binding.authPasswordInput.text?.toString().orEmpty()
        val nickname = binding.authNicknameInput.text?.toString().orEmpty()
        val isRegister = currentStep == Step.REGISTER

        submitting = true
        syncSubmitButtonState()
        binding.authFormMascot.playLoading()
        showLoadingOverlay(if (isRegister) "正在创建账号…" else "正在验证账号…")

        val appCtx = applicationContext
        lifecycleScope.launch {
            val startAt = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                val repo = AuthRepository(appCtx)
                if (isRegister) {
                    repo.register(identifier, password, nickname)
                } else {
                    repo.login(identifier, password)
                }
            }
            awaitMinimumLoadingBeat(startAt)
            if (isFinishing || isDestroyed) return@launch
            hideLoadingOverlay()

            when (result) {
                is AuthResult.Error -> {
                    submitting = false
                    syncSubmitButtonState()
                    binding.authFormMascot.playError()
                    binding.authFormBubble.showMessage(result.message, SquirrelSpeechBubble.Tone.ERROR)
                }
                is AuthResult.Success -> {
                    binding.authFormMascot.playWelcome()
                    val welcomeText = if (isRegister) {
                        "欢迎加入小松果，${result.account.nickname}！"
                    } else {
                        "欢迎回来，${result.account.nickname}！"
                    }
                    binding.authFormBubble.showMessage(welcomeText, SquirrelSpeechBubble.Tone.WELCOME)
                    if (ForestMotion.enabled()) {
                        delay(950L)
                    }
                    submitting = false
                    if (!isFinishing && !isDestroyed) {
                        completeAuthSuccess(
                            result.account.nickname,
                            result.guestMergeSummary?.hasMergeableData == true
                        )
                    }
                }
            }
        }
    }

    private fun continueAsGuest() {
        if (submitting) return
        submitting = true
        val appCtx = applicationContext
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AuthRepository(appCtx).logoutToGuest()
            }
            submitting = false
            if (!isFinishing && !isDestroyed) {
                if (returnToCaller) {
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }

    private fun showLoadingOverlay(label: String) {
        binding.authLoadingLabel.text = label
        binding.authLoadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoadingOverlay() {
        binding.authLoadingOverlay.visibility = View.GONE
    }

    private suspend fun awaitMinimumLoadingBeat(startAtMillis: Long) {
        if (!ForestMotion.enabled()) return
        val elapsed = System.currentTimeMillis() - startAtMillis
        val remain = 360L - elapsed
        if (remain > 0L) delay(remain)
    }

    private fun completeAuthSuccess(nickname: String, promptGuestMerge: Boolean) {
        if (returnToCaller) {
            val data = Intent().apply {
                putExtra(EXTRA_PROMPT_GUEST_MERGE, promptGuestMerge)
                putExtra(EXTRA_ACCOUNT_NICKNAME, nickname)
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_PROMPT_GUEST_MERGE, promptGuestMerge)
                putExtra(EXTRA_ACCOUNT_NICKNAME, nickname)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun bindAgreementText() {
        val raw = "登录即表示同意《用户协议》与《隐私政策》"
        val span = SpannableString(raw)
        val termsStart = raw.indexOf("《用户协议》")
        if (termsStart >= 0) {
            span.setSpan(StyleSpan(Typeface.BOLD), termsStart, termsStart + 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val privacyStart = raw.indexOf("《隐私政策》")
        if (privacyStart >= 0) {
            span.setSpan(StyleSpan(Typeface.BOLD), privacyStart, privacyStart + 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.authAgreementText.text = span
    }

    private fun showForgotPasswordDialog() {
        ForestDialogBuilder(this)
            .setTitle("忘记密码")
            .setMessage("当前为本机离线账号时，密码仅以不可逆哈希校验，暂无法直接找回原密码；你也可以注册新账号，或在接入云服务器后通过云端重置。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showAgreementDialog() {
        ForestDialogBuilder(this)
            .setTitle("用户协议与隐私说明")
            .setMessage("小松果优先将你的账本、学习与反馈保存在本机，密码采用加盐哈希、令牌采用系统安全区加密保存，不会在未经授权时上传个人数据。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)

    companion object {
        const val EXTRA_RETURN_TO_CALLER = "return_to_caller"
        const val EXTRA_INITIAL_STEP = "initial_step"
        const val EXTRA_PROMPT_GUEST_MERGE = "prompt_guest_merge"
        const val EXTRA_ACCOUNT_NICKNAME = "account_nickname"

        const val STEP_PICK = "PICK"
        const val STEP_CHOICE = "CHOICE"
        const val STEP_LOGIN = "LOGIN"
        const val STEP_REGISTER = "REGISTER"

        @JvmStatic
        @JvmOverloads
        fun createIntent(
            context: Context,
            returnToCaller: Boolean = false,
            initialStep: String? = null
        ): Intent = Intent(context, AuthActivity::class.java).apply {
            putExtra(EXTRA_RETURN_TO_CALLER, returnToCaller)
            if (initialStep != null) {
                putExtra(EXTRA_INITIAL_STEP, initialStep)
            }
        }
    }
}

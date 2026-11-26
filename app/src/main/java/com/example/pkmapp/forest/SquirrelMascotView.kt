package com.example.pkmapp.forest

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import com.example.pkmapp.R

/**
 * Interactive animated mascot view for 小松果's scarfed watercolor squirrel.
 *
 * Combines matching hand-drawn pose sprites (idle / wave / confused / welcome) with subtle
 * breathing squash-and-stretch, periodic waving hops, error head-shake, welcome bounce, and a
 * soft grounded contact shadow. Respects [ForestMotion.enabled] for accessibility and Espresso.
 */
class SquirrelMascotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    enum class Mood {
        IDLE,
        WAVE,
        LOADING,
        ERROR,
        WELCOME
    }

    private var mood: Mood = Mood.IDLE
    private var idleBreathAnimator: AnimatorSet? = null
    private var activeReaction: AnimatorSet? = null
    private var waveTimerPosted = false
    private var autoWaveEnabled = true
    private var onTapMascot: (() -> Unit)? = null

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1A5C4938
    }
    private val errorBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF9E4C37.toInt()
    }
    private val errorBadgeInkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFEFA.toInt()
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val shadowRect = RectF()

    private val periodicWaveRunnable = Runnable {
        waveTimerPosted = false
        if (isAttachedToWindow && visibility == VISIBLE && autoWaveEnabled && mood == Mood.IDLE) {
            playWave(scheduleNext = true)
        }
    }

    init {
        scaleType = ScaleType.FIT_CENTER
        isClickable = true
        isFocusable = true
        contentDescription = "小松果伙伴"
        applyPoseDrawable(R.drawable.ic_launcher_character)
        setOnClickListener {
            ForestMotion.tap(this)
            playWave(scheduleNext = autoWaveEnabled)
            onTapMascot?.invoke()
        }
    }

    fun setOnMascotTapListener(listener: (() -> Unit)?) {
        onTapMascot = listener
    }

    fun setAutoWaveEnabled(enabled: Boolean) {
        autoWaveEnabled = enabled
        if (!enabled) {
            cancelScheduledWave()
        } else if (isAttachedToWindow && mood == Mood.IDLE) {
            scheduleNextWave(2600L)
        }
    }

    fun currentMood(): Mood = mood

    fun showIdle() {
        mood = Mood.IDLE
        cancelReaction()
        applyPoseDrawable(R.drawable.ic_launcher_character)
        resetTransform()
        startIdleBreathing()
        if (autoWaveEnabled) scheduleNextWave(2400L)
    }

    fun playWave(scheduleNext: Boolean = autoWaveEnabled) {
        mood = Mood.WAVE
        cancelScheduledWave()
        stopIdleBreathing()
        cancelReaction()
        anchorPivotAtFeet()
        applyPoseDrawable(R.drawable.squirrel_wave)

        if (!ForestMotion.enabled() || !isAttachedToWindow) {
            resetTransform()
            return
        }

        val settleX = ObjectAnimator.ofFloat(this, TRANSLATION_X, translationX, 0f).apply {
            duration = 240L
        }
        val lift = ObjectAnimator.ofFloat(
            this,
            TRANSLATION_Y,
            translationY, -dpF(2.5f), -dpF(0.6f), 0f
        ).apply {
            duration = 1050L
        }
        val gentleLean = ObjectAnimator.ofFloat(
            this,
            ROTATION,
            rotation, 2.4f, -1.8f, 1.0f, 0f
        ).apply {
            duration = 1050L
        }
        val breathY = ObjectAnimator.ofFloat(
            this,
            SCALE_Y,
            scaleY, 1.028f, 0.994f, 1.008f, 1f
        ).apply {
            duration = 1050L
        }
        val breathX = ObjectAnimator.ofFloat(
            this,
            SCALE_X,
            scaleX, 0.988f, 1.004f, 0.997f, 1f
        ).apply {
            duration = 1050L
        }
        activeReaction = AnimatorSet().apply {
            playTogether(settleX, lift, gentleLean, breathY, breathX)
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && mood == Mood.WAVE && isAttachedToWindow) {
                        mood = Mood.IDLE
                        applyPoseDrawable(R.drawable.ic_launcher_character)
                        startIdleBreathing()
                        if (scheduleNext) scheduleNextWave(4600L)
                    }
                }
            })
            start()
        }
    }

    fun playLoading() {
        mood = Mood.LOADING
        cancelScheduledWave()
        stopIdleBreathing()
        cancelReaction()
        anchorPivotAtFeet()
        applyPoseDrawable(R.drawable.ic_launcher_character)

        if (!ForestMotion.enabled() || !isAttachedToWindow) {
            resetTransform()
            return
        }

        val sway = ObjectAnimator.ofFloat(this, ROTATION, -2.0f, 2.0f).apply {
            duration = 720L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val bob = ObjectAnimator.ofFloat(this, TRANSLATION_Y, 0f, -dpF(2f)).apply {
            duration = 720L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        activeReaction = AnimatorSet().apply {
            playTogether(sway, bob)
            start()
        }
    }

    fun playError() {
        mood = Mood.ERROR
        cancelScheduledWave()
        stopIdleBreathing()
        cancelReaction()
        anchorPivotAtFeet()
        applyPoseDrawable(R.drawable.ic_launcher_character)
        invalidate()

        if (!ForestMotion.enabled() || !isAttachedToWindow) {
            resetTransform()
            return
        }

        val shakeX = ObjectAnimator.ofFloat(
            this,
            TRANSLATION_X,
            translationX, -dpF(5.5f), dpF(5.5f), -dpF(3.5f), dpF(2.2f), 0f
        ).apply { duration = 420L }
        val tilt = ObjectAnimator.ofFloat(
            this,
            ROTATION,
            rotation, -2.6f, 2.2f, -1.2f, 0f
        ).apply { duration = 420L }
        val settleY = ObjectAnimator.ofFloat(this, TRANSLATION_Y, translationY, 0f).apply {
            duration = 200L
        }
        activeReaction = AnimatorSet().apply {
            playTogether(shakeX, tilt, settleY)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    fun playWelcome() {
        mood = Mood.WELCOME
        cancelScheduledWave()
        stopIdleBreathing()
        cancelReaction()
        anchorPivotAtFeet()
        applyPoseDrawable(R.drawable.squirrel_welcome)
        ForestMotion.confirm(this)

        if (!ForestMotion.enabled() || !isAttachedToWindow) {
            resetTransform()
            return
        }

        val jump = ObjectAnimator.ofFloat(
            this,
            TRANSLATION_Y,
            translationY, -dpF(7.5f), 0f, -dpF(3f), 0f
        ).apply { duration = 720L }
        val popX = ObjectAnimator.ofFloat(this, SCALE_X, scaleX, 0.98f, 1.025f, 0.995f, 1f).apply {
            duration = 720L
        }
        val popY = ObjectAnimator.ofFloat(this, SCALE_Y, scaleY, 1.045f, 0.99f, 1.015f, 1f).apply {
            duration = 720L
        }
        val cheerTilt = ObjectAnimator.ofFloat(this, ROTATION, rotation, 2.4f, -1.8f, 0f).apply {
            duration = 720L
        }
        activeReaction = AnimatorSet().apply {
            playTogether(jump, popX, popY, cheerTilt)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        anchorPivotAtFeet()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anchorPivotAtFeet()
        if (mood == Mood.IDLE) {
            startIdleBreathing()
            if (autoWaveEnabled) scheduleNextWave(1800L)
        }
    }

    override fun onDetachedFromWindow() {
        cancelScheduledWave()
        stopIdleBreathing()
        cancelReaction()
        resetTransform()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (width > 0 && height > 0) {
            val rx = width * 0.28f
            val ry = dpF(3.5f)
            val cy = height - dpF(4f)
            shadowRect.set(width / 2f - rx, cy - ry, width / 2f + rx, cy + ry)
            canvas.drawOval(shadowRect, shadowPaint)
        }
        super.onDraw(canvas)
        if (mood == Mood.ERROR && width > 0 && height > 0) {
            val r = dpF(9.5f)
            val cx = width - r - dpF(3f)
            val cy = r + dpF(3f)
            canvas.drawCircle(cx, cy, r, errorBadgeBgPaint)
            errorBadgeInkPaint.textSize = dpF(12.5f)
            val textY = cy - (errorBadgeInkPaint.descent() + errorBadgeInkPaint.ascent()) / 2f
            canvas.drawText("!", cx, textY, errorBadgeInkPaint)
        }
    }

    private fun startIdleBreathing() {
        if (!ForestMotion.enabled() || !isAttachedToWindow) return
        if (idleBreathAnimator?.isRunning == true) return
        anchorPivotAtFeet()

        val breathY = ObjectAnimator.ofFloat(this, SCALE_Y, 1f, 1.018f).apply {
            duration = 2100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val breathX = ObjectAnimator.ofFloat(this, SCALE_X, 1f, 0.992f).apply {
            duration = 2100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        idleBreathAnimator = AnimatorSet().apply {
            playTogether(breathY, breathX)
            start()
        }
    }

    private fun anchorPivotAtFeet() {
        if (width > 0 && height > 0) {
            pivotX = width * 0.5f
            pivotY = height * 0.92f
        }
    }

    private fun stopIdleBreathing() {
        idleBreathAnimator?.cancel()
        idleBreathAnimator = null
    }

    private fun cancelReaction() {
        activeReaction?.cancel()
        activeReaction = null
    }

    private fun scheduleNextWave(delayMs: Long) {
        cancelScheduledWave()
        if (!ForestMotion.enabled() || !isAttachedToWindow) return
        waveTimerPosted = true
        postDelayed(periodicWaveRunnable, delayMs)
    }

    private fun cancelScheduledWave() {
        if (waveTimerPosted) {
            removeCallbacks(periodicWaveRunnable)
            waveTimerPosted = false
        }
    }

    private fun resetTransform() {
        translationX = 0f
        translationY = 0f
        rotation = 0f
        scaleX = 1f
        scaleY = 1f
    }

    private fun applyPoseDrawable(resId: Int) {
        val bmp: Bitmap = ForestBitmaps.get(context, resId)
        setImageBitmap(bmp)
    }

    private fun dpF(value: Float): Float = value * resources.displayMetrics.density
}

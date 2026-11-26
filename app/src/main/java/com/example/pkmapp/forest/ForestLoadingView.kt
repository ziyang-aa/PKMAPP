package com.example.pkmapp.forest;

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Lightweight 60fps Canvas loading indicator for 小松果.
 *
 * Draws either a 3-element botanical bounce wave (sprout leaf · acorn · pine leaf, inspired by
 * open-source Android-SpinKit ThreeBounce harmonic phase physics) or a compact orbiting leaf ring
 * for inline network sync indicators. Automatically falls back to a clean static frame when system
 * animators are disabled (accessibility / Espresso tests).
 */
class ForestLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode {
        ACORN_BOUNCE,
        LEAF_ORBIT
    }

    private var mode: Mode = Mode.ACORN_BOUNCE
    private var progress: Float = 0.25f
    private var animator: ValueAnimator? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x225C4938
    }
    private val tempRect = RectF()
    private val leafPath = Path()

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultW = if (mode == Mode.ACORN_BOUNCE) dp(78) else dp(22)
        val defaultH = if (mode == Mode.ACORN_BOUNCE) dp(34) else dp(22)
        val w = resolveSize(defaultW, widthMeasureSpec)
        val h = resolveSize(defaultH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) syncAnimation() else stopAnimation()
    }

    private fun syncAnimation() {
        if (!isAttachedToWindow || visibility != VISIBLE || !ForestMotion.enabled()) {
            stopAnimation()
            progress = 0.28f
            invalidate()
            return
        }
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (mode == Mode.ACORN_BOUNCE) 1280L else 1050L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mode == Mode.ACORN_BOUNCE) {
            drawAcornBounce(canvas)
        } else {
            drawLeafOrbit(canvas)
        }
    }

    private fun drawAcornBounce(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val spacing = minOf(w / 3.2f, dpF(24f))
        val centerX = w / 2f
        val baseY = h * 0.78f
        val jumpHeight = minOf(h * 0.42f, dpF(11f))
        val itemSize = minOf(h * 0.34f, dpF(10f))
        val phases = floatArrayOf(0f, 0.22f, 0.44f)

        for (i in 0..2) {
            val localT = ((progress - phases[i]) % 1f + 1f) % 1f
            // Smooth bounce lobe during first 62% of cycle, calm rest during remaining 38%
            val lift = if (localT < 0.62f) {
                sin((localT / 0.62f) * PI).toFloat()
            } else {
                0f
            }
            val cx = centerX + (i - 1) * spacing
            val cy = baseY - itemSize - lift * jumpHeight
            val scaleX = 1f + (1f - lift) * 0.06f - lift * 0.04f
            val scaleY = 1f - (1f - lift) * 0.05f + lift * 0.06f

            // Ground contact shadow
            shadowPaint.alpha = (44 - (lift * 24).toInt()).coerceIn(14, 44)
            val shadowRx = itemSize * (0.68f - lift * 0.22f)
            val shadowRy = dpF(2.1f)
            tempRect.set(cx - shadowRx, baseY - shadowRy, cx + shadowRx, baseY + shadowRy)
            canvas.drawOval(tempRect, shadowPaint)

            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(scaleX, scaleY)
            when (i) {
                0 -> drawBotanicalLeaf(canvas, itemSize * 0.92f, 0xFF6DA364.toInt(), -22f + lift * 14f)
                1 -> drawMiniAcorn(canvas, itemSize)
                else -> drawBotanicalLeaf(canvas, itemSize * 0.92f, 0xFF456444.toInt(), 22f - lift * 14f)
            }
            canvas.restore()
        }
    }

    private fun drawMiniAcorn(canvas: Canvas, r: Float) {
        // Nut body
        fillPaint.color = 0xFFC5844C.toInt()
        tempRect.set(-r * 0.68f, -r * 0.22f, r * 0.68f, r * 0.92f)
        canvas.drawOval(tempRect, fillPaint)

        // Highlight cheek on nut
        fillPaint.color = 0x55FFF6E6
        tempRect.set(-r * 0.36f, r * 0.04f, -r * 0.04f, r * 0.56f)
        canvas.drawOval(tempRect, fillPaint)

        // Cap dome
        fillPaint.color = 0xFF6D4C32.toInt()
        tempRect.set(-r * 0.82f, -r * 0.72f, r * 0.82f, r * 0.12f)
        canvas.drawArc(tempRect, 180f, 180f, true, fillPaint)
        tempRect.set(-r * 0.82f, -r * 0.36f, r * 0.82f, -r * 0.02f)
        canvas.drawRoundRect(tempRect, r * 0.2f, r * 0.2f, fillPaint)

        // Tiny top stem
        strokePaint.color = 0xFF5A3D27.toInt()
        strokePaint.strokeWidth = max(dpF(1.5f), r * 0.18f)
        canvas.drawLine(0f, -r * 0.70f, r * 0.14f, -r * 1.02f, strokePaint)
    }

    private fun drawBotanicalLeaf(canvas: Canvas, r: Float, color: Int, angleDeg: Float) {
        canvas.save()
        canvas.rotate(angleDeg)
        leafPath.reset()
        leafPath.moveTo(0f, -r * 0.95f)
        leafPath.cubicTo(r * 0.78f, -r * 0.42f, r * 0.72f, r * 0.52f, 0f, r * 0.92f)
        leafPath.cubicTo(-r * 0.72f, r * 0.52f, -r * 0.78f, -r * 0.42f, 0f, -r * 0.95f)
        leafPath.close()

        fillPaint.color = color
        canvas.drawPath(leafPath, fillPaint)

        // Delicate center vein
        strokePaint.color = 0x88FFFEFA.toInt()
        strokePaint.strokeWidth = max(dpF(1f), r * 0.11f)
        canvas.drawLine(0f, -r * 0.58f, 0f, r * 0.72f, strokePaint)
        canvas.restore()
    }

    private fun drawLeafOrbit(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val strokeW = max(dpF(2.2f), size * 0.12f)
        val radius = (size - strokeW * 2.2f) / 2f

        // Warm track
        strokePaint.color = 0x265C4938
        strokePaint.strokeWidth = strokeW
        canvas.drawCircle(cx, cy, radius, strokePaint)

        // Variable-length pine arc
        val startAngle = progress * 360f - 90f
        val sweepAngle = 95f + 55f * sin(progress * 2.0 * PI).toFloat()
        strokePaint.color = 0xFF456444.toInt()
        tempRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(tempRect, startAngle, sweepAngle, false, strokePaint)

        // Orbiting warm acorn dot at arc head
        val headRad = Math.toRadians((startAngle + sweepAngle).toDouble())
        val dotX = cx + (radius * cos(headRad)).toFloat()
        val dotY = cy + (radius * sin(headRad)).toFloat()
        fillPaint.color = 0xFFC5844C.toInt()
        canvas.drawCircle(dotX, dotY, strokeW * 0.72f, fillPaint)
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)
    private fun dpF(value: Float): Float = value * resources.displayMetrics.density
}

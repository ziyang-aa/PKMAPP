package com.example.pkmapp.forest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import androidx.appcompat.widget.AppCompatTextView

/**
 * Hand-crafted warm-paper speech bubble with a left pointer tail, designed to sit right beside
 * [SquirrelMascotView] for friendly greetings, wrong-password nudges, and welcome messages.
 */
class SquirrelSpeechBubble @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    enum class Tone {
        GREETING,
        ERROR,
        WELCOME
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val bubblePath = Path()
    private val bodyRect = RectF()
    private var tailWidthPx = 0f

    init {
        tailWidthPx = dpF(8f)
        setPadding(dp(19), dp(9), dp(14), dp(9))
        gravity = Gravity.CENTER_VERTICAL
        textSize = 13f
        setLineSpacing(dpF(2.5f), 1f)
        applyTone(Tone.GREETING)
        setWillNotDraw(false)
    }

    fun showMessage(message: String, tone: Tone, animated: Boolean = true) {
        applyTone(tone)
        text = message
        visibility = VISIBLE
        animate().cancel()
        if (animated && ForestMotion.enabled()) {
            pivotX = 0f
            pivotY = (height.takeIf { it > 0 } ?: dp(38)) * 0.5f
            alpha = 0f
            scaleX = 0.84f
            scaleY = 0.84f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260L)
                .setInterpolator(OvershootInterpolator(1.35f))
                .start()
        } else {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }
    }

    fun hideBubble() {
        animate().cancel()
        visibility = GONE
    }

    private fun applyTone(tone: Tone) {
        when (tone) {
            Tone.GREETING -> {
                fillPaint.color = 0xFFFFFEFA.toInt()
                strokePaint.color = 0x335C4938
                setTextColor(0xFF4B4236.toInt())
                setTypeface(null, Typeface.NORMAL)
            }
            Tone.ERROR -> {
                fillPaint.color = 0xFFFFF5F2.toInt()
                strokePaint.color = 0x889E4C37.toInt()
                setTextColor(0xFF9E4C37.toInt())
                setTypeface(null, Typeface.BOLD)
            }
            Tone.WELCOME -> {
                fillPaint.color = 0xFFEEF5E8.toInt()
                strokePaint.color = 0x77456444
                setTextColor(0xFF315E3A.toInt())
                setTypeface(null, Typeface.BOLD)
            }
        }
        strokePaint.strokeWidth = dpF(1.2f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > tailWidthPx + dpF(8f) && h > dpF(8f)) {
            val halfStroke = strokePaint.strokeWidth / 2f
            val left = tailWidthPx + halfStroke
            val top = halfStroke
            val right = w - halfStroke
            val bottom = h - halfStroke
            val radius = minOf(dpF(14f), (bottom - top) / 2f)
            val tailCenterY = (top + bottom) * 0.52f
            val tailHalfH = minOf(dpF(6f), (bottom - top) * 0.24f)

            bubblePath.reset()
            bodyRect.set(left, top, right, bottom)
            bubblePath.addRoundRect(bodyRect, radius, radius, Path.Direction.CW)

            // Draw smooth left pointer tail blended cleanly with rounded body
            val tailPath = Path().apply {
                moveTo(left + dpF(2f), tailCenterY - tailHalfH)
                lineTo(halfStroke, tailCenterY)
                lineTo(left + dpF(2f), tailCenterY + tailHalfH)
                close()
            }
            canvas.drawPath(bubblePath, fillPaint)
            canvas.drawPath(bubblePath, strokePaint)
            // Cover border seam where tail joins body, then outline the two exposed tail edges
            canvas.drawPath(tailPath, fillPaint)
            val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePaint.strokeWidth + dpF(1f)
                color = fillPaint.color
            }
            canvas.drawLine(left, tailCenterY - tailHalfH + dpF(1f), left, tailCenterY + tailHalfH - dpF(1f), seamPaint)
            canvas.drawLine(left, tailCenterY - tailHalfH, halfStroke, tailCenterY, strokePaint)
            canvas.drawLine(halfStroke, tailCenterY, left, tailCenterY + tailHalfH, strokePaint)
        }
        super.onDraw(canvas)
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)
    private fun dpF(value: Float): Float = value * resources.displayMetrics.density
}

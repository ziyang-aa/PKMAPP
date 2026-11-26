package com.example.pkmapp.forest

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.example.pkmapp.R
import com.example.pkmapp.forest.ForestUi.DEEP
import com.example.pkmapp.forest.ForestUi.MUTED
import com.example.pkmapp.forest.ForestUi.column
import com.example.pkmapp.forest.ForestUi.heading
import com.example.pkmapp.forest.ForestUi.ripple
import com.example.pkmapp.forest.ForestUi.rounded
import com.example.pkmapp.forest.ForestUi.row
import com.example.pkmapp.forest.ForestUi.text
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/**
 * Shared warm-porcelain bottom sheet builder with Android 15/16 Edge-to-Edge IME avoidance
 * and editorial hairline styling (zero nested cards).
 */
object ForestSheetHelper {

    const val INK = 0xff243624.toInt()
    const val HAIRLINE = 0x1f5c4938
    const val PINE = 0xff456444.toInt()
    const val PORCELAIN = 0xfffffefa.toInt()
    const val AMBER = 0xffc68b3c.toInt()
    const val TERRACOTTA = 0xff9e4c37.toInt()

    @JvmStatic
    fun createSheet(context: Context): BottomSheetDialog {
        val sheet = BottomSheetDialog(context, R.style.ThemeOverlay_PkmAPP_StudySheet)
        sheet.setDismissWithAnimation(ForestMotion.enabled())
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
            window.setDimAmount(0.34f)
        }
        return sheet
    }

    @JvmStatic
    fun attachContentWithImeAvoidance(sheet: BottomSheetDialog, content: View) {
        val context = sheet.context
        val scroll = NestedScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val host = FrameLayout(context).apply {
            addView(
                scroll,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val baseBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottom = maxOf(nav, ime)
            content.setPadding(
                content.paddingLeft,
                content.paddingTop,
                content.paddingRight,
                baseBottom + bottom
            )
            insets
        }
        sheet.setContentView(host)
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            if (bottomSheet != null) {
                bottomSheet.background = buildSheetSurface(context)
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            ViewCompat.requestApplyInsets(host)
        }
    }

    private fun buildSheetSurface(context: Context): Drawable {
        val r = dp(context, 26).toFloat()
        return GradientDrawable().apply {
            setColor(PORCELAIN)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }

    @JvmStatic
    fun createSheetRoot(
        context: Context,
        sheet: BottomSheetDialog,
        titleText: String,
        subtitleText: String?
    ): LinearLayout {
        val root = column(context)
        root.setPadding(dp(context, 22), dp(context, 10), dp(context, 22), dp(context, 22))

        val grip = View(context).apply {
            background = rounded(context, 0x387b5c3b, 0, 3)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val gripLp = LinearLayout.LayoutParams(dp(context, 36), dp(context, 4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(grip, gripLp)

        val header = row(context)
        val title = heading(context, titleText, 21).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(INK)
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = text(context, "✕", 15, 0xff525e50.toInt()).apply {
            gravity = Gravity.CENTER
            contentDescription = "关闭弹窗"
            background = ripple(context, rounded(context, 0x125c4938, 0, 999), false)
            setOnClickListener { sheet.dismiss() }
        }
        ForestMotion.bind(close)
        header.addView(close, LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)))
        ForestUi.add(root, header, 12)

        if (!subtitleText.isNullOrBlank()) {
            val sub = text(context, subtitleText, 12, MUTED)
            sub.fontFeatureSettings = "tnum"
            ForestUi.add(root, sub, 4)
        }
        addHairline(root, 12, 14)
        return root
    }

    @JvmStatic
    fun addHairline(parent: LinearLayout, topDp: Int, bottomDp: Int) {
        val ctx = parent.context
        val line = View(ctx).apply { setBackgroundColor(HAIRLINE) }
        val h = maxOf(1, Math.round(0.75f * ctx.resources.displayMetrics.density))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h).apply {
            topMargin = dp(ctx, topDp)
            bottomMargin = dp(ctx, bottomDp)
        }
        parent.addView(line, lp)
    }

    @JvmStatic
    fun fieldLabel(context: Context, textVal: String): TextView =
        text(context, textVal, 12, MUTED).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

    @JvmStatic
    fun underlineInput(context: Context, hint: String, inputType: Int): EditText {
        return EditText(context).apply {
            setSingleLine(true)
            this.hint = hint
            this.inputType = inputType
            textSize = 15f
            setTextColor(INK)
            setHintTextColor(0xff9ca195.toInt())
            setPadding(dp(context, 2), dp(context, 8), dp(context, 2), dp(context, 10))
            minimumHeight = dp(context, 44)
            val bg = StateListDrawable()
            bg.addState(intArrayOf(android.R.attr.state_focused), bottomLineDrawable(context, PINE, 1.5f))
            bg.addState(IntArray(0), bottomLineDrawable(context, HAIRLINE, 1f))
            background = bg
            backgroundTintList = null
        }
    }

    @JvmStatic
    fun quickChip(context: Context, label: String, onClick: View.OnClickListener): TextView {
        return text(context, label, 12, DEEP).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            gravity = Gravity.CENTER
            minHeight = dp(context, 36)
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
            background = ripple(context, rounded(context, PORCELAIN, 0x38315e3a, 999), false)
            setOnClickListener(onClick)
            ForestMotion.bind(this)
        }
    }

    @JvmStatic
    fun primaryButton(context: Context, label: String, onClick: View.OnClickListener): MaterialButton {
        return ForestUi.button(context, label, true).apply {
            minHeight = dp(context, 48)
            minimumHeight = dp(context, 48)
            cornerRadius = dp(context, 14)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setOnClickListener(onClick)
        }
    }

    @JvmStatic
    fun quietAction(context: Context, label: String, color: Int, onClick: View.OnClickListener): TextView {
        return text(context, label, 13, color).apply {
            gravity = Gravity.CENTER
            minHeight = dp(context, 44)
            background = ripple(context, rounded(context, Color.TRANSPARENT, 0, 12), false)
            setOnClickListener(onClick)
            ForestMotion.bind(this)
        }
    }

    private fun dp(context: Context, value: Int): Int = ForestUi.dp(context, value.toFloat())

    private fun bottomLineDrawable(context: Context, color: Int, heightDp: Float): Drawable {
        val bar = GradientDrawable().apply { setColor(color) }
        val layer = LayerDrawable(arrayOf<Drawable>(bar))
        layer.setLayerGravity(0, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
        layer.setLayerHeight(0, maxOf(1, Math.round(heightDp * context.resources.displayMetrics.density)))
        return layer
    }
}

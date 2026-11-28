package com.example.pkmapp.forest;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.pkmapp.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

public final class ForestUi {

    public static final int PAPER = 0xfff5f1e7,
        LIGHT = 0xfffffcf3,
        GREEN = 0xff4e8146,
        DEEP = 0xff315e3a,
        WOOD = 0xff7b5c3b,
        MUTED = 0xff736c5d,
        LINE = 0xffdddcc9;

    private ForestUi() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static TextView text(Context context, String value, int sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView heading(Context context, String value, int sp) {
        TextView view = text(context, value, sp, DEEP);
        view.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        return view;
    }

    public static MaterialButton button(Context context, String label, boolean primary) {
        ForestButton button = new ForestButton(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(primary ? LIGHT : DEEP);
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? GREEN : 0xffedf0e0));
        button.setStrokeColor(ColorStateList.valueOf(primary ? GREEN : 0xffcbd3ba));
        button.setStrokeWidth(dp(context, 1));
        button.setCornerRadius(dp(context, 11));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(dp(context, 46));
        button.setMinimumHeight(dp(context, 46));
        button.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        button.setLetterSpacing(.04f);
        button.present(
            primary ? ForestSurface.Kind.PRIMARY : ForestSurface.Kind.SECONDARY,
            primary ? 0xff456444 : LIGHT,
            primary ? 0xff345439 : 0xffd5d6bf,
            GREEN
        );
        return button;
    }

    public static Drawable ripple(Context context, Drawable content, boolean primary) {
        return new RippleDrawable(
            ColorStateList.valueOf(primary ? 0x28ffffff : 0x184e8146),
            content,
            rounded(context, 0xffffffff, 0, 12)
        );
    }

    public static void bookmark(MaterialButton button, int accent, boolean selected) {
        if (button instanceof ForestButton) ((ForestButton) button).present(
            ForestSurface.Kind.BOOKMARK,
            selected ? 0xffedf1e2 : LIGHT,
            selected ? 0xff9eaf88 : LINE,
            accent
        );
        button.setSelected(selected);
    }

    public static GradientDrawable rounded(Context context, int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static void paper(View view) {
        BitmapDrawable background = new BitmapDrawable(
            view.getResources(),
            ForestBitmaps.get(view.getContext(), R.drawable.forest_paper_clean)
        );
        background.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        view.setBackground(background);
    }

    public static ImageView image(Context context, int resource, String description) {
        ImageView image = new ImageView(context);
        image.setImageBitmap(ForestBitmaps.get(context, resource));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(description);
        return image;
    }

    public static void add(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(parent.getContext(), topDp);
        parent.addView(child, params);
    }

    public static void spacer(LinearLayout parent, int heightDp) {
        parent.addView(
            new View(parent.getContext()),
            new LinearLayout.LayoutParams(1, dp(parent.getContext(), heightDp))
        );
    }

    public static MaterialCheckBox check(Context context, CharSequence label, boolean switchMode) {
        return checkInternal(context, label, null, switchMode);
    }

    public static MaterialCheckBox checkWithSubtitle(Context context, String title, String subtitle) {
        return checkInternal(context, title, subtitle, false);
    }

    public static MaterialCheckBox compactSwitch(Context context) {
        MaterialCheckBox checkbox = new MaterialCheckBox(context);
        checkbox.setUseMaterialThemeColors(false);
        checkbox.setButtonTintList(null);
        checkbox.setButtonIconDrawable(null);
        checkbox.setButtonDrawable(new ForestCheckDrawable(context, true, null));
        checkbox.setBackground(null);
        checkbox.setText(null);
        checkbox.setMinWidth(0);
        checkbox.setMinimumWidth(0);
        checkbox.setMinHeight(0);
        checkbox.setMinimumHeight(0);
        checkbox.setPadding(0, 0, 0, 0);
        return checkbox;
    }

    public static MaterialCheckBox flatSwitchRow(Context context, String title, String subtitle) {
        MaterialCheckBox checkbox = new MaterialCheckBox(context);
        checkbox.setUseMaterialThemeColors(false);
        checkbox.setButtonTintList(null);
        checkbox.setButtonIconDrawable(null);
        checkbox.setButtonDrawable(null);
        checkbox.setTag(title);
        checkbox.setText(formatTwoLine(title, subtitle));
        checkbox.setTextColor(0xff243624);
        checkbox.setTextSize(14.5f);
        checkbox.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        checkbox.setCompoundDrawablesRelativeWithIntrinsicBounds(
            null,
            null,
            new ForestCheckDrawable(context, true, null),
            null
        );
        checkbox.setCompoundDrawablePadding(dp(context, 12));
        checkbox.setGravity(Gravity.CENTER_VERTICAL);
        checkbox.setLineSpacing(dp(context, 4), 1f);
        checkbox.setMinHeight(dp(context, 56));
        checkbox.setMinimumHeight(dp(context, 56));
        checkbox.setPadding(dp(context, 2), dp(context, 12), dp(context, 2), dp(context, 12));
        checkbox.setBackground(null);
        return checkbox;
    }

    public static void updateFlatSwitchSubtitle(MaterialCheckBox checkbox, String subtitle) {
        Object tag = checkbox.getTag();
        if (tag instanceof String) {
            checkbox.setText(formatTwoLine((String) tag, subtitle));
            return;
        }
        updateSwitchSubtitle(checkbox, subtitle);
    }

    public static MaterialCheckBox flatCheckWithSubtitle(Context context, String title, String subtitle) {
        MaterialCheckBox checkbox = checkInternal(context, title, subtitle, false);
        checkbox.setBackground(ripple(context, rounded(context, android.graphics.Color.TRANSPARENT, 0, 12), false));
        checkbox.setPadding(
            dp(context, 10),
            dp(context, subtitle != null && !subtitle.isEmpty() ? 10 : 11),
            dp(context, 4),
            dp(context, subtitle != null && !subtitle.isEmpty() ? 26 : 11)
        );
        return checkbox;
    }

    public static MaterialCheckBox switchWithSubtitle(Context context, String title, String subtitle) {
        MaterialCheckBox checkbox = checkInternal(context, formatTwoLine(title, subtitle), null, true);
        Drawable[] drawables = checkbox.getCompoundDrawablesRelative();
        if (drawables[0] instanceof BellBadgeDrawable) {
            ((BellBadgeDrawable) drawables[0]).setTitle(title);
        }
        return checkbox;
    }

    public static void updateSwitchSubtitle(MaterialCheckBox checkbox, String subtitle) {
        Drawable[] drawables = checkbox.getCompoundDrawablesRelative();
        if (drawables[0] instanceof BellBadgeDrawable) {
            String title = ((BellBadgeDrawable) drawables[0]).getTitle();
            if (title != null) {
                checkbox.setText(formatTwoLine(title, subtitle));
            }
        }
    }

    private static CharSequence formatTwoLine(String title, String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) return title;
        String raw = title + "\n" + subtitle;
        SpannableString span = new SpannableString(raw);
        int subStart = title.length() + 1;
        span.setSpan(new RelativeSizeSpan(0.82f), subStart, raw.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(0xff6c7669), subStart, raw.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    private static MaterialCheckBox checkInternal(
        Context context,
        CharSequence label,
        String subtitle,
        boolean switchMode
    ) {
        MaterialCheckBox checkbox = new MaterialCheckBox(context);
        checkbox.setUseMaterialThemeColors(false);
        checkbox.setButtonTintList(null);
        checkbox.setButtonIconDrawable(null);
        checkbox.setText(label);
        checkbox.setTextColor(0xff243624);
        checkbox.setTextSize(14f);
        checkbox.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (label instanceof Spanned) {
            Spanned spanned = (Spanned) label;
            for (CourseCodeSpan span : spanned.getSpans(0, spanned.length(), CourseCodeSpan.class)) {
                span.bind(checkbox);
            }
        }
        boolean hasSubtitle = subtitle != null && !subtitle.isEmpty();
        if (switchMode) {
            checkbox.setButtonDrawable(null);
            checkbox.setCompoundDrawablesRelativeWithIntrinsicBounds(
                new BellBadgeDrawable(context),
                null,
                new ForestCheckDrawable(context, true, null),
                null
            );
            checkbox.setCompoundDrawablePadding(dp(context, 11));
            checkbox.setGravity(Gravity.CENTER_VERTICAL);
            checkbox.setLineSpacing(dp(context, 4), 1f);
            checkbox.setMinHeight(dp(context, 60));
            checkbox.setMinimumHeight(dp(context, 60));
            checkbox.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        } else {
            checkbox.setButtonDrawable(new ForestCheckDrawable(context, false, subtitle));
            checkbox.setGravity(Gravity.CENTER_VERTICAL);
            int minH = dp(context, hasSubtitle ? 64 : 52);
            checkbox.setMinHeight(minH);
            checkbox.setMinimumHeight(minH);
            checkbox.setPadding(
                dp(context, 10),
                dp(context, hasSubtitle ? 10 : 11),
                dp(context, 14),
                dp(context, hasSubtitle ? 28 : 11)
            );
        }
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[] { -android.R.attr.state_enabled }, rounded(context, 0xfff5f2e8, 0, 12));
        state.addState(new int[] { android.R.attr.state_checked }, rounded(context, 0xffeef4ea, 0, 12));
        state.addState(new int[0], rounded(context, android.graphics.Color.TRANSPARENT, 0, 12));
        checkbox.setBackground(ripple(context, state, false));
        ForestMotion.bind(checkbox);
        return checkbox;
    }

    public static CharSequence badgeText(Context context, String code, String title, String suffix) {
        String raw = (code.isEmpty() ? title : code + "  " + title) + suffix;
        SpannableString span = new SpannableString(raw);
        if (!code.isEmpty()) {
            span.setSpan(
                new CourseCodeSpan(context, code.length()),
                0,
                code.length() + 2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        if (!suffix.isEmpty()) {
            int start = raw.length() - suffix.length();
            span.setSpan(
                new TrailingTagSpan(context),
                start,
                raw.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return span;
    }

    private static final class BellBadgeDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private String title;

        BellBadgeDrawable(Context context) {
            this.d = context.getResources().getDisplayMetrics().density;
        }

        void setTitle(String title) {
            this.title = title;
        }

        String getTitle() {
            return title;
        }

        @Override
        public int getIntrinsicWidth() {
            return Math.round(36 * d);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round(36 * d);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF box = new RectF(getBounds());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffe7efe3);
            canvas.drawRoundRect(box, 11 * d, 11 * d, paint);

            float cx = box.centerX();
            float cy = box.centerY();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * d);
            paint.setColor(0xff355336);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            Path bell = new Path();
            bell.moveTo(cx - 5.6f * d, cy + 3.4f * d);
            bell.lineTo(cx + 5.6f * d, cy + 3.4f * d);
            bell.quadTo(cx + 4.5f * d, cy + 1.0f * d, cx + 4.5f * d, cy - 1.6f * d);
            bell.arcTo(new RectF(cx - 4.5f * d, cy - 6.1f * d, cx + 4.5f * d, cy + 2.9f * d), 0, -180, false);
            bell.quadTo(cx - 4.5f * d, cy + 1.0f * d, cx - 5.6f * d, cy + 3.4f * d);
            canvas.drawPath(bell, paint);
            canvas.drawArc(
                new RectF(cx - 1.7f * d, cy + 3.4f * d, cx + 1.7f * d, cy + 6.1f * d),
                0,
                180,
                false,
                paint
            );
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class ForestCheckDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private final boolean switchMode;
        private final String subtitle;
        private boolean checked;
        private boolean initialized;
        private boolean enabled = true;
        private float progress;
        private android.animation.ValueAnimator toggleAnim;

        ForestCheckDrawable(Context context, boolean switchMode, String subtitle) {
            this.d = context.getResources().getDisplayMetrics().density;
            this.switchMode = switchMode;
            this.subtitle = subtitle;
        }

        @Override
        public int getIntrinsicWidth() {
            return Math.round((switchMode ? 54 : 36) * d);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round((switchMode ? 30 : 26) * d);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean wasChecked = checked;
            boolean wasEnabled = enabled;
            checked = false;
            enabled = false;
            for (int s : state) {
                if (s == android.R.attr.state_checked) checked = true;
                if (s == android.R.attr.state_enabled) enabled = true;
            }
            if (!initialized) {
                initialized = true;
                progress = checked ? 1f : 0f;
                invalidateSelf();
                return true;
            }
            if (wasChecked != checked) {
                if (switchMode && ForestMotion.enabled()) {
                    if (toggleAnim != null) toggleAnim.cancel();
                    toggleAnim = android.animation.ValueAnimator.ofFloat(progress, checked ? 1f : 0f);
                    toggleAnim.setDuration(240);
                    toggleAnim.setInterpolator(new android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f));
                    toggleAnim.addUpdateListener(a -> {
                        progress = (float) a.getAnimatedValue();
                        invalidateSelf();
                    });
                    toggleAnim.start();
                } else {
                    progress = checked ? 1f : 0f;
                    invalidateSelf();
                }
                return true;
            }
            if (wasEnabled != enabled) {
                invalidateSelf();
                return true;
            }
            return false;
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float cy = b.exactCenterY();
            if (switchMode) {
                float trackW = 52 * d;
                float trackH = 28 * d;
                float left = b.left + d;
                RectF track = new RectF(left, cy - trackH / 2f, left + trackW, cy + trackH / 2f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(androidx.core.graphics.ColorUtils.blendARGB(0xffd6d2c1, 0xff456444, progress));
                canvas.drawRoundRect(track, trackH / 2f, trackH / 2f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(d);
                paint.setColor(androidx.core.graphics.ColorUtils.blendARGB(0xffc3beaa, 0xff345439, progress));
                canvas.drawRoundRect(track, trackH / 2f, trackH / 2f, paint);

                float thumbR = 11f * d;
                float startCx = track.left + 3 * d + thumbR;
                float endCx = track.right - 3 * d - thumbR;
                float thumbCx = startCx + (endCx - startCx) * progress;

                // Soft elevation shadow under the circular thumb
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x22243624);
                canvas.drawCircle(thumbCx, cy + 1.2f * d, thumbR, paint);

                // Warm cream circular knob
                paint.setColor(0xfffffcf3);
                canvas.drawCircle(thumbCx, cy, thumbR, paint);

                // Outlined botanical leaf with central stem & veins rotating 360° (Morphing Leaf Switch)
                canvas.save();
                canvas.translate(thumbCx, cy);
                canvas.rotate(360f * progress);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.35f * d);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(androidx.core.graphics.ColorUtils.blendARGB(0xff7b6f5c, 0xff456444, progress));

                Path leaf = new Path();
                leaf.moveTo(4.6f * d, -4.6f * d);
                leaf.quadTo(-3.4f * d, -4.4f * d, -3.6f * d, 2.8f * d);
                leaf.quadTo(3.8f * d, 3.6f * d, 4.6f * d, -4.6f * d);
                canvas.drawPath(leaf, paint);
                // Diagonal stem / midrib vein
                canvas.drawLine(-4.8f * d, 4.8f * d, 2.2f * d, -2.2f * d, paint);
                // Tiny side veins
                canvas.drawLine(-1.2f * d, 1.2f * d, -1.5f * d, -1.5f * d, paint);
                canvas.drawLine(0.6f * d, -0.6f * d, 2.5f * d, -0.2f * d, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
                canvas.restore();
                return;
            }

            float size = 22 * d;
            float left = b.left + 14 * d;
            float top = cy - size / 2f;
            RectF box = new RectF(left, top, left + size, top + size);
            float radius = 7 * d;

            if (checked) {
                RectF halo = new RectF(box);
                halo.inset(-2.6f * d, -2.6f * d);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(enabled ? 0x26456444 : 0x16456444);
                canvas.drawRoundRect(halo, radius + 2.5f * d, radius + 2.5f * d, paint);

                paint.setColor(enabled ? 0xff456444 : 0xff7a9077);
                canvas.drawRoundRect(box, radius, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.2f * d);
                paint.setColor(enabled ? 0xff385438 : 0xff6c8169);
                canvas.drawRoundRect(box, radius, radius, paint);

                paint.setColor(0xfffffcf3);
                paint.setStrokeWidth(2.0f * d);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                Path checkPath = new Path();
                checkPath.moveTo(box.left + 5.8f * d, cy + 0.2f * d);
                checkPath.lineTo(box.left + 9.6f * d, cy + 4.0f * d);
                checkPath.lineTo(box.left + 16.2f * d, cy - 3.2f * d);
                canvas.drawPath(checkPath, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xfffffcf3);
                canvas.drawRoundRect(box, radius, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * d);
                paint.setColor(0xffb7c0b2);
                canvas.drawRoundRect(box, radius, radius, paint);
            }

            if (subtitle != null && !subtitle.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xff6c7669);
                paint.setTextSize(12f * d);
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                canvas.drawText(subtitle, b.left + 46 * d, cy + 15.5f * d, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class CourseCodeSpan extends ReplacementSpan {
        private final float d;
        private final int codeLen;
        private MaterialCheckBox host;

        CourseCodeSpan(Context context, int codeLen) {
            this.d = context.getResources().getDisplayMetrics().density;
            this.codeLen = codeLen;
        }

        void bind(MaterialCheckBox checkbox) {
            this.host = checkbox;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float prevSize = paint.getTextSize();
            paint.setTextSize(11.5f * d);
            int codeEnd = Math.min(end, start + codeLen);
            int width = Math.round(paint.measureText(text, start, codeEnd) + 14 * d + 8 * d);
            paint.setTextSize(prevSize);
            return width;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, Paint paint) {
            int prevColor = paint.getColor();
            float prevSize = paint.getTextSize();
            Typeface prevTypeface = paint.getTypeface();
            Paint.FontMetrics mainFm = paint.getFontMetrics();
            float lineCenterY = y + (mainFm.ascent + mainFm.descent) / 2f;

            int codeEnd = Math.min(end, start + codeLen);
            paint.setTextSize(11.5f * d);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            float textWidth = paint.measureText(text, start, codeEnd);
            float padH = 7f * d;
            float pillW = textWidth + padH * 2;
            float pillH = 20 * d;
            RectF rect = new RectF(x, lineCenterY - pillH / 2f, x + pillW, lineCenterY + pillH / 2f);

            boolean active = host == null || (host.isChecked() && host.isEnabled());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(active ? 0xffdce8d5 : 0xffefece1);
            canvas.drawRoundRect(rect, 6 * d, 6 * d, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d);
            paint.setColor(active ? 0xffbdd0b8 : 0xffdcd9ca);
            canvas.drawRoundRect(rect, 6 * d, 6 * d, paint);

            Paint.FontMetrics badgeFm = paint.getFontMetrics();
            float badgeBaseline = rect.centerY() - (badgeFm.ascent + badgeFm.descent) / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(active ? 0xff2e4f2f : 0xff5a6558);
            canvas.drawText(text, start, codeEnd, x + padH, badgeBaseline, paint);

            paint.setColor(prevColor);
            paint.setTextSize(prevSize);
            paint.setTypeface(prevTypeface);
        }
    }

    private static final class TrailingTagSpan extends ReplacementSpan {
        private final float d;

        TrailingTagSpan(Context context) {
            this.d = context.getResources().getDisplayMetrics().density;
        }

        private String cleanLabel(CharSequence text, int start, int end) {
            String raw = text.subSequence(start, end).toString().trim();
            if (raw.startsWith("·")) raw = raw.substring(1).trim();
            if ("已添加".equals(raw)) return "已加入书架";
            return raw;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float prevSize = paint.getTextSize();
            paint.setTextSize(10.5f * d);
            String label = cleanLabel(text, start, end);
            int width = Math.round(paint.measureText(label) + 24 * d);
            paint.setTextSize(prevSize);
            return width;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, Paint paint) {
            int prevColor = paint.getColor();
            float prevSize = paint.getTextSize();
            Typeface prevTypeface = paint.getTypeface();
            Paint.FontMetrics mainFm = paint.getFontMetrics();
            float lineCenterY = y + (mainFm.ascent + mainFm.descent) / 2f;

            String label = cleanLabel(text, start, end);
            paint.setTextSize(10.5f * d);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float textWidth = paint.measureText(label);
            float padH = 7.5f * d;
            float pillW = textWidth + padH * 2;
            float pillH = 20 * d;
            float left = x + 8 * d;
            RectF rect = new RectF(left, lineCenterY - pillH / 2f, left + pillW, lineCenterY + pillH / 2f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffe6e3d6);
            canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, paint);

            Paint.FontMetrics badgeFm = paint.getFontMetrics();
            float badgeBaseline = rect.centerY() - (badgeFm.ascent + badgeFm.descent) / 2f;
            paint.setColor(0xff5f685d);
            canvas.drawText(label, left + padH, badgeBaseline, paint);

            paint.setColor(prevColor);
            paint.setTextSize(prevSize);
            paint.setTypeface(prevTypeface);
        }
    }
}

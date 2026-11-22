package com.example.pkmapp.forest;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;

/** Cardless editorial To-Do row with a Uiverse-inspired dash-draw pen checkbox and animated strikethrough. */
public final class ForestTodoRow extends LinearLayout {
    private static final PathInterpolator EASE = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private final PenCheckBox checkBox;
    private final StrikeTitleView titleView;
    private boolean checked;
    private boolean animating;
    private ValueAnimator animator;

    public ForestTodoRow(
        Context context,
        String title,
        int subjectColor,
        String subjectName,
        String dueMeta,
        boolean urgentDue,
        boolean initialChecked,
        Runnable onToggle,
        Runnable onEdit
    ) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        this.checked = initialChecked;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Math.round(58 * d));
        setPadding(Math.round(2 * d), Math.round(10 * d), Math.round(4 * d), Math.round(10 * d));
        setBackground(ForestUi.ripple(context, ForestUi.rounded(context, Color.TRANSPARENT, 0, 12), false));

        checkBox = new PenCheckBox(context, initialChecked);
        checkBox.setContentDescription((initialChecked ? "取消完成 " : "完成待办 ") + title);
        checkBox.setOnClickListener(v -> triggerToggle(onToggle));
        addView(checkBox, new LayoutParams(Math.round(38 * d), Math.round(38 * d)));

        LinearLayout copy = ForestUi.column(context);
        titleView = new StrikeTitleView(context, title, initialChecked);
        copy.addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if ((subjectName != null && !subjectName.isEmpty()) || (dueMeta != null && !dueMeta.isEmpty())) {
            LinearLayout metaRow = ForestUi.row(context);
            if (subjectName != null && !subjectName.isEmpty()) {
                View dot = new View(context);
                dot.setBackground(ForestUi.rounded(context, subjectColor, 0, 4));
                LayoutParams dotLp = new LayoutParams(Math.round(7 * d), Math.round(7 * d));
                dotLp.setMarginEnd(Math.round(6 * d));
                metaRow.addView(dot, dotLp);
                metaRow.addView(ForestUi.text(context, subjectName, 11, 0xff737c6f));
            }
            if (dueMeta != null && !dueMeta.isEmpty()) {
                String prefix = (subjectName != null && !subjectName.isEmpty()) ? "  ·  " : "";
                TextView dueTv = ForestUi.text(
                    context,
                    prefix + dueMeta,
                    11,
                    urgentDue && !initialChecked ? 0xff9e4c37 : 0xff737c6f
                );
                if (urgentDue && !initialChecked) {
                    dueTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                }
                metaRow.addView(dueTv);
            }
            ForestUi.add(copy, metaRow, 4);
        }

        LayoutParams copyLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        copyLp.setMarginStart(Math.round(10 * d));
        addView(copy, copyLp);

        if (onEdit != null) {
            TextView chevron = ForestUi.text(context, "›", 19, 0xff9aa193);
            chevron.setPadding(Math.round(10 * d), 0, Math.round(2 * d), 0);
            chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(chevron);
            setOnClickListener(v -> onEdit.run());
        }
    }

    private void triggerToggle(Runnable onToggle) {
        if (animating) return;
        boolean next = !checked;
        checked = next;
        if (!ForestMotion.enabled()) {
            checkBox.setProgress(next ? 1f : 0f);
            titleView.setStrikeProgress(next ? 1f : 0f);
            onToggle.run();
            return;
        }
        animating = true;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(next ? 0f : 1f, next ? 1f : 0f);
        animator.setDuration(290);
        animator.setInterpolator(EASE);
        animator.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            checkBox.setProgress(p);
            titleView.setStrikeProgress(p);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animating = false;
                onToggle.run();
            }
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    private static final class PenCheckBox extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path fullCheck = new Path();
        private final Path partialCheck = new Path();
        private final PathMeasure measure = new PathMeasure();
        private final float d;
        private float progress;

        PenCheckBox(Context context, boolean initialChecked) {
            super(context);
            this.d = context.getResources().getDisplayMetrics().density;
            this.progress = initialChecked ? 1f : 0f;
            setClickable(true);
            setFocusable(true);
        }

        void setProgress(float p) {
            this.progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float bounce = progress > 0f && progress < 1f
                ? 1f + 0.10f * (float) Math.sin(progress * Math.PI)
                : 1f;
            float size = 21 * d * bounce;
            float left = cx - size / 2f;
            float top = cy - size / 2f;
            RectF box = new RectF(left, top, left + size, top + size);
            float radius = 7 * d;

            if (progress > 0.01f) {
                RectF halo = new RectF(box);
                float haloExpand = (2.8f + 1.4f * (1f - progress)) * d;
                halo.inset(-haloExpand, -haloExpand);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x28456444);
                paint.setAlpha(Math.round(0x28 * progress));
                canvas.drawRoundRect(halo, radius + 2.5f * d, radius + 2.5f * d, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.blendARGB(0xfffdfaf2, 0xff456444, progress));
            canvas.drawRoundRect(box, radius, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * d);
            paint.setColor(ColorUtils.blendARGB(0xffb4bca9, 0xff385438, progress));
            canvas.drawRoundRect(box, radius, radius, paint);

            if (progress > 0.05f) {
                fullCheck.reset();
                fullCheck.moveTo(box.left + 5.5f * d, cy + 0.3f * d);
                fullCheck.lineTo(box.left + 9.2f * d, cy + 4.0f * d);
                fullCheck.lineTo(box.left + 15.6f * d, cy - 3.2f * d);
                measure.setPath(fullCheck, false);
                partialCheck.reset();
                measure.getSegment(0f, measure.getLength() * progress, partialCheck, true);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xfffffcf3);
                paint.setStrokeWidth(2.0f * d);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                canvas.drawPath(partialCheck, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
            }
        }
    }

    private static final class StrikeTitleView extends androidx.appcompat.widget.AppCompatTextView {
        private final Paint strikePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float strikeProgress;

        StrikeTitleView(Context context, String text, boolean initialChecked) {
            super(context);
            float d = context.getResources().getDisplayMetrics().density;
            this.strikeProgress = initialChecked ? 1f : 0f;
            setText(text);
            setTextSize(15);
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            setIncludeFontPadding(false);
            setTextColor(ColorUtils.blendARGB(0xff243624, 0xff8a9285, strikeProgress));
            strikePaint.setColor(0xff8a9285);
            strikePaint.setStrokeWidth(1.4f * d);
            strikePaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setStrikeProgress(float p) {
            this.strikeProgress = p;
            setTextColor(ColorUtils.blendARGB(0xff243624, 0xff8a9285, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (strikeProgress > 0.02f && getLayout() != null && getLayout().getLineCount() > 0) {
                float lineRight = getLayout().getLineRight(0);
                float cy = getLayout().getLineTop(0) + (getLayout().getLineBottom(0) - getLayout().getLineTop(0)) * 0.52f;
                canvas.drawLine(0f, cy, lineRight * strikeProgress, cy, strikePaint);
            }
        }
    }
}

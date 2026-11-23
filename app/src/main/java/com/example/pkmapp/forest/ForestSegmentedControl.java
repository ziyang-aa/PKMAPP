package com.example.pkmapp.forest;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Motion-Primitives-inspired segmented control with a spring-eased sliding cream paper pill. */
public final class ForestSegmentedControl extends LinearLayout {
    private static final PathInterpolator SPRING_EASE = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private final Paint trackFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final List<TextView> items = new ArrayList<>();
    private int selectedIndex;
    private boolean selectionCleared;
    private float indicatorLeft = -1f;
    private float indicatorRight = -1f;
    private ValueAnimator animator;

    public ForestSegmentedControl(
        Context context,
        String[] labels,
        int[] ids,
        int initialSelected,
        IntConsumer onSelect
    ) {
        super(context);
        this.d = context.getResources().getDisplayMetrics().density;
        this.selectedIndex = Math.max(0, Math.min(initialSelected, labels.length - 1));
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        int pad = Math.round(4 * d);
        setPadding(pad, pad, pad, pad);

        trackFill.setStyle(Paint.Style.FILL);
        trackFill.setColor(0xffeae5d9);
        trackStroke.setStyle(Paint.Style.STROKE);
        trackStroke.setStrokeWidth(0f);
        trackStroke.setColor(0x00000000);

        pillShadow.setStyle(Paint.Style.FILL);
        pillShadow.setColor(0x12243624);
        pillFill.setStyle(Paint.Style.FILL);
        pillFill.setColor(0xfffffefa);
        pillStroke.setStyle(Paint.Style.STROKE);
        pillStroke.setStrokeWidth(Math.max(1f, 0.75f * d));
        pillStroke.setColor(0x145c4938);

        for (int i = 0; i < labels.length; i++) {
            int idx = i;
            TextView tab = new TextView(context);
            if (ids != null && i < ids.length && ids[i] != NO_ID) {
                tab.setId(ids[i]);
            }
            tab.setText(labels[i]);
            tab.setTextSize(12);
            tab.setGravity(Gravity.CENTER);
            tab.setIncludeFontPadding(false);
            tab.setMinHeight(Math.round(36 * d));
            tab.setMinimumHeight(Math.round(36 * d));
            tab.setFocusable(true);
            tab.setOnClickListener(v -> {
                if (!selectionCleared && selectedIndex == idx) return;
                selectionCleared = false;
                select(idx, true);
                onSelect.accept(idx);
            });
            items.add(tab);
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart(Math.round(2 * d));
            addView(tab, lp);
        }
        syncTextStyles();
    }

    public void setTabLabels(CharSequence[] labels) {
        if (labels == null) return;
        int n = Math.min(labels.length, items.size());
        for (int i = 0; i < n; i++) {
            items.get(i).setText(labels[i]);
        }
    }

    public void setSelectionCleared(boolean cleared) {
        if (this.selectionCleared == cleared) return;
        this.selectionCleared = cleared;
        syncTextStyles();
        invalidate();
    }

    public void select(int index, boolean animate) {
        if (index < 0 || index >= items.size()) return;
        selectionCleared = false;
        selectedIndex = index;
        syncTextStyles();
        if (getWidth() == 0 || items.get(index).getWidth() == 0) {
            indicatorLeft = -1f;
            indicatorRight = -1f;
            invalidate();
            return;
        }
        TextView target = items.get(index);
        float toLeft = target.getLeft();
        float toRight = target.getRight();
        if (!animate || indicatorLeft < 0 || !ForestMotion.enabled()) {
            indicatorLeft = toLeft;
            indicatorRight = toRight;
            invalidate();
            return;
        }
        if (animator != null) animator.cancel();
        float fromLeft = indicatorLeft;
        float fromRight = indicatorRight;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(240);
        animator.setInterpolator(SPRING_EASE);
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            indicatorLeft = fromLeft + (toLeft - fromLeft) * f;
            indicatorRight = fromRight + (toRight - fromRight) * f;
            invalidate();
        });
        animator.start();
    }

    private void syncTextStyles() {
        for (int i = 0; i < items.size(); i++) {
            boolean active = !selectionCleared && i == selectedIndex;
            TextView tv = items.get(i);
            tv.setSelected(active);
            tv.setTextColor(active ? 0xff243624 : 0xff6b7568);
            tv.setTypeface(Typeface.create(
                active ? "sans-serif-medium" : "sans-serif",
                active ? Typeface.BOLD : Typeface.NORMAL
            ));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (selectedIndex >= 0 && selectedIndex < items.size() && (indicatorLeft < 0 || changed)) {
            TextView target = items.get(selectedIndex);
            indicatorLeft = target.getLeft();
            indicatorRight = target.getRight();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        RectF track = new RectF(d * 0.5f, d * 0.5f, w - d * 0.5f, h - d * 0.5f);
        float trackR = 12 * d;
        canvas.drawRoundRect(track, trackR, trackR, trackFill);
        canvas.drawRoundRect(track, trackR, trackR, trackStroke);

        if (!selectionCleared && indicatorLeft >= 0 && indicatorRight > indicatorLeft) {
            float top = getPaddingTop();
            float bottom = h - getPaddingBottom();
            float pillR = 9 * d;
            RectF shadow = new RectF(indicatorLeft, top + 1.5f * d, indicatorRight, bottom + 1.5f * d);
            canvas.drawRoundRect(shadow, pillR, pillR, pillShadow);
            RectF pill = new RectF(indicatorLeft, top, indicatorRight, bottom);
            canvas.drawRoundRect(pill, pillR, pillR, pillFill);
            canvas.drawRoundRect(pill, pillR, pillR, pillStroke);
        }
        super.onDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}

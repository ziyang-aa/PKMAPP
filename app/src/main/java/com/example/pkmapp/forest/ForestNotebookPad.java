package com.example.pkmapp.forest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Handcrafted draft-pad / lined notebook surface for standalone To-Do lists.
 * Renders stacked paper sheets, top notebook punch-holes + washi tape accent,
 * a left vertical margin rule separating checkboxes from text, and ruled paper lines.
 */
public final class ForestNotebookPad extends LinearLayout {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marginPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public ForestNotebookPad(Context context) {
        super(context);
        this.d = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        setClipToPadding(false);
        setMinimumHeight(Math.round(280 * d));
        setPadding(
            Math.round(8 * d),
            Math.round(34 * d),
            Math.round(14 * d),
            Math.round(22 * d)
        );

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(d);

        rulePaint.setStyle(Paint.Style.STROKE);
        rulePaint.setStrokeWidth(d);
        rulePaint.setColor(0xffeae5d5);

        marginPaint.setStyle(Paint.Style.STROKE);
        marginPaint.setStrokeWidth(1.1f * d);
        marginPaint.setColor(0x36b87760);

        tapePaint.setStyle(Paint.Style.FILL);
        tapePaint.setColor(0x4c96b392);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float top = 6 * d;
        float mainBottom = h - 9 * d;
        float radius = 14 * d;

        // 1. Stacked physical paper sheets peeking below the main pad
        RectF sheet3 = new RectF(10 * d, top + 6 * d, w - 10 * d, mainBottom + 7 * d);
        fillPaint.setColor(0xffefece0);
        strokePaint.setColor(0xffd9d4c2);
        canvas.drawRoundRect(sheet3, radius, radius, fillPaint);
        canvas.drawRoundRect(sheet3, radius, radius, strokePaint);

        RectF sheet2 = new RectF(5 * d, top + 3 * d, w - 5 * d, mainBottom + 3.5f * d);
        fillPaint.setColor(0xfff5f2e6);
        strokePaint.setColor(0xffddd8c6);
        canvas.drawRoundRect(sheet2, radius, radius, fillPaint);
        canvas.drawRoundRect(sheet2, radius, radius, strokePaint);

        // 2. Main warm cream draft-paper sheet
        RectF mainSheet = new RectF(0.5f * d, top, w - 0.5f * d, mainBottom);
        fillPaint.setColor(0xfffbf8ee);
        strokePaint.setColor(0xffd7d2be);
        canvas.drawRoundRect(mainSheet, radius, radius, fillPaint);
        canvas.drawRoundRect(mainSheet, radius, radius, strokePaint);

        // 3. Top binder punch holes & subtle binding rings
        int holeCount = 7;
        float holeY = top + 14 * d;
        float holeR = 3.6f * d;
        float spanLeft = 28 * d;
        float spanRight = w - 28 * d;
        float step = (spanRight - spanLeft) / (holeCount - 1);
        for (int i = 0; i < holeCount; i++) {
            float hx = spanLeft + i * step;
            fillPaint.setColor(0xffe7e2d1);
            canvas.drawCircle(hx, holeY, holeR, fillPaint);
            strokePaint.setColor(0xffccc6b2);
            canvas.drawCircle(hx, holeY, holeR, strokePaint);

            // Subtle binding notch from top edge into each punch hole
            strokePaint.setColor(0xffc3bda8);
            strokePaint.setStrokeWidth(1.6f * d);
            canvas.drawLine(hx, top - 2 * d, hx, holeY - holeR + 0.8f * d, strokePaint);
            strokePaint.setStrokeWidth(d);
        }

        // Top header separator below punch holes
        float headerLineY = top + 26 * d;
        canvas.drawLine(12 * d, headerLineY, w - 12 * d, headerLineY, rulePaint);

        // 4. Vertical notebook margin line separating left checkboxes from task writing area
        float marginX = 50 * d;
        canvas.drawLine(marginX, headerLineY, marginX, mainBottom - 6 * d, marginPaint);

        // 5. Horizontal ruled notebook lines across the paper
        float lastLineY = headerLineY;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != VISIBLE) continue;
            float y = child.getBottom();
            if (y > headerLineY + 6 * d && y < mainBottom - 8 * d) {
                canvas.drawLine(10 * d, y, w - 10 * d, y, rulePaint);
                lastLineY = y;
            }
        }
        // Fill remaining space on the draft pad with crisp empty ruled lines
        float linePitch = 46 * d;
        float nextY = lastLineY + linePitch;
        while (nextY < mainBottom - 10 * d) {
            canvas.drawLine(10 * d, nextY, w - 10 * d, nextY, rulePaint);
            nextY += linePitch;
        }

        // 6. Subtle semi-transparent moss washi tape at the top-right corner
        canvas.save();
        canvas.translate(w - 58 * d, top - 2 * d);
        canvas.rotate(4f);
        RectF tape = new RectF(-24 * d, -4 * d, 24 * d, 7 * d);
        canvas.drawRoundRect(tape, 2 * d, 2 * d, tapePaint);
        canvas.restore();

        super.onDraw(canvas);
    }
}

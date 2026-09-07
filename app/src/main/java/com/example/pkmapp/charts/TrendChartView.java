package com.example.pkmapp.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public final class TrendChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long[] values = {1, 1};

    public TrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeWidth(6f);
        linePaint.setColor(0xFF4E8146); pointPaint.setColor(0xFF4E8146);
    }

    public void setValues(long[] values, boolean expense) {
        this.values = values.length == 0 ? new long[]{1, 1} : values.clone();
        int color = expense ? 0xFFC75B4B : 0xFF4E8146;
        linePaint.setColor(color); pointPaint.setColor(color); invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight(); if (width == 0 || height == 0) return;
        long max = 1; for (long value : values) max = Math.max(max, value);
        float left = 12f, top = 16f, bottom = height - 24f;
        Path path = new Path();
        for (int i = 0; i < values.length; i++) {
            float x = left + (width - left * 2f) * i / Math.max(1, values.length - 1);
            float y = bottom - (bottom - top) * values[i] / (float) max;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            canvas.drawCircle(x, y, 7f, pointPaint);
        }
        canvas.drawPath(path, linePaint);
    }
}

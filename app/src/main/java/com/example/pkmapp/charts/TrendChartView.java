package com.example.pkmapp.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.pkmapp.R;
import com.example.pkmapp.record.MoneyParser;

public final class TrendChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipDetailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long[] values = {1, 1};
    private int[] counts = {0, 0};
    private String[] pointLabels = {"", ""};
    private String startLabel = "01日";
    private String middleLabel = "15日";
    private String endLabel = "30日";
    private String tooltipType = "支出";
    private String peakLabel = "最高消费 ¥0.00";
    private float[] pointXs = new float[0];
    private float[] pointYs = new float[0];
    private int selectedIndex = -1;

    public TrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setColor(Color.rgb(227, 216, 192));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(3), dp(4)}, 0));
        linePaint.setColor(context.getResources().getColor(R.color.forest_green));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(3));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        dotFillPaint.setColor(context.getResources().getColor(R.color.paper_card));
        dotFillPaint.setStyle(Paint.Style.FILL);
        dotStrokePaint.setColor(context.getResources().getColor(R.color.warm_orange));
        dotStrokePaint.setStyle(Paint.Style.STROKE);
        dotStrokePaint.setStrokeWidth(dp(2));
        labelPaint.setColor(context.getResources().getColor(R.color.wood_brown));
        labelPaint.setTextSize(sp(10));
        tooltipFillPaint.setColor(context.getResources().getColor(R.color.paper_card));
        tooltipFillPaint.setStyle(Paint.Style.FILL);
        tooltipStrokePaint.setColor(context.getResources().getColor(R.color.wood_brown));
        tooltipStrokePaint.setStyle(Paint.Style.STROKE);
        tooltipStrokePaint.setStrokeWidth(dp(1));
        tooltipTitlePaint.setColor(context.getResources().getColor(R.color.wood_brown));
        tooltipTitlePaint.setTextSize(sp(10));
        tooltipTitlePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tooltipDetailPaint.setColor(context.getResources().getColor(R.color.ink));
        tooltipDetailPaint.setTextSize(sp(11));
        setClickable(true);
    }

    public void setValues(long[] values, int[] counts, String[] pointLabels,
            String startLabel, String middleLabel, String endLabel, String tooltipType,
            String peakLabel) {
        this.values = values.length == 0 ? new long[]{1, 1} : values.clone();
        this.counts = counts.length == values.length ? counts.clone() : new int[this.values.length];
        this.pointLabels = pointLabels.length == values.length
                ? pointLabels.clone() : new String[this.values.length];
        this.startLabel = startLabel;
        this.middleLabel = middleLabel;
        this.endLabel = endLabel;
        this.tooltipType = tooltipType;
        this.peakLabel = peakLabel;
        this.selectedIndex = -1;
        linePaint.setColor(getResources().getColor(R.color.forest_green));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float left = dp(8);
        float right = width - dp(8);
        float top = dp(26);
        float bottom = height - dp(30);
        long max = 1;
        for (long value : values) max = Math.max(max, value);

        for (int i = 0; i < 3; i++) {
            float y = top + (bottom - top) * i / 2f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        canvas.drawText(peakLabel, left, top - dp(7), labelPaint);

        float[] xs = new float[values.length];
        float[] ys = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            xs[i] = left + (right - left) * i / Math.max(1, values.length - 1);
            ys[i] = bottom - (bottom - top) * values[i] / (float) max;
        }
        pointXs = xs;
        pointYs = ys;

        Path line = new Path();
        line.moveTo(xs[0], ys[0]);
        for (int i = 1; i < values.length; i++) {
            float midX = (xs[i - 1] + xs[i]) / 2f;
            line.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i]);
        }
        Path area = new Path(line);
        area.lineTo(xs[xs.length - 1], bottom);
        area.lineTo(xs[0], bottom);
        area.close();
        areaPaint.setShader(new LinearGradient(0, top, 0, bottom,
                0x99A8D58C, 0x00A8D58C, Shader.TileMode.CLAMP));
        canvas.drawPath(area, areaPaint);
        canvas.drawPath(line, linePaint);

        for (int i = 0; i < xs.length; i++) {
            if (values[i] == 0L) {
                continue;
            }
            canvas.drawCircle(xs[i], ys[i], dp(4), dotFillPaint);
            canvas.drawCircle(xs[i], ys[i], dp(4), dotStrokePaint);
        }

        if (selectedIndex >= 0 && selectedIndex < xs.length && values[selectedIndex] > 0L) {
            canvas.drawCircle(xs[selectedIndex], ys[selectedIndex], dp(7), dotStrokePaint);
            drawTooltip(canvas, selectedIndex, xs[selectedIndex], ys[selectedIndex], left, right, top);
        }

        canvas.drawText(startLabel, left, height - dp(5), labelPaint);
        float middleLabelWidth = labelPaint.measureText(middleLabel);
        canvas.drawText(middleLabel, (width - middleLabelWidth) / 2f, height - dp(5), labelPaint);
        float endLabelWidth = labelPaint.measureText(endLabel);
        canvas.drawText(endLabel, right - endLabelWidth, height - dp(5), labelPaint);
    }

    private void drawTooltip(Canvas canvas, int index, float pointX, float pointY,
            float plotLeft, float plotRight, float plotTop) {
        String title = pointLabels[index] == null ? "" : pointLabels[index];
        String detail = MoneyParser.formatCents(values[index]) + " · " + counts[index] + " 笔" + tooltipType;
        float width = Math.max(dp(132), Math.max(tooltipTitlePaint.measureText(title),
                tooltipDetailPaint.measureText(detail)) + dp(20));
        float height = dp(50);
        float left = Math.max(plotLeft, Math.min(pointX - width / 2f, plotRight - width));
        float top = pointY - height - dp(12);
        if (top < plotTop) {
            top = pointY + dp(12);
        }
        RectF bubble = new RectF(left, top, left + width, top + height);
        canvas.drawRoundRect(bubble, dp(12), dp(12), tooltipFillPaint);
        canvas.drawRoundRect(bubble, dp(12), dp(12), tooltipStrokePaint);
        Paint.FontMetrics titleMetrics = tooltipTitlePaint.getFontMetrics();
        Paint.FontMetrics detailMetrics = tooltipDetailPaint.getFontMetrics();
        canvas.drawText(title, left + dp(10), top + dp(16) - titleMetrics.ascent / 4f, tooltipTitlePaint);
        canvas.drawText(detail, left + dp(10), top + dp(36) - detailMetrics.ascent / 4f,
                tooltipDetailPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pointXs.length == 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                int hit = nearestPoint(event.getX(), event.getY());
                if (hit < 0) {
                    return false;
                }
                selectedIndex = hit;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    private int nearestPoint(float x, float y) {
        int nearest = -1;
        float nearestDistance = dp(28);
        for (int i = 0; i < pointXs.length; i++) {
            if (values[i] <= 0L) {
                continue;
            }
            float dx = pointXs[i] - x;
            float dy = pointYs[i] - y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}

package com.example.pkmapp.study;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** 6-week (Monday–Sunday) warm woodland focus heatmap with interactive day selection. */
public final class StudyHeatmapView extends View {

    private static final int WEEKS = 6;
    private static final int DAYS = 7;
    private static final int[] LEVELS = {
        0xffefece2, // 0 min
        0xffd8e7cf, // 1..29 min
        0xffa5c69a, // 30..89 min
        0xff689260, // 90..179 min
        0xff335837  // 180+ min
    };
    private static final int FUTURE_FILL = 0xfff6f3eb;
    private static final String[] WEEK_LABELS = { "一", "二", "三", "四", "五", "六", "日" };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cellRect = new RectF();
    private final float d;

    private final String[] dayKeys = new String[WEEKS * DAYS];
    private final String[] dayLabels = new String[WEEKS * DAYS];
    private final long[] dayMillis = new long[WEEKS * DAYS];
    private final boolean[] isFuture = new boolean[WEEKS * DAYS];
    private int todayIndex = -1;
    private int selectedIndex = -1;
    private Consumer<String> onDaySelected;

    // Cached layout metrics for hit-testing
    private float gridLeft, gridTop, cellSize, cellGap;

    public StudyHeatmapView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        textPaint.setColor(0xff7d7463);
        textPaint.setTextSize(10.5f * d);
        textPaint.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
    }

    public void setData(List<StudyNotebook.Record> records, Consumer<String> onDaySelected) {
        this.onDaySelected = onDaySelected;
        Map<String, Long> totals = new HashMap<>();
        if (records != null) {
            for (StudyNotebook.Record record : records) {
                String key = StudyNotebook.dayKey(record.completedAt);
                totals.merge(key, record.durationMillis, Long::sum);
            }
        }
        long now = System.currentTimeMillis();
        String todayKey = StudyNotebook.dayKey(now);
        Calendar todayCal = StudyFormat.day(todayKey);

        // Find Monday of current week, then go back (WEEKS - 1) weeks
        int dow = todayCal.get(Calendar.DAY_OF_WEEK);
        int offsetFromMonday = (dow == Calendar.SUNDAY) ? 6 : (dow - Calendar.MONDAY);
        Calendar start = (Calendar) todayCal.clone();
        start.add(Calendar.DATE, -offsetFromMonday - (WEEKS - 1) * 7);

        SimpleDateFormat labelFmt = new SimpleDateFormat("M月d日", Locale.CHINA);
        todayIndex = -1;
        selectedIndex = -1;

        for (int i = 0; i < WEEKS * DAYS; i++) {
            Calendar cur = (Calendar) start.clone();
            cur.add(Calendar.DATE, i);
            String key = StudyNotebook.dayKey(cur.getTimeInMillis());
            dayKeys[i] = key;
            dayLabels[i] = labelFmt.format(cur.getTime());
            dayMillis[i] = totals.getOrDefault(key, 0L);
            isFuture[i] = key.compareTo(todayKey) > 0;
            if (key.equals(todayKey)) {
                todayIndex = i;
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float padH = 2f * d;
        float gap = 7f * d;
        float usableW = Math.max(100f, width - padH * 2f);
        float cSize = Math.min(39f * d, (usableW - gap * (DAYS - 1)) / DAYS);
        float headerH = 22f * d;
        float gridH = WEEKS * cSize + (WEEKS - 1) * gap;
        float legendH = 24f * d;
        int height = Math.round(headerH + gridH + legendH + 8f * d);
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        if (w <= 0) return;

        cellGap = 7f * d;
        float usableW = w - 4f * d;
        cellSize = Math.min(39f * d, (usableW - cellGap * (DAYS - 1)) / DAYS);
        float gridW = DAYS * cellSize + (DAYS - 1) * cellGap;
        gridLeft = (w - gridW) / 2f;
        gridTop = 22f * d;
        float radius = 7.5f * d;

        // Weekday column headers
        textPaint.setTextSize(10.5f * d);
        textPaint.setColor(0xff8c8372);
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int col = 0; col < DAYS; col++) {
            float cx = gridLeft + col * (cellSize + cellGap) + cellSize / 2f;
            canvas.drawText(WEEK_LABELS[col], cx, 13f * d, textPaint);
        }

        // Heatmap cells (row = week 0..WEEKS-1, col = Mon..Sun 0..6)
        for (int row = 0; row < WEEKS; row++) {
            for (int col = 0; col < DAYS; col++) {
                int idx = row * DAYS + col;
                float left = gridLeft + col * (cellSize + cellGap);
                float top = gridTop + row * (cellSize + cellGap);
                cellRect.set(left, top, left + cellSize, top + cellSize);

                paint.setStyle(Paint.Style.FILL);
                if (isFuture[idx]) {
                    paint.setColor(FUTURE_FILL);
                } else {
                    paint.setColor(levelColor(dayMillis[idx]));
                }
                canvas.drawRoundRect(cellRect, radius, radius, paint);

                if (idx == selectedIndex) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.8f * d);
                    paint.setColor(0xff2a4d2f);
                    canvas.drawRoundRect(cellRect, radius, radius, paint);
                } else if (idx == todayIndex) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.25f * d);
                    paint.setColor(0x99345837);
                    canvas.drawRoundRect(cellRect, radius, radius, paint);
                }
            }
        }

        // Bottom legend (少 □□□□□ 多)
        float legendY = gridTop + WEEKS * cellSize + (WEEKS - 1) * cellGap + 18f * d;
        float box = 10f * d;
        float boxGap = 4f * d;
        float rightEdge = gridLeft + gridW;

        textPaint.setTextSize(10f * d);
        textPaint.setColor(0xff8c8372);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("多", rightEdge, legendY, textPaint);

        float curX = rightEdge - 16f * d;
        paint.setStyle(Paint.Style.FILL);
        for (int i = LEVELS.length - 1; i >= 0; i--) {
            curX -= box;
            cellRect.set(curX, legendY - box + 1.5f * d, curX + box, legendY + 1.5f * d);
            paint.setColor(LEVELS[i]);
            canvas.drawRoundRect(cellRect, 3f * d, 3f * d, paint);
            curX -= boxGap;
        }
        canvas.drawText("少", curX - 2f * d, legendY, textPaint);

        // Left hint
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("近 6 周每日专注", gridLeft, legendY, textPaint);
    }

    private int levelColor(long millis) {
        long min = millis / 60_000L;
        if (min <= 0) return LEVELS[0];
        if (min < 30) return LEVELS[1];
        if (min < 90) return LEVELS[2];
        if (min < 180) return LEVELS[3];
        return LEVELS[4];
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX() - gridLeft;
            float y = event.getY() - gridTop;
            if (x >= 0 && y >= 0 && cellSize > 0) {
                float step = cellSize + cellGap;
                int col = (int) (x / step);
                int row = (int) (y / step);
                if (col >= 0 && col < DAYS && row >= 0 && row < WEEKS) {
                    int idx = row * DAYS + col;
                    if (!isFuture[idx]) {
                        selectedIndex = idx;
                        invalidate();
                        if (onDaySelected != null) {
                            String detail = dayMillis[idx] > 0
                                ? dayLabels[idx] + " · 专注 " + StudyFormat.duration(dayMillis[idx])
                                : dayLabels[idx] + " · 当日未专注";
                            onDaySelected.accept(detail);
                        }
                        performClick();
                        return true;
                    }
                }
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}

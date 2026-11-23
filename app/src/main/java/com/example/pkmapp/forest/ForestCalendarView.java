package com.example.pkmapp.forest;

import static com.example.pkmapp.forest.ForestUi.*;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handcrafted quiet-luxury porcelain calendar component for StudySheet date picking and To-Do filtering.
 * Renders directly onto the sheet canvas (zero nested card borders) with Chinese month headers,
 * Monday-first weekday strip, pine selection circle, and subtle botanical task dots.
 */
public final class ForestCalendarView extends LinearLayout {
    public static final int MARKER_PENDING = 1;
    public static final int MARKER_DONE = 2;
    public static final int MARKER_OVERDUE = 4;

    private static final String[] WEEKDAYS = {"一", "二", "三", "四", "五", "六", "日"};

    private final TextView monthTitle;
    private final TextView jumpTodayLink;
    private final GridCanvas grid;
    private final Calendar visibleMonth;
    private final Calendar selected;
    private final Calendar today;
    private final Consumer<String> onDateChanged;

    public ForestCalendarView(
        Context context,
        String initialDayKey,
        Map<String, Integer> dayMarkers,
        Consumer<String> onDateChanged
    ) {
        super(context);
        this.onDateChanged = onDateChanged;
        setOrientation(VERTICAL);

        today = Calendar.getInstance();
        normalizeMidnight(today);

        selected = parseDayOrToday(initialDayKey);
        visibleMonth = (Calendar) selected.clone();
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);

        // 1. Editorial Month Navigation Header
        LinearLayout header = row(context);
        header.setPadding(dp(context, 2), dp(context, 4), dp(context, 2), dp(context, 10));

        monthTitle = heading(context, "", 18);
        monthTitle.setTypeface(Typeface.create("serif", Typeface.BOLD));
        monthTitle.setTextColor(0xff243624);
        monthTitle.setFontFeatureSettings("tnum");
        header.addView(monthTitle, new LayoutParams(0, -2, 1));

        jumpTodayLink = text(context, "回到今天", 12, DEEP);
        jumpTodayLink.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        jumpTodayLink.setGravity(Gravity.CENTER);
        jumpTodayLink.setMinimumHeight(dp(context, 32));
        jumpTodayLink.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        jumpTodayLink.setBackground(ripple(context, rounded(context, 0xffeef4ea, 0, 999), false));
        jumpTodayLink.setOnClickListener(v -> selectCalendar((Calendar) today.clone()));
        ForestMotion.bind(jumpTodayLink);
        LayoutParams todayLp = new LayoutParams(-2, -2);
        todayLp.setMarginEnd(dp(context, 8));
        header.addView(jumpTodayLink, todayLp);

        TextView prevBtn = navCircleButton(context, "‹", "上个月", () -> {
            visibleMonth.add(Calendar.MONTH, -1);
            syncHeaderAndGrid();
        });
        LayoutParams prevLp = new LayoutParams(dp(context, 34), dp(context, 34));
        prevLp.setMarginEnd(dp(context, 6));
        header.addView(prevBtn, prevLp);

        TextView nextBtn = navCircleButton(context, "›", "下个月", () -> {
            visibleMonth.add(Calendar.MONTH, 1);
            syncHeaderAndGrid();
        });
        header.addView(nextBtn, new LayoutParams(dp(context, 34), dp(context, 34)));
        addView(header, new LayoutParams(-1, -2));

        // 2. Monday-First Chinese Weekday Strip
        LinearLayout weekRow = row(context);
        weekRow.setPadding(0, dp(context, 2), 0, dp(context, 6));
        for (String day : WEEKDAYS) {
            TextView tv = text(context, day, 11, 0xff8f8675);
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            weekRow.addView(tv, new LayoutParams(0, -2, 1));
        }
        addView(weekRow, new LayoutParams(-1, -2));

        // 3. Direct Canvas 6x7 Date Grid
        grid = new GridCanvas(context, dayMarkers == null ? Collections.emptyMap() : dayMarkers);
        addView(grid, new LayoutParams(-1, dp(context, 292)));

        syncHeaderAndGrid();
    }

    public String selectedDayKey() {
        return formatKey(selected);
    }

    public static String formatKey(Calendar cal) {
        return String.format(
            Locale.ROOT,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        );
    }

    public static String formatChineseFull(String dayKey) {
        Calendar cal = parseDayOrToday(dayKey);
        String[] weeks = {"", "周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return String.format(
            Locale.CHINA,
            "%d年%d月%d日 · %s",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            weeks[cal.get(Calendar.DAY_OF_WEEK)]
        );
    }

    private TextView navCircleButton(Context context, String glyph, String desc, Runnable click) {
        TextView btn = text(context, glyph, 19, 0xff4c594a);
        btn.setGravity(Gravity.CENTER);
        btn.setContentDescription(desc);
        btn.setBackground(ripple(context, rounded(context, 0x125c4938, 0, 999), false));
        btn.setOnClickListener(v -> click.run());
        btn.setFocusable(true);
        ForestMotion.bind(btn);
        return btn;
    }

    private void selectCalendar(Calendar target) {
        normalizeMidnight(target);
        selected.setTimeInMillis(target.getTimeInMillis());
        visibleMonth.setTimeInMillis(target.getTimeInMillis());
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        syncHeaderAndGrid();
        if (onDateChanged != null) onDateChanged.accept(selectedDayKey());
    }

    private void syncHeaderAndGrid() {
        monthTitle.setText(String.format(
            Locale.CHINA,
            "%d年%d月",
            visibleMonth.get(Calendar.YEAR),
            visibleMonth.get(Calendar.MONTH) + 1
        ));
        boolean showingToday = sameDay(selected, today)
            && visibleMonth.get(Calendar.YEAR) == today.get(Calendar.YEAR)
            && visibleMonth.get(Calendar.MONTH) == today.get(Calendar.MONTH);
        jumpTodayLink.setVisibility(showingToday ? View.GONE : View.VISIBLE);
        grid.rebuildCells();
    }

    private static Calendar parseDayOrToday(String dayKey) {
        Calendar cal = Calendar.getInstance();
        normalizeMidnight(cal);
        if (dayKey != null && dayKey.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            String[] p = dayKey.split("-");
            cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            normalizeMidnight(cal);
        }
        return cal;
    }

    private static void normalizeMidnight(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private final class GridCanvas extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private final Map<String, Integer> markers;
        private final Calendar[] cells = new Calendar[42];
        private final String[] cellKeys = new String[42];
        private int rowCount = 6;
        private int pressedIndex = -1;

        GridCanvas(Context context, Map<String, Integer> markers) {
            super(context);
            this.d = context.getResources().getDisplayMetrics().density;
            this.markers = markers;

            fillPaint.setStyle(Paint.Style.FILL);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(1.3f * d);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFontFeatureSettings("tnum");
            setClickable(true);
        }

        void rebuildCells() {
            Calendar cursor = (Calendar) visibleMonth.clone();
            cursor.set(Calendar.DAY_OF_MONTH, 1);
            int mondayOffset = (cursor.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            int daysInMonth = cursor.getActualMaximum(Calendar.DAY_OF_MONTH);
            rowCount = (mondayOffset + daysInMonth) <= 35 ? 5 : 6;
            cursor.add(Calendar.DAY_OF_MONTH, -mondayOffset);
            for (int i = 0; i < 42; i++) {
                Calendar c = (Calendar) cursor.clone();
                cells[i] = c;
                cellKeys[i] = formatKey(c);
                cursor.add(Calendar.DAY_OF_MONTH, 1);
            }
            LayoutParams lp = (LayoutParams) getLayoutParams();
            int targetH = Math.round((rowCount * 48f + 4f) * d);
            if (lp != null && lp.height != targetH) {
                lp.height = targetH;
                setLayoutParams(lp);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0 || cells[0] == null) return;

            float colW = w / 7f;
            float rowH = h / rowCount;
            float circleR = Math.min(colW, rowH) * 0.35f;
            int visYear = visibleMonth.get(Calendar.YEAR);
            int visMonth = visibleMonth.get(Calendar.MONTH);
            int totalCells = rowCount * 7;

            for (int i = 0; i < totalCells; i++) {
                Calendar cell = cells[i];
                int col = i % 7;
                int row = i / 7;
                float cx = col * colW + colW / 2f;
                float cy = row * rowH + rowH * 0.43f;

                boolean inMonth = cell.get(Calendar.YEAR) == visYear && cell.get(Calendar.MONTH) == visMonth;
                boolean isSelected = sameDay(cell, selected);
                boolean isToday = sameDay(cell, today);

                if (isSelected) {
                    fillPaint.setColor(0xff456444);
                    canvas.drawCircle(cx, cy, circleR, fillPaint);
                } else if (i == pressedIndex) {
                    fillPaint.setColor(0x16456444);
                    canvas.drawCircle(cx, cy, circleR, fillPaint);
                } else if (isToday) {
                    fillPaint.setColor(0xffeef4ea);
                    canvas.drawCircle(cx, cy, circleR, fillPaint);
                    ringPaint.setColor(0x66456444);
                    canvas.drawCircle(cx, cy, circleR - 0.6f * d, ringPaint);
                }

                textPaint.setTextSize(14f * d);
                textPaint.setTypeface(Typeface.create(
                    (isSelected || isToday) ? "sans-serif-medium" : "sans-serif",
                    (isSelected || isToday) ? Typeface.BOLD : Typeface.NORMAL
                ));
                if (isSelected) {
                    textPaint.setColor(0xfffffcf3);
                } else if (!inMonth) {
                    textPaint.setColor(0xffc7c1b4);
                } else if (isToday) {
                    textPaint.setColor(0xff315e3a);
                } else {
                    textPaint.setColor(0xff243624);
                }
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float baseline = cy - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(String.valueOf(cell.get(Calendar.DAY_OF_MONTH)), cx, baseline, textPaint);

                // Botanical task dot markers cleanly tucked below the circle
                Integer maskObj = markers.get(cellKeys[i]);
                if (maskObj != null && maskObj != 0) {
                    int mask = maskObj;
                    int dotCount = 0;
                    int[] dotColors = new int[2];
                    if ((mask & MARKER_OVERDUE) != 0) {
                        dotColors[dotCount++] = 0xffb2553f;
                    } else if ((mask & MARKER_PENDING) != 0) {
                        dotColors[dotCount++] = 0xff456444;
                    }
                    if ((mask & MARKER_DONE) != 0 && dotCount < 2) {
                        dotColors[dotCount++] = 0xff9bb595;
                    }
                    float dotY = cy + circleR + 3.8f * d;
                    if (dotY > (row + 1) * rowH - 2f * d) {
                        dotY = (row + 1) * rowH - 2.2f * d;
                    }
                    float dotR = 2.1f * d;
                    float spacing = 6.2f * d;
                    float startX = cx - (dotCount - 1) * spacing / 2f;
                    for (int k = 0; k < dotCount; k++) {
                        fillPaint.setColor(dotColors[k]);
                        canvas.drawCircle(startX + k * spacing, dotY, dotR, fillPaint);
                    }
                }
            }
        }

        private int cellAt(float x, float y) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return -1;
            int col = (int) (x / (w / 7f));
            int row = (int) (y / (h / rowCount));
            if (col < 0 || col >= 7 || row < 0 || row >= rowCount) return -1;
            int idx = row * 7 + col;
            return idx < rowCount * 7 ? idx : -1;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pressedIndex = cellAt(event.getX(), event.getY());
                invalidate();
                return pressedIndex >= 0;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                int idx = cellAt(event.getX(), event.getY());
                if (idx != pressedIndex) {
                    pressedIndex = idx;
                    invalidate();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                int idx = cellAt(event.getX(), event.getY());
                pressedIndex = -1;
                if (idx >= 0 && cells[idx] != null) {
                    performClick();
                    selectCalendar((Calendar) cells[idx].clone());
                } else {
                    invalidate();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                pressedIndex = -1;
                invalidate();
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }
}

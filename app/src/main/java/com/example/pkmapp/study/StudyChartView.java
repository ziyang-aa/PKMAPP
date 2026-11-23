package com.example.pkmapp.study;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import com.example.pkmapp.forest.ForestUi;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StudyChartView extends View {

    public enum Kind {
        RING,
        BARS,
        TREND,
    }

    private Kind kind = Kind.RING;
    private List<StudyNotebook.Record> records = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<StudySubject, Long> times = new LinkedHashMap<>();
    private StudySubject selectedSubject;
    private float ringHeight = 252;
    private long start;
    private int days = 7;

    public StudyChartView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setClickable(true);
    }

    public void setData(List<StudyNotebook.Record> records, Kind kind, long start, int days) {
        this.records = new ArrayList<>(records);
        this.kind = kind;
        this.start = start;
        this.days = days;
        this.selectedSubject = null;
        Map<StudySubject, Long> durations = new LinkedHashMap<>();
        for (StudyNotebook.Record record : records)
            durations.merge(record.subject, record.durationMillis, Long::sum);
        List<Map.Entry<StudySubject, Long>> ranked = new ArrayList<>(durations.entrySet());
        ranked.sort((left, right) -> Long.compare(right.getValue(), left.getValue()));
        times.clear();
        for (Map.Entry<StudySubject, Long> entry : ranked) times.put(entry.getKey(), entry.getValue());
        int right = 0;
        long total = total();
        float angle = -90;
        for (long duration : times.values()) {
            float sweep = (360f * duration) / total;
            if (Math.cos(Math.toRadians(angle + sweep / 2)) >= 0) right++;
            angle += sweep;
        }
        ringHeight = Math.max(236, 48 + Math.max(right, times.size() - right) * 36f);
        StringBuilder description = new StringBuilder(kind == Kind.TREND ? "学习趋势，" : "各科目学习时长，");
        description.append("合计").append(StudyFormat.duration(total)).append("。");
        for (Map.Entry<StudySubject, Long> entry : times.entrySet())
            description
                .append(entry.getKey().title)
                .append(StudyFormat.duration(entry.getValue()))
                .append("。");
        setContentDescription(description);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(
            width,
            resolveSize(
                Math.round(
                    chartWidth(width) / 360f * (records.isEmpty() ? 0
                        : kind == Kind.BARS ? 14 + times.size() * 40
                        : kind == Kind.TREND ? 228 : ringHeight)
                ),
                heightSpec
            )
        );
    }

    private float chartWidth(int width) {
        float limit = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500, getResources().getDisplayMetrics());
        return Math.min(width, limit);
    }

    private void text(
        Canvas c,
        String value,
        float x,
        float y,
        float size,
        int color,
        Paint.Align align
    ) {
        text(c, value, x, y, size, color, align, Typeface.create("sans-serif", Typeface.NORMAL));
    }

    private void text(
        Canvas c,
        String value,
        float x,
        float y,
        float size,
        int color,
        Paint.Align align,
        Typeface typeface
    ) {
        paint.setShader(null);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(1);
        paint.setTextSize(size);
        paint.setTypeface(typeface);
        paint.setFontFeatureSettings("tnum");
        paint.setColor(color);
        paint.setTextAlign(align);
        c.drawText(value, x, y, paint);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        long total = total();
        if (getWidth() == 0 || total == 0) return;
        float width = chartWidth(getWidth());
        c.save();
        c.translate((getWidth() - width) / 2, 0);
        c.scale(width / 360f, width / 360f);
        if (kind == Kind.RING) drawRing(c, total);
        else if (kind == Kind.BARS) drawBars(c, total);
        else drawTrend(c);
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (kind == Kind.RING && event.getAction() == MotionEvent.ACTION_UP && getWidth() > 0) {
            long total = total();
            if (total > 0) {
                float width = chartWidth(getWidth());
                float scale = width / 360f;
                float lx = (event.getX() - (getWidth() - width) / 2f) / scale;
                float ly = event.getY() / scale;
                float cx = 180f;
                float cy = ringHeight / 2f - 2f;
                float dx = lx - cx;
                float dy = ly - cy;
                float dist = (float) Math.hypot(dx, dy);
                if (dist < 42f) {
                    if (selectedSubject != null) {
                        selectedSubject = null;
                        invalidate();
                        performClick();
                        return true;
                    }
                } else if (dist <= 86f) {
                    double deg = Math.toDegrees(Math.atan2(dy, dx));
                    float fromTop = (float) ((deg + 90 + 360) % 360);
                    float acc = 0f;
                    for (Map.Entry<StudySubject, Long> entry : times.entrySet()) {
                        float sweep = (360f * entry.getValue()) / total;
                        if (fromTop >= acc && fromTop <= acc + sweep) {
                            StudySubject tapped = entry.getKey();
                            selectedSubject = tapped.equals(selectedSubject) ? null : tapped;
                            invalidate();
                            performClick();
                            return true;
                        }
                        acc += sweep;
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

    private long total() {
        long total = 0;
        for (long value : times.values()) total += value;
        return total;
    }

    private void drawRing(Canvas c, long total) {
        float angle = -90;
        float center = ringHeight / 2 - 2;
        List<Label> labels = new ArrayList<>();
        int count = times.size();
        for (Map.Entry<StudySubject, Long> entry : times.entrySet()) {
            StudySubject subject = entry.getKey();
            long duration = entry.getValue();
            float sweep = (360f * duration) / total;
            double mid = Math.toRadians(angle + sweep / 2);
            boolean isSelected = subject.equals(selectedSubject);
            boolean dimmed = selectedSubject != null && !isSelected;

            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(isSelected ? 24f : 21f);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setColor(subject.color);
            paint.setAlpha(dimmed ? 90 : 255);
            float gap = count > 1 ? Math.min(1.4f, sweep * .08f) : 0f;
            c.drawArc(
                new RectF(122, center - 58, 238, center + 58),
                angle + gap / 2f,
                Math.max(0.5f, sweep - gap),
                false,
                paint
            );
            paint.setAlpha(255);
            labels.add(
                new Label(
                    subject,
                    duration,
                    180 + (float) Math.cos(mid) * 70,
                    center + (float) Math.sin(mid) * 70,
                    center + (float) Math.sin(mid) * 92,
                    Math.cos(mid) >= 0
                )
            );
            angle += sweep;
        }
        for (boolean right : new boolean[] { false, true }) {
            List<Label> side = new ArrayList<>();
            for (Label label : labels) if (label.right == right) side.add(label);
            side.sort((a, b) -> Float.compare(a.target, b.target));
            for (int i = 0; i < side.size(); i++) side.get(i).target = Math.max(
                side.get(i).target,
                i == 0 ? 22 : side.get(i - 1).target + 36
            );
            if (!side.isEmpty()) {
                side.get(side.size() - 1).target = Math.min(ringHeight - 24, side.get(side.size() - 1).target);
                for (int i = side.size() - 2; i >= 0; i--) side.get(i).target = Math.min(
                    side.get(i).target,
                    side.get(i + 1).target - 36
                );
            }
        }
        TextPaint measure = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        for (Label label : labels) {
            boolean isSelected = label.subject.equals(selectedSubject);
            boolean dimmed = selectedSubject != null && !isSelected;
            float elbow = label.right ? 255 : 105,
                end = label.right ? 262 : 98,
                x = label.right ? 266 : 94;
            paint.setPathEffect(null);
            paint.setColor(0x597b5c3b);
            paint.setAlpha(dimmed ? 45 : 95);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(.95f);
            Path path = new Path();
            path.moveTo(label.x, label.y);
            path.lineTo(elbow, label.y);
            path.lineTo(end, label.target);
            c.drawPath(path, paint);

            Paint.Align align = label.right ? Paint.Align.LEFT : Paint.Align.RIGHT;
            measure.setTextSize(11.2f);
            String labelText = TextUtils.ellipsize(label.subject.title, measure, 90, TextUtils.TruncateAt.END).toString();
            int nameColor = dimmed ? 0x707b5c3b : ForestUi.WOOD;
            int valColor = dimmed ? 0x70283f2c : ForestUi.DEEP;
            text(
                c,
                labelText,
                x,
                label.target - 3.5f,
                11.2f,
                nameColor,
                align,
                Typeface.create(isSelected ? "sans-serif-medium" : "sans-serif", isSelected ? Typeface.BOLD : Typeface.NORMAL)
            );
            String duration = StudyFormat.duration(label.duration);
            measure.setTextSize(10.5f);
            text(c, measure.measureText(duration) <= 90 ? duration : compact(label.duration),
                x, label.target + 10.5f, 10.5f, valColor, align);
        }

        if (selectedSubject != null && times.containsKey(selectedSubject)) {
            long selMillis = times.get(selectedSubject);
            measure.setTextSize(10.5f);
            String title = TextUtils.ellipsize(selectedSubject.title, measure, 84, TextUtils.TruncateAt.END).toString();
            text(c, title, 180, center - 12, 10.5f, ForestUi.MUTED, Paint.Align.CENTER);
            String selDur = StudyFormat.duration(selMillis);
            measure.setTextSize(14f);
            String selLabel = measure.measureText(selDur) <= 84 ? selDur : compact(selMillis);
            text(c, selLabel, 180, center + 6, 14f, ForestUi.DEEP, Paint.Align.CENTER, Typeface.create("serif", Typeface.BOLD));
            double share = (100.0 * selMillis) / total;
            String pct = share < .1 ? "<0.1%" : String.format(Locale.CHINA, "%.1f%%", share);
            text(c, pct, 180, center + 21, 10f, 0xff456444, Paint.Align.CENTER, Typeface.create("sans-serif-medium", Typeface.BOLD));
        } else {
            text(c, "合计", 180, center - 9, 10.5f, ForestUi.MUTED, Paint.Align.CENTER);
            String duration = StudyFormat.duration(total);
            measure.setTextSize(14.5f);
            String totalLabel = measure.measureText(duration) <= 88 ? duration : compact(total);
            text(
                c,
                totalLabel,
                180,
                center + 11,
                14.5f,
                ForestUi.DEEP,
                Paint.Align.CENTER,
                Typeface.create("serif", Typeface.BOLD)
            );
        }
    }

    private void drawBars(Canvas c, long total) {
        long max = 1;
        for (long time : times.values()) max = Math.max(max, time);
        int index = 0;
        TextPaint measure = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        measure.setTextSize(11.5f);
        for (Map.Entry<StudySubject, Long> entry : times.entrySet()) {
            StudySubject subject = entry.getKey();
            long duration = entry.getValue();
            float y = 22 + index++ * 40;
            String label = TextUtils.ellipsize(subject.title, measure, 196, TextUtils.TruncateAt.END).toString();
            text(c, label, 6, y, 11.5f, ForestUi.WOOD, Paint.Align.LEFT);
            double share = (100.0 * duration) / total;
            String pct = share < .1 ? "<0.1%" : String.format(Locale.CHINA, "%.1f%%", share);
            text(
                c,
                StudyFormat.duration(duration) + " · " + pct,
                354,
                y,
                10.5f,
                ForestUi.DEEP,
                Paint.Align.RIGHT
            );
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffefece2);
            c.drawRoundRect(new RectF(6, y + 8, 354, y + 15.5f), 4, 4, paint);
            paint.setColor(subject.color);
            c.drawRoundRect(
                new RectF(6, y + 8, 6 + Math.max(8f, (348f * duration) / max), y + 15.5f),
                4,
                4,
                paint
            );
        }
    }

    private void drawTrend(Canvas c) {
        int step = days == 30 ? 5 : (days - 1) / 7 + 1;
        int count = (days - 1) / step + 1;
        long[] values = new long[count];
        String[] labels = new String[count];
        long max = 1;
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(start);
        for (int i = 0; i < count; i++) {
            long from = day.getTimeInMillis();
            labels[i] = day.get(Calendar.MONTH) + 1 + "/" + day.get(Calendar.DAY_OF_MONTH);
            day.add(Calendar.DATE, step);
            long to = day.getTimeInMillis();
            for (StudyNotebook.Record record : records)
                if (record.completedAt >= from && record.completedAt < to) values[i] +=
                    record.durationMillis;
            max = Math.max(max, values[i]);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(0x225c4938);
        paint.setPathEffect(new DashPathEffect(new float[] { 4f, 4f }, 0));
        for (int i = 0; i < 4; i++) {
            float gy = 36 + i * 46;
            c.drawLine(6, gy, 354, gy, paint);
        }
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
        float column = 346f / count;
        for (int i = 0; i < count; i++) {
            float x = 7 + column * (i + .5f),
                height = (134f * values[i]) / max;
            boolean isToday = i == count - 1;
            paint.setColor(values[i] == 0 ? 0xffeae5d9 : isToday ? 0xff456444 : 0xff9aad86);
            c.drawRoundRect(new RectF(x - 11.5f, 174 - Math.max(3, height), x + 11.5f, 174), 5, 5, paint);
            if (values[i] > 0) {
                text(c, compact(values[i]), x, 165 - height, 9.2f, ForestUi.WOOD, Paint.Align.CENTER);
            }
            text(
                c,
                labels[i],
                x,
                194,
                10,
                isToday ? ForestUi.DEEP : ForestUi.MUTED,
                Paint.Align.CENTER,
                Typeface.create(isToday ? "sans-serif-medium" : "sans-serif", isToday ? Typeface.BOLD : Typeface.NORMAL)
            );
        }
        text(
            c,
            step > 1 ? "每柱最多合计" + step + "天 · 深绿为最新区间" : "每柱代表当天学习时长 · 深绿为今日",
            180,
            218,
            9.5f,
            ForestUi.MUTED,
            Paint.Align.CENTER
        );
    }

    private static String compact(long millis) {
        if (millis > 0 && millis < 60_000) return StudyFormat.duration(millis);
        long minutes = millis / 60_000;
        return minutes >= 60
            ? minutes / 60 + "时" + (minutes % 60 == 0 ? "" : (minutes % 60) + "分")
            : minutes + "分";
    }

    private static final class Label {

        final StudySubject subject;
        final long duration;
        final float x, y;
        final boolean right;
        float target;

        Label(StudySubject subject, long duration, float x, float y, float target, boolean right) {
            this.subject = subject;
            this.duration = duration;
            this.x = x;
            this.y = y;
            this.target = target;
            this.right = right;
        }
    }
}

package com.example.pkmapp.study;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import com.example.pkmapp.forest.ForestUi;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
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
    private float ringHeight = 274;
    private long start;
    private int days = 7;

    public StudyChartView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setData(List<StudyNotebook.Record> records, Kind kind, long start, int days) {
        this.records = new ArrayList<>(records);
        this.kind = kind;
        this.start = start;
        this.days = days;
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
        ringHeight = Math.max(250, 54 + Math.max(right, times.size() - right) * 38f);
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
                        : kind == Kind.BARS ? 15 + times.size() * 38
                        : kind == Kind.TREND ? 234 : ringHeight)
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
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(1);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
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
        else if (kind == Kind.BARS) drawBars(c);
        else drawTrend(c);
        c.restore();
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
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(24);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setColor(subject.color);
            c.drawArc(
                new RectF(120, center - 60, 240, center + 60),
                angle,
                sweep - (count > 1 ? Math.min(.9f, sweep * .08f) : 0),
                false,
                paint
            );
            labels.add(
                new Label(
                    subject,
                    duration,
                    180 + (float) Math.cos(mid) * 72,
                    center + (float) Math.sin(mid) * 72,
                    center + (float) Math.sin(mid) * 95,
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
                i == 0 ? 26 : side.get(i - 1).target + 38
            );
            if (!side.isEmpty()) {
                side.get(side.size() - 1).target = Math.min(ringHeight - 30, side.get(side.size() - 1).target);
                for (int i = side.size() - 2; i >= 0; i--) side.get(i).target = Math.min(
                    side.get(i).target,
                    side.get(i + 1).target - 38
                );
            }
        }
        TextPaint measure = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        for (Label label : labels) {
            float elbow = label.right ? 257 : 103,
                end = label.right ? 264 : 96,
                x = label.right ? 268 : 92;
            paint.setColor(0xff8e8e75);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(.9f);
            Path path = new Path();
            path.moveTo(label.x, label.y);
            path.lineTo(elbow, label.y);
            path.lineTo(end, label.target);
            c.drawPath(path, paint);
            Paint.Align align = label.right ? Paint.Align.LEFT : Paint.Align.RIGHT;
            measure.setTextSize(11.5f);
            String labelText = TextUtils.ellipsize(label.subject.title, measure, 90, TextUtils.TruncateAt.END).toString();
            text(c, labelText, x, label.target - 4, 11.5f, ForestUi.WOOD, align);
            String duration = StudyFormat.duration(label.duration);
            measure.setTextSize(10.5f);
            text(c, measure.measureText(duration) <= 90 ? duration : compact(label.duration),
                x, label.target + 11, 10.5f, ForestUi.DEEP, align);
        }
        text(c, "合计", 180, center - 9, 11, ForestUi.MUTED, Paint.Align.CENTER);
        String duration = StudyFormat.duration(total);
        measure.setTextSize(14);
        String totalLabel = measure.measureText(duration) <= 86 ? duration : compact(total);
        text(
            c,
            totalLabel,
            180,
            center + 12,
            14,
            ForestUi.DEEP,
            Paint.Align.CENTER
        );
    }

    private void drawBars(Canvas c) {
        long max = 1;
        for (long time : times.values()) max = Math.max(max, time);
        int index = 0;
        TextPaint measure = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        measure.setTextSize(11);
        for (Map.Entry<StudySubject, Long> entry : times.entrySet()) {
            StudySubject subject = entry.getKey();
            long duration = entry.getValue();
            float y = 24 + index++ * 38;
            String label = TextUtils.ellipsize(subject.title, measure, 240, TextUtils.TruncateAt.END).toString();
            text(c, label, 8, y, 11, ForestUi.WOOD, Paint.Align.LEFT);
            text(
                c,
                StudyFormat.duration(duration),
                352,
                y,
                10,
                ForestUi.DEEP,
                Paint.Align.RIGHT
            );
            paint.setColor(0xffececdf);
            c.drawRoundRect(new RectF(8, y + 8, 352, y + 16), 4, 4, paint);
            paint.setColor(subject.color);
            c.drawRoundRect(
                new RectF(8, y + 8, 8 + (344f * duration) / max, y + 16),
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
        paint.setStrokeWidth(1);
        for (int i = 0; i < 4; i++) {
            paint.setColor(0xffe2e2d1);
            c.drawLine(7, 40 + i * 45, 353, 40 + i * 45, paint);
        }
        float column = 344f / count;
        for (int i = 0; i < count; i++) {
            float x = 8 + column * (i + .5f),
                height = (138f * values[i]) / max;
            paint.setColor(i == count - 1 ? ForestUi.GREEN : 0xff9aad86);
            c.drawRoundRect(new RectF(x - 12, 180 - Math.max(2, height), x + 12, 180), 5, 5, paint);
            text(c, compact(values[i]), x, 171 - height, 9, ForestUi.WOOD, Paint.Align.CENTER);
            text(c, labels[i], x, 199, 10, ForestUi.MUTED, Paint.Align.CENTER);
        }
        text(
            c,
            step > 1 ? "每柱最多合计" + step + "天，日期为区间起日" : "每柱代表一天的具体学习时长",
            180,
            224,
            9,
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

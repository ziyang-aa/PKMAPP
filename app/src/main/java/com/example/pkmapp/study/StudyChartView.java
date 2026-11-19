package com.example.pkmapp.study;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import com.example.pkmapp.forest.ForestUi;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class StudyChartView extends View {

    public enum Kind {
        RING,
        BARS,
        TREND,
    }

    private Kind kind = Kind.RING;
    private List<StudyNotebook.Record> records = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long[] times = new long[StudySubject.values().length];
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
        java.util.Arrays.fill(times, 0);
        for (StudyNotebook.Record record : records)
            times[record.subject.ordinal()] += record.durationMillis;
        StringBuilder description = new StringBuilder("各科目学习时长。");
        for (StudySubject subject : StudySubject.values())
            description
                .append(subject.title)
                .append(StudyFormat.duration(times[subject.ordinal()]))
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
                    width *
                        (records.isEmpty()
                            ? .56f
                            : kind == Kind.BARS
                              ? .84f
                              : kind == Kind.TREND
                                ? .65f
                                : .76f)
                ),
                heightSpec
            )
        );
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
        if (getWidth() == 0) return;
        c.save();
        c.scale(getWidth() / 360f, getWidth() / 360f);
        long total = 0;
        for (long value : times) total += value;
        if (total == 0) {
            paint.setColor(0xffffffff);
            c.drawBitmap(
                com.example.pkmapp.forest.ForestBitmaps.get(
                    getContext(),
                    com.example.pkmapp.R.drawable.crayon_study_meadow
                ),
                null,
                new RectF(18, 12, 342, 120),
                paint
            );
            text(c, "还没有学习记录", 180, 151, 14, ForestUi.DEEP, Paint.Align.CENTER);
            text(
                c,
                "完成一次专注，足迹就会出现在这里。",
                180,
                179,
                11,
                ForestUi.MUTED,
                Paint.Align.CENTER
            );
        } else if (kind == Kind.RING) drawRing(c, total);
        else if (kind == Kind.BARS) drawBars(c);
        else drawTrend(c);
        c.restore();
    }

    private void drawRing(Canvas c, long total) {
        float angle = -90;
        List<Label> labels = new ArrayList<>();
        int count = 0;
        for (long value : times) if (value > 0) count++;
        for (StudySubject subject : StudySubject.values()) {
            long duration = times[subject.ordinal()];
            if (duration == 0) continue;
            float sweep = (360f * duration) / total;
            double mid = Math.toRadians(angle + sweep / 2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(25);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setColor(subject.color);
            c.drawArc(
                new RectF(111, 66, 249, 204),
                angle,
                sweep - (count > 1 ? Math.min(.9f, sweep * .08f) : 0),
                false,
                paint
            );
            labels.add(
                new Label(
                    subject,
                    duration,
                    180 + (float) Math.cos(mid) * 82,
                    135 + (float) Math.sin(mid) * 82,
                    135 + (float) Math.sin(mid) * 106,
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
                i == 0 ? 26 : side.get(i - 1).target + 34
            );
            if (!side.isEmpty()) {
                side.get(side.size() - 1).target = Math.min(244, side.get(side.size() - 1).target);
                for (int i = side.size() - 2; i >= 0; i--) side.get(i).target = Math.min(
                    side.get(i).target,
                    side.get(i + 1).target - 34
                );
            }
        }
        for (Label label : labels) {
            float elbow = label.right ? 269 : 91,
                end = label.right ? 280 : 80,
                x = label.right ? 284 : 76;
            paint.setColor(0xffa99d7d);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(.9f);
            Path path = new Path();
            path.moveTo(label.x, label.y);
            path.lineTo(elbow, label.y);
            path.lineTo(end, label.target);
            c.drawPath(path, paint);
            Paint.Align align = label.right ? Paint.Align.LEFT : Paint.Align.RIGHT;
            text(c, label.subject.title, x, label.target - 5, 10.5f, ForestUi.WOOD, align);
            text(
                c,
                StudyFormat.duration(label.duration),
                x,
                label.target + 9,
                9.5f,
                ForestUi.DEEP,
                align
            );
        }
        text(c, "累计专注", 180, 117, 10, ForestUi.MUTED, Paint.Align.CENTER);
        String duration = StudyFormat.duration(total);
        text(
            c,
            duration,
            180,
            143,
            duration.length() > 8 ? 13 : 16,
            ForestUi.DEEP,
            Paint.Align.CENTER
        );
        text(c, "一点点，也在生长", 180, 161, 9, ForestUi.MUTED, Paint.Align.CENTER);
    }

    private void drawBars(Canvas c) {
        long max = 1;
        for (long time : times) max = Math.max(max, time);
        for (StudySubject s : StudySubject.values()) {
            float y = 24 + s.ordinal() * 38;
            text(c, s.title, 8, y, 11, ForestUi.WOOD, Paint.Align.LEFT);
            text(
                c,
                StudyFormat.duration(times[s.ordinal()]),
                352,
                y,
                10,
                ForestUi.DEEP,
                Paint.Align.RIGHT
            );
            paint.setColor(0xffececdf);
            c.drawRoundRect(new RectF(8, y + 8, 352, y + 16), 4, 4, paint);
            paint.setColor(s.color);
            if (times[s.ordinal()] > 0) c.drawRoundRect(
                new RectF(8, y + 8, 8 + (344f * times[s.ordinal()]) / max, y + 16),
                4,
                4,
                paint
            );
        }
    }

    private void drawTrend(Canvas c) {
        int count = days == 30 ? 6 : days,
            step = days == 30 ? 5 : 1;
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
            days == 30 ? "每柱合计5天，日期为区间起日" : "每柱代表一天的具体学习时长",
            180,
            224,
            9,
            ForestUi.MUTED,
            Paint.Align.CENTER
        );
    }

    private static String compact(long millis) {
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

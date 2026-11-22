package com.example.pkmapp.study;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import com.example.pkmapp.forest.ForestUi;

/** An engraved dial; its progress represents elapsed study time against the chosen goal. */
public final class StudyClockFace extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;

    public void setProgress(float value) {
        progress = Math.max(0, Math.min(1, value));
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(getBounds().centerX(), getBounds().centerY());
        float scale = Math.min(getBounds().width(), getBounds().height()) / 220f;
        canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffdbe5bf);
        canvas.drawCircle(0, 2, 103, paint);
        paint.setColor(0xfff3f5e5);
        canvas.drawCircle(0, 0, 103, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f);
        paint.setColor(0xffc3c8ab);
        canvas.drawCircle(0, 0, 103, paint);
        canvas.drawOval(new RectF(-100, -98, 99, 101), paint);
        for (int i = 0; i < 12; i++) {
            canvas.save();
            canvas.rotate(i * 30);
            paint.setStrokeWidth(i % 5 == 0 ? 1.3f : .7f);
            paint.setColor(i % 5 == 0 ? 0xff9b9f7d : 0xffd4d4bd);
            canvas.drawLine(0, -93, 0, i % 5 == 0 ? -86 : -90, paint);
            canvas.restore();
        }
        paint.setStrokeWidth(2);
        paint.setColor(0xffe0e5d2);
        canvas.drawCircle(0, 0, 78, paint);
        if (progress > 0) {
            paint.setColor(ForestUi.GREEN);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(new RectF(-78, -78, 78, 78), -90, progress * 360, false, paint);
            double angle = Math.toRadians(-90 + progress * 360);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle((float) Math.cos(angle) * 78, (float) Math.sin(angle) * 78, 3, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffb8a171);
        canvas.drawCircle(0, -58, 2, paint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

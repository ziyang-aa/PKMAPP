package com.example.pkmapp.study;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Editorial precision progress rail representing elapsed study time against the chosen goal. */
public final class StudyClockFace extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF fillRect = new RectF();
    private float progress;

    public void setProgress(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - progress) < 0.0005f) return;
        progress = clamped;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int w = bounds.width();
        int h = bounds.height();
        if (w <= 0 || h <= 0) return;

        float radius = h / 2f;
        trackRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);

        // Warm linen recessed track
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffece8dc);
        canvas.drawRoundRect(trackRect, radius, radius, paint);

        // Subtle precision quarter notches along empty rail
        paint.setColor(0x24736c5d);
        float notchW = Math.max(1.5f, h * 0.16f);
        for (int i = 1; i <= 3; i++) {
            float nx = bounds.left + (w * i) / 4f;
            canvas.drawRect(nx - notchW / 2f, bounds.top + h * 0.24f, nx + notchW / 2f, bounds.bottom - h * 0.24f, paint);
        }

        // Smooth forest sage-to-pine gradient fill
        if (progress > 0f) {
            float fillW = Math.max(h, w * progress);
            fillRect.set(bounds.left, bounds.top, Math.min(bounds.right, bounds.left + fillW), bounds.bottom);
            paint.setColor(0xffffffff);
            paint.setAlpha(255);
            paint.setShader(
                new LinearGradient(
                    fillRect.left,
                    0,
                    fillRect.right,
                    0,
                    0xff6e9169,
                    0xff3e6040,
                    Shader.TileMode.CLAMP
                )
            );
            canvas.drawRoundRect(fillRect, radius, radius, paint);
            paint.setShader(null);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

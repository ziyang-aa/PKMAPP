package com.example.pkmapp.forest;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Editorial porcelain notebook card with subtle sheen, hairline border, faint rules, and washi tape. */
public final class CrayonCard extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        float scale = getBounds().width() / 320f;
        canvas.scale(scale, scale);
        float height = getBounds().height() / scale;
        Path edge = new Path();
        edge.moveTo(20, 2);
        edge.cubicTo(90, 1.5f, 225, 2.5f, 300, 2);
        edge.quadTo(318, 2, 318, 20);
        edge.cubicTo(317.5f, height / 3, 318.5f, height * .7f, 317.5f, height - 20);
        edge.quadTo(318, height - 2, 300, height - 2);
        edge.quadTo(155, height - 1.5f, 20, height - 2);
        edge.quadTo(2, height - 2, 2.5f, height - 20);
        edge.quadTo(1.5f, height / 2, 2, 20);
        edge.quadTo(2, 2, 20, 2);
        edge.close();

        // Warm porcelain vertical gradient
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, height, 0xffffffff, 0xfffcf8ee, Shader.TileMode.CLAMP));
        canvas.drawPath(edge, paint);
        paint.setShader(null);

        // Ultra-delicate warm sage hairline
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.95f);
        paint.setColor(0x28456444);
        canvas.drawPath(edge, paint);

        // Subtle warm-sage washi tape accent
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x78dfe6cc);
        canvas.save();
        canvas.rotate(4, 273, 7);
        canvas.drawRect(253, 0, 293, 11, paint);
        paint.setColor(0x45628056);
        for (int x = 257; x < 291; x += 6) canvas.drawLine(x, 2, x + 3, 9, paint);
        canvas.restore();
        canvas.restore();
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

package com.example.pkmapp.forest;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Light notebook paper with an irregular pencil edge and a short piece of paper tape. */
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
        edge.moveTo(18, 2);
        edge.cubicTo(90, 0, 225, 4, 302, 2);
        edge.quadTo(318, 1, 318, 19);
        edge.cubicTo(316, height / 3, 320, height * .7f, 317, height - 18);
        edge.quadTo(318, height - 2, 300, height - 2);
        edge.quadTo(155, height + 1, 18, height - 3);
        edge.quadTo(2, height - 2, 3, height - 20);
        edge.quadTo(0, height / 2, 2, 20);
        edge.quadTo(1, 2, 18, 2);
        edge.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xfffffdf4);
        canvas.drawPath(edge, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0xffb6b99b);
        canvas.drawPath(edge, paint);
        paint.setStrokeWidth(.6f);
        paint.setColor(0x244c7044);
        for (float y = 71; y < height - 24; y += 23) canvas.drawLine(15, y, 305, y + .6f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x90e6d799);
        canvas.save();
        canvas.rotate(5, 273, 7);
        canvas.drawRect(253, 0, 294, 12, paint);
        paint.setColor(0x607d9763);
        for (int x = 257; x < 292; x += 6) canvas.drawLine(x, 2, x + 3, 10, paint);
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

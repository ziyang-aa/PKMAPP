package com.example.pkmapp.forest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import com.example.pkmapp.R;

/** One full-screen image. No header image, regional backgrounds, fades or scroll offsets. */
public final class StudyBackdrop extends Drawable {

    private final Bitmap background;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public StudyBackdrop(Context context) {
        background = ForestBitmaps.get(context, R.drawable.bg_fixed_forest);
    }

    @Override
    public void draw(Canvas canvas) {
        float width = getBounds().width(),
            height = getBounds().height();
        if (width == 0 || height == 0) return;
        canvas.drawBitmap(background, null, new RectF(getBounds()), paint);
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

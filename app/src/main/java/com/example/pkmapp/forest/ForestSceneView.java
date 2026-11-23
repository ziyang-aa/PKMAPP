package com.example.pkmapp.forest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Cropped watercolor scenery feathered directly into its parent's paper texture. */
public final class ForestSceneView extends View {

    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final float verticalPosition;
    private final boolean fadeTop;

    public ForestSceneView(Context context, int resource, float verticalPosition, boolean fadeTop) {
        super(context);
        bitmap = ForestBitmaps.get(context, resource);
        this.verticalPosition = verticalPosition;
        this.fadeTop = fadeTop;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(),
            h = getHeight();
        if (w == 0 || h == 0) return;
        int layer = canvas.saveLayer(0, 0, w, h, null);
        float scale = Math.max(w / bitmap.getWidth(), h / bitmap.getHeight());
        float dw = bitmap.getWidth() * scale,
            dh = bitmap.getHeight() * scale;
        float left = (w - dw) / 2,
            top = (h - dh) * verticalPosition;
        paint.setShader(null);
        paint.setXfermode(null);
        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + dw, top + dh), paint);
        if (fadeTop) {
            paint.setShader(
                new LinearGradient(
                    0,
                    0,
                    0,
                    h,
                    new int[] { 0xebfffaf1, 0xc7fffaf1, 0x38fffaf1, 0x00fffaf1 },
                    new float[] { 0, .24f, .47f, .65f },
                    Shader.TileMode.CLAMP
                )
            );
            canvas.drawRect(0, 0, w, h, paint);
        }
        paint.setShader(
            new LinearGradient(
                0,
                0,
                0,
                h,
                new int[] { 0xff000000, 0xff000000, 0xe8000000, 0x66000000, 0x00000000 },
                new float[] { 0, .84f, .91f, .97f, 1 },
                Shader.TileMode.CLAMP
            )
        );
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        paint.setXfermode(null);
        canvas.restoreToCount(layer);
    }
}

package com.example.pkmapp.forest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.ColorUtils;

/** Quiet paper edges, a fine inset keyline and a small physical lip shared by forest controls. */
public final class ForestSurface extends Drawable {

    public enum Kind {
        PAPER,
        PRIMARY,
        SECONDARY,
        BOOKMARK,
        NAV,
        FIELD,
        TRAY,
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final Kind kind;
    private int fill,
        border,
        accent = ForestUi.GREEN,
        alpha = 255;
    private boolean selected, pressed, focused;
    private float radius, stroke;

    public ForestSurface(Context context, Kind kind, int fill, int border, float radius) {
        this.density = context.getResources().getDisplayMetrics().density;
        this.kind = kind;
        this.fill = fill;
        this.border = border;
        this.radius = radius * density;
        stroke = density;
    }

    public void setFill(int color) {
        fill = color;
        invalidateSelf();
    }

    public void setBorder(int color) {
        border = color;
        invalidateSelf();
    }

    public void setAccent(int color) {
        accent = color;
        invalidateSelf();
    }

    public void setRadius(float pixels) {
        radius = pixels;
        invalidateSelf();
    }

    public void setStroke(float pixels) {
        stroke = pixels;
        invalidateSelf();
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        selected = pressed = focused = false;
        for (int value : state) {
            if (value == android.R.attr.state_selected) selected = true;
            if (value == android.R.attr.state_pressed) pressed = true;
            if (value == android.R.attr.state_focused) focused = true;
        }
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        if (Color.alpha(fill) == 0 && kind != Kind.NAV) return;
        float d = density;
        RectF box = new RectF(getBounds());
        box.inset(d, d);
        float lip = kind == Kind.PRIMARY ? d : 0;
        box.bottom -= lip;
        if (pressed) box.top += d;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        if (lip > 0) {
            RectF back = new RectF(box);
            back.offset(0, lip);
            paint.setColor(kind == Kind.PRIMARY ? 0xff314d36 : 0xffdfdac7);
            paint.setAlpha(alpha);
            canvas.drawRoundRect(back, radius, radius, paint);
        }
        int base = kind == Kind.NAV ? (selected ? 0xffe8eddf : Color.TRANSPARENT) : fill;
        if (kind == Kind.NAV) {
            box.inset(5 * d, 3 * d);
            if (!selected) return;
        }
        paint.setShader(
            new LinearGradient(
                0,
                box.top,
                0,
                box.bottom,
                ColorUtils.blendARGB(base, Color.WHITE, kind == Kind.PRIMARY ? .11f : .32f),
                base,
                Shader.TileMode.CLAMP
            )
        );
        paint.setAlpha(alpha);
        canvas.drawRoundRect(box, radius, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(focused ? ForestUi.GREEN : border);
        paint.setAlpha(alpha);
        if (stroke > 0 && Color.alpha(border) > 0) canvas.drawRoundRect(box, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        if (kind == Kind.BOOKMARK) {
            paint.setColor(accent);
            Path ribbon = new Path();
            ribbon.moveTo(box.left + 10 * d, box.top);
            ribbon.lineTo(box.left + 18 * d, box.top);
            ribbon.lineTo(box.left + 18 * d, box.top + 16 * d);
            ribbon.lineTo(box.left + 14 * d, box.top + 12 * d);
            ribbon.lineTo(box.left + 10 * d, box.top + 16 * d);
            ribbon.close();
            canvas.drawPath(ribbon, paint);
            if (selected) {
                paint.setColor(ForestUi.GREEN);
                canvas.drawCircle(box.right - 12 * d, box.top + 12 * d, 6 * d, paint);
                paint.setColor(ForestUi.LIGHT);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(d);
                paint.setStrokeCap(Paint.Cap.ROUND);
                Path check = new Path();
                check.moveTo(box.right - 15 * d, box.top + 12 * d);
                check.lineTo(box.right - 13 * d, box.top + 14 * d);
                check.lineTo(box.right - 9 * d, box.top + 10 * d);
                canvas.drawPath(check, paint);
            }
        }
        if (kind == Kind.NAV) {
            paint.setColor(0xffb39460);
            canvas.drawRoundRect(
                new RectF(
                    box.centerX() - 10 * d,
                    box.bottom - 4 * d,
                    box.centerX() + 10 * d,
                    box.bottom - 2 * d
                ),
                d,
                d,
                paint
            );
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    @Override
    public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
    }

    @Override
    public void setAlpha(int value) {
        alpha = value;
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

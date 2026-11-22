package com.example.pkmapp.forest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import androidx.annotation.RequiresApi;

/** Frosts the fixed forest background and the scrolling page beneath the bottom bar. */
public final class FrostedNavigationBar extends LinearLayout {
    private View content;
    private final float radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path shape = new Path();
    private final BackdropBlur blur;
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::refreshGlass;
    private Shader glassTint;
    private Shader fallbackTint;

    public FrostedNavigationBar(Context context) {
        this(context, null);
    }

    public FrostedNavigationBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        radius = ForestUi.dp(context, 24);
        blur = Build.VERSION.SDK_INT >= 31 ? new BackdropBlur(ForestUi.dp(context, 12)) : null;
        setWillNotDraw(false);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                outline.setAlpha(.55f);
            }
        });
    }

    public void setBackdropContent(View content) {
        this.content = content;
        refreshGlass();
    }

    public void refreshGlass() {
        if (isShown()) postInvalidateOnAnimation();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(scrollListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        bounds.set(.5f, .5f, width - .5f, height - .5f);
        shape.reset();
        shape.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        glassTint = new LinearGradient(0, 0, 0, height, 0x96fffef4, 0x66f5f4e5, Shader.TileMode.CLAMP);
        fallbackTint = new LinearGradient(0, 0, 0, height, 0xd4fffef4, 0xb8f5f4e5, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bounds.isEmpty()) return;
        boolean frosted = Build.VERSION.SDK_INT >= 31 && blur != null && canvas.isHardwareAccelerated()
                && content != null && getParent() instanceof View;
        canvas.save();
        canvas.clipPath(shape);
        if (frosted) blur.draw(canvas, (View) getParent(), content, getLeft(), getTop(), getWidth(), getHeight());
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(frosted ? glassTint : fallbackTint);
        canvas.drawRoundRect(bounds, radius, radius, paint);
        canvas.restore();

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ForestUi.dp(getContext(), 1));
        paint.setColor(0xcafffef5);
        canvas.drawRoundRect(bounds, radius, radius, paint);
    }

    @RequiresApi(31)
    private static final class BackdropBlur {
        private final RenderNode layer = new RenderNode("Frosted navigation backdrop");
        private final int overscan;

        BackdropBlur(int radius) {
            overscan = radius * 2;
            layer.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        }

        void draw(Canvas canvas, View root, View content, int x, int y, int width, int height) {
            // A small margin supplies pixels beyond the visible edge of the blur.
            layer.setPosition(-overscan, -overscan, width + overscan, height + overscan);
            Canvas recording = layer.beginRecording(width + 2 * overscan, height + 2 * overscan);
            try {
                recording.translate(overscan - x, overscan - y);
                Drawable background = root.getBackground();
                if (background != null) background.draw(recording);
                recording.translate(content.getLeft(), content.getTop());
                content.draw(recording);
            } finally {
                layer.endRecording();
            }
            canvas.drawRenderNode(layer);
        }
    }
}

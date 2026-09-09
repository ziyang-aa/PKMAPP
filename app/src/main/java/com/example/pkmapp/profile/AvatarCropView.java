package com.example.pkmapp.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public final class AvatarCropView extends View {
    private static final int OUTPUT_SIZE = 512;
    private static final float MAX_SCALE_MULTIPLIER = 4f;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cropRect = new RectF();

    @Nullable
    private Bitmap bitmap;
    private float scale;
    private float minimumScale;
    private float maximumScale;
    private float offsetX;
    private float offsetY;
    private float lastTouchX;
    private float lastTouchY;
    private float lastPinchDistance;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    public AvatarCropView(Context context) {
        this(context, null);
    }

    public AvatarCropView(Context context, @Nullable AttributeSet attrs) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        scrimPaint.setColor(0x990F2118);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        borderPaint.setColor(0xFFF7F0D9);
        setFocusable(true);
    }

    public void setBitmap(@Nullable Bitmap bitmap) {
        this.bitmap = bitmap;
        resetPosition();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float inset = dp(18);
        float cropSize = Math.max(0f, Math.min(width, height) - inset * 2f);
        float left = (width - cropSize) / 2f;
        float top = (height - cropSize) / 2f;
        cropRect.set(left, top, left + cropSize, top + cropSize);
        resetPosition();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || cropRect.width() <= 0f) {
            return;
        }

        float imageWidth = bitmap.getWidth() * scale;
        float imageHeight = bitmap.getHeight() * scale;
        float imageLeft = centeredImageLeft(imageWidth) + offsetX;
        float imageTop = centeredImageTop(imageHeight) + offsetY;
        canvas.save();
        canvas.clipRect(cropRect);
        canvas.drawBitmap(bitmap, null,
                new RectF(imageLeft, imageTop, imageLeft + imageWidth, imageTop + imageHeight),
                bitmapPaint);
        canvas.restore();

        canvas.drawRect(0, 0, getWidth(), cropRect.top, scrimPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), scrimPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, scrimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, scrimPaint);
        canvas.drawRoundRect(cropRect, dp(22), dp(22), borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    lastPinchDistance = distance(event);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    float distance = distance(event);
                    if (lastPinchDistance > 0f && distance > 0f) {
                        float focusX = (event.getX(0) + event.getX(1)) / 2f;
                        float focusY = (event.getY(0) + event.getY(1)) / 2f;
                        zoomAround(focusX, focusY, distance / lastPinchDistance);
                    }
                    lastPinchDistance = distance;
                } else if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex >= 0) {
                        float x = event.getX(pointerIndex);
                        float y = event.getY(pointerIndex);
                        offsetX += x - lastTouchX;
                        offsetY += y - lastTouchY;
                        lastTouchX = x;
                        lastTouchY = y;
                        clampOffsets();
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() - 1 < 2) {
                    lastPinchDistance = 0f;
                    int releasedId = event.getPointerId(event.getActionIndex());
                    if (releasedId == activePointerId) {
                        int replacementIndex = event.getPointerCount() == 2
                                ? (event.getActionIndex() == 0 ? 1 : 0) : -1;
                        activePointerId = replacementIndex >= 0
                                ? event.getPointerId(replacementIndex)
                                : MotionEvent.INVALID_POINTER_ID;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                lastPinchDistance = 0f;
                return true;
            default:
                return true;
        }
    }

    public Bitmap getCroppedBitmap() {
        if (bitmap == null || cropRect.width() <= 0f) {
            throw new IllegalStateException("Avatar image is not ready");
        }
        float imageLeft = centeredImageLeft(bitmap.getWidth() * scale) + offsetX;
        float imageTop = centeredImageTop(bitmap.getHeight() * scale) + offsetY;
        int sourceLeft = Math.round((cropRect.left - imageLeft) / scale);
        int sourceTop = Math.round((cropRect.top - imageTop) / scale);
        int sourceSide = Math.round(cropRect.width() / scale);
        sourceSide = Math.min(sourceSide, Math.min(bitmap.getWidth(), bitmap.getHeight()));
        sourceLeft = clamp(sourceLeft, 0, bitmap.getWidth() - sourceSide);
        sourceTop = clamp(sourceTop, 0, bitmap.getHeight() - sourceSide);
        Bitmap cropped = Bitmap.createBitmap(bitmap, sourceLeft, sourceTop, sourceSide, sourceSide);
        if (sourceSide == OUTPUT_SIZE) {
            return cropped;
        }
        Bitmap output = Bitmap.createScaledBitmap(cropped, OUTPUT_SIZE, OUTPUT_SIZE, true);
        if (output != cropped) {
            cropped.recycle();
        }
        return output;
    }

    private void resetPosition() {
        if (bitmap == null || cropRect.width() <= 0f) {
            return;
        }
        minimumScale = AvatarCropMath.minimumScale(bitmap.getWidth(), bitmap.getHeight(),
                cropRect.width());
        maximumScale = minimumScale * MAX_SCALE_MULTIPLIER;
        scale = minimumScale;
        offsetX = 0f;
        offsetY = 0f;
        clampOffsets();
    }

    private void zoomAround(float focusX, float focusY, float factor) {
        if (factor <= 0f) {
            return;
        }
        float oldScale = scale;
        float newScale = Math.max(minimumScale, Math.min(maximumScale, oldScale * factor));
        if (newScale == oldScale) {
            return;
        }
        float oldLeft = centeredImageLeft(bitmap.getWidth() * oldScale) + offsetX;
        float oldTop = centeredImageTop(bitmap.getHeight() * oldScale) + offsetY;
        float contentX = (focusX - oldLeft) / oldScale;
        float contentY = (focusY - oldTop) / oldScale;
        scale = newScale;
        float newLeft = focusX - contentX * newScale;
        float newTop = focusY - contentY * newScale;
        offsetX = newLeft - centeredImageLeft(bitmap.getWidth() * newScale);
        offsetY = newTop - centeredImageTop(bitmap.getHeight() * newScale);
        clampOffsets();
        invalidate();
    }

    private void clampOffsets() {
        if (bitmap == null || cropRect.width() <= 0f) {
            return;
        }
        float imageWidth = bitmap.getWidth() * scale;
        float imageHeight = bitmap.getHeight() * scale;
        float baseLeft = centeredImageLeft(imageWidth);
        float baseTop = centeredImageTop(imageHeight);
        offsetX = Math.max(cropRect.right - imageWidth - baseLeft,
                Math.min(cropRect.left - baseLeft, offsetX));
        offsetY = Math.max(cropRect.bottom - imageHeight - baseTop,
                Math.min(cropRect.top - baseTop, offsetY));
    }

    private float centeredImageLeft(float imageWidth) {
        return (getWidth() - imageWidth) / 2f;
    }

    private float centeredImageTop(float imageHeight) {
        return (getHeight() - imageHeight) / 2f;
    }

    private float distance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

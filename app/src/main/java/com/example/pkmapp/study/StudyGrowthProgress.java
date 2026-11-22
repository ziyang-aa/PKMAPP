package com.example.pkmapp.study;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import com.example.pkmapp.forest.ForestUi;

/** A leaf travels along a dotted garden path, passing one sprout at each milestone. */
public final class StudyGrowthProgress extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float displayed;
    private ValueAnimator animator;

    public StudyGrowthProgress(Context context, int growth) {
        super(context);
        displayed = growth;
        describe(growth);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void advanceTo(int growth) {
        describe(growth);
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(displayed, growth);
        animator.setDuration(
            Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled() ? 0 : 600
        );
        animator.addUpdateListener(value -> {
            displayed = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void describe(int growth) {
        setContentDescription(
            "成长进度" + growth + "/" + StudyGrowth.REQUIRED_WATER + "，" + StudyGrowth.name(growth)
        );
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 360f, getHeight() / 76f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xffe3e8cf);
        canvas.drawLine(16, 34, 344, 34, paint);
        paint.setColor(0xff91b366);
        float tip = 16 + (328 * displayed) / StudyGrowth.REQUIRED_WATER;
        canvas.drawLine(16, 34, tip, 34, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < StudyGrowth.STAGE_COUNT; i++) {
            float x = 16 + (328f * StudyGrowth.milestone(i)) / StudyGrowth.REQUIRED_WATER;
            paint.setColor(displayed >= StudyGrowth.milestone(i) ? ForestUi.GREEN : 0xffb9c4a0);
            canvas.drawCircle(x, 34, 5, paint);
            canvas.drawRect(x - .8f, 16, x + .8f, 29, paint);
            canvas.drawOval(new RectF(x - 8, 14, x, 20), paint);
            canvas.drawOval(new RectF(x, 10, x + 8, 17), paint);
        }
        paint.setColor(0xfffff8dc);
        canvas.drawCircle(tip, 34, 8, paint);
        paint.setColor(ForestUi.GREEN);
        canvas.save();
        canvas.rotate(-32, tip, 34);
        canvas.drawOval(new RectF(tip - 5, 31, tip + 5, 37), paint);
        canvas.restore();
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(10);
        paint.setColor(ForestUi.MUTED);
        canvas.drawText("安家", 8, 59, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("住进森林", 352, 59, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(ForestUi.DEEP);
        canvas.drawText(Math.round(displayed) + "% · 每一滴，都在慢慢长大", 180, 74, paint);
        canvas.restore();
    }
}

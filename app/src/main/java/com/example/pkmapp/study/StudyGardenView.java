package com.example.pkmapp.study;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import com.example.pkmapp.R;
import com.example.pkmapp.forest.ForestBitmaps;
import com.example.pkmapp.forest.ForestUi;

/** Independent hand-painted sprites, real-time motion and touch reactions, without a game engine. */
public final class StudyGardenView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap backdrop, sprout, sapling, tree, can;
    private Bitmap companion;
    private final Rect visible = new Rect();
    private boolean planted, active;
    private int growth, from, to;
    private String motion = "idle";
    private float progress;
    private ValueAnimator animator;
    private final float[] sizes = { 96, 138, 190, 212, 226, 238 };
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!active || !isAttachedToWindow()) return;
            if (moving() && getGlobalVisibleRect(visible)) invalidate();
            postDelayed(this, getGlobalVisibleRect(visible) ? 33 : 400);
        }
    };

    public StudyGardenView(Context context) {
        super(context);
        backdrop = ForestBitmaps.get(context, R.drawable.crayon_garden_backdrop);
        sprout = ForestBitmaps.get(context, R.drawable.savings_seedling_stage_1);
        sapling = ForestBitmaps.get(context, R.drawable.savings_seedling_stage_2);
        tree = ForestBitmaps.get(context, R.drawable.savings_tree_stage_3);
        can = ForestBitmaps.get(context, R.drawable.crayon_watering_can);
        companion = ForestBitmaps.get(context, R.drawable.crayon_study_companion);
        setBackground(ForestUi.rounded(context, 0xfff7f4e8, 0x24456444, 22));
        setElevation(ForestUi.dp(context, 2));
        setClipToOutline(true);
        setFocusable(true);
        setClickable(true);
    }

    public void setCompanion(int drawableRes) {
        this.companion = ForestBitmaps.get(getContext(), drawableRes);
        invalidate();
    }

    public void setState(boolean planted, int growth) {
        this.planted = planted;
        this.growth = growth;
        setContentDescription(
            planted
                ? "互动小树，成长值" +
                      growth +
                      "/" +
                      StudyGrowth.REQUIRED_WATER +
                      "，点按可以打招呼"
                : "等待播种的林地"
        );
        invalidate();
    }

    private boolean moving() {
        return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
    }

    public void setActive(boolean value) {
        active = value;
        removeCallbacks(frame);
        if (value) post(frame);
        else cancelMotion();
    }

    public boolean busy() {
        return !motion.equals("idle") && !motion.equals("greet");
    }

    public void water(int from, Runnable done) {
        this.from = from;
        this.to = from + 1;
        animate("water", 3100, done);
    }

    public void plant(Runnable done) {
        animate("plant", 1500, done);
    }

    public void greet() {
        if (!busy()) animate("greet", 1500, null);
    }

    private void animate(String type, long duration, Runnable done) {
        cancelMotion();
        motion = type;
        progress = 0;
        ValueAnimator next = ValueAnimator.ofFloat(0, 1);
        animator = next;
        next.setDuration(moving() ? duration : 0);
        next.setInterpolator(new android.view.animation.LinearInterpolator());
        next.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        next.addListener(
            new AnimatorListenerAdapter() {
                boolean cancelled;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animator == next) animator = null;
                    if (cancelled) return;
                    motion = "idle";
                    invalidate();
                    if (done != null && isAttachedToWindow()) done.run();
                }
            }
        );
        next.start();
    }

    private void cancelMotion() {
        if (animator != null) {
            ValueAnimator old = animator;
            animator = null;
            old.cancel();
        }
        motion = "idle";
        progress = 0;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active) post(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        cancelMotion();
        super.onDetachedFromWindow();
    }

    private void oval(Canvas c, float x, float y, float rx, float ry, int color) {
        p.setShader(null);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        c.drawOval(new RectF(x - rx, y - ry, x + rx, y + ry), p);
    }

    private void text(Canvas c, String value, float x, float y, int size, int color) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText(value, x, y, p);
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static float ease(float v) {
        float t = clamp(v);
        return t * t * (3 - 2 * t);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.save();
        c.scale(getWidth() / 360f, getHeight() / 285f);
        float time = moving() ? SystemClock.uptimeMillis() / 1000f : 0;
        p.setShader(null);
        p.setColor(0xffffffff);
        p.setAlpha(255);
        c.drawBitmap(backdrop, null, new RectF(0, 0, 360, 285), p);
        oval(c, 182, 238, 52, 10, 0x205a6c44);
        if (motion.equals("water")) {
            float grow = ease((progress - .5f) / .42f),
                size = size(from) + (size(to) - size(from)) * grow;
            if (image(from) == image(to)) drawTree(c, to, time, 1, size, grow, 0);
            else {
                drawTree(c, from, time, 1 - grow, size, grow, 0);
                drawTree(c, to, time, grow, size, grow, 0);
            }
        } else if (motion.equals("plant")) {
            c.save();
            c.translate(170, 237);
            float scale = ease(progress);
            c.scale(scale, scale);
            c.translate(-170, -237);
            drawTree(c, 0, time, scale, sizes[0], 0, 0);
            c.restore();
            if (moving()) particles(c, progress);
        } else if (planted) {
            float nod =
                motion.equals("greet") && moving()
                    ? (float) (Math.sin(progress * Math.PI * 4) * Math.sin(progress * Math.PI))
                    : 0;
            drawTree(c, growth, time, 1, size(growth), 0, nod);
            if (motion.equals("greet")) hearts(c, progress);
        } else {
            oval(c, 182, 236, 26, 6.5f, 0x88b4986d);
            oval(c, 181, 234.5f, 19, 4.5f, 0x99d1b98e);
        }
        // The tree is the main subject; the companion and low woodland details frame it.
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xffffffff);
        p.setAlpha(255);
        float unstretchX = (getHeight() / 285f) / Math.max(0.001f, getWidth() / 360f);
        float compH = 88f;
        float compW = compH * (companion.getWidth() / (float) Math.max(1, companion.getHeight())) * unstretchX;
        c.drawBitmap(companion, null, new RectF(18, 246 - compH, 18 + compW, 246), p);
        if (motion.equals("water")) {
            watering(c, progress);
            float grow = ease((progress - .5f) / .42f);
            if (grow > 0 && moving()) particles(c, grow);
        }
        float bx = 70 + (float) Math.sin(time * .25) * 27,
            by = 118 + (float) Math.cos(time * .6) * 14,
            wing = .4f + Math.abs((float) Math.sin(time * 5)) * .6f;
        oval(c, bx - 3 * wing, by - 2, 4 * wing, 5, 0xaad3b878);
        oval(c, bx + 3 * wing, by - 2, 4 * wing, 5, 0xbadec98b);
        oval(c, bx, by, .8f, 4, 0xff9c926b);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(9);
        String badge = "会呼吸的小林地";
        float bw = p.measureText(badge);
        p.setColor(0xccfffef8);
        c.drawRoundRect(new RectF(12, 11, 12 + bw + 18, 30), 10, 10, p);
        text(c, badge, 21, 23.5f, 9, 0xff4c6444);
        String caption = busy()
            ? "水壶 → 洒水 → 舒展生长"
            : planted
              ? "点点小树，和它打个招呼"
              : "先在这里，种下一颗种子";
        p.setTextSize(9);
        float cw = p.measureText(caption);
        p.setColor(0xc2fffef8);
        c.drawRoundRect(new RectF(180 - cw / 2f - 14, 258, 180 + cw / 2f + 14, 277), 10, 10, p);
        p.setColor(0xff52644a);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(caption, 180, 270.5f, p);
        p.setTextAlign(Paint.Align.LEFT);
        c.restore();
    }

    private float size(int growth) {
        int stage = StudyGrowth.stage(growth);
        if (stage == StudyGrowth.STAGE_COUNT - 1) return sizes[stage];
        float fraction =
            (growth - StudyGrowth.milestone(stage)) /
            (float) (StudyGrowth.milestone(stage + 1) - StudyGrowth.milestone(stage));
        return sizes[stage] + (sizes[stage + 1] - sizes[stage]) * fraction;
    }

    private Bitmap image(int stage) {
        int visualStage = StudyGrowth.stage(stage);
        return visualStage < 2 ? sprout : visualStage == 2 ? sapling : tree;
    }

    private void drawTree(
        Canvas c,
        int stage,
        float t,
        float alpha,
        float size,
        float flourish,
        float nod
    ) {
        if (alpha <= 0) return;
        Bitmap bitmap = image(stage);
        int visualStage = StudyGrowth.stage(stage);
        float root = visualStage < 2 ? .90f : visualStage == 2 ? .91f : .94f;
        float unstretchX = (getHeight() / 285f) / Math.max(0.001f, getWidth() / 360f);
        float aspect = bitmap.getWidth() / (float) Math.max(1, bitmap.getHeight());
        float halfW = (size * aspect * unstretchX) / 2f;
        c.save();
        c.translate(182, 238);
        c.rotate(
            moving() ? (float) (Math.sin(t * 1.35) * .92 + Math.sin(t * .73) * .46) + nod * 5 : 0
        );
        float stretch = 1 + (moving() ? (float) Math.sin(flourish * Math.PI * 2) * .05f : 0);
        c.scale(1 / stretch, stretch);
        p.setShader(null);
        p.setColor(0xffffffff);
        p.setAlpha(Math.round(alpha * 255));
        c.drawBitmap(
            bitmap,
            null,
            new RectF(-halfW, -size * root, halfW, size * (1 - root)),
            p
        );
        p.setAlpha(255);
        c.restore();
    }

    private void watering(Canvas c, float value) {
        float show = ease(value / .16f) * (1 - ease((value - .71f) / .16f));
        if (show <= 0) return;
        float pouring = ease((value - .13f) / .12f) * (1 - ease((value - .6f) / .1f));
        float angle = -.22f - pouring * .55f,
            x = 273 + (1 - show) * 80,
            y = 111 - pouring * 8;
        c.save();
        c.translate(x, y);
        c.rotate((float) Math.toDegrees(angle));
        c.scale(-1, 1);
        p.setColor(0xffffffff);
        p.setAlpha((int) (show * 255));
        c.drawBitmap(can, null, new RectF(-72, -58, 72, 58), p);
        p.setAlpha(255);
        c.restore();
        if (value > .22f && value < .7f) {
            float sx = x - 55 * (float) Math.cos(angle) + 23 * (float) Math.sin(angle),
                sy = y - 55 * (float) Math.sin(angle) - 23 * (float) Math.cos(angle);
            for (int i = 0; i < 22; i++) {
                float phase = ((value - .22f) * 6 + i / 22f) % 1,
                    dx = sx - phase * (sx - 170) + (float) Math.sin(i * 7) * phase * 10,
                    dy = sy + phase * (234 - sy);
                p.setColor(i % 2 == 0 ? 0xffb3d3d5 : 0xff82aeb8);
                p.setAlpha((int) (Math.sin(phase * Math.PI) * 215));
                p.setStrokeWidth(1.8f);
                c.drawLine(dx, dy, dx - 1.7f, dy + 5, p);
            }
            p.setAlpha(255);
            p.setColor(0x6680a9a0);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1);
            float ripple = (value * 7) % 1;
            c.drawOval(
                new RectF(161 - ripple * 23, 234 - ripple * 5, 179 + ripple * 23, 238 + ripple * 5),
                p
            );
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void particles(Canvas c, float value) {
        for (int i = 0; i < 13; i++) {
            float phase = (value * 1.2f + i / 13f) % 1,
                angle = i * 2.4f,
                x = 170 + (float) Math.cos(angle) * (25 + phase * 54),
                y = 200 - phase * 130 + (float) Math.sin(i) * 22;
            p.setColor(0xff94ad67);
            p.setAlpha((int) (Math.sin(phase * Math.PI) * Math.sin(value * Math.PI) * 220));
            c.save();
            c.rotate((float) Math.toDegrees(angle + phase), x, y);
            c.drawOval(new RectF(x - 3.5f, y - 1.6f, x + 3.5f, y + 1.6f), p);
            c.restore();
            p.setAlpha(255);
        }
    }

    private void hearts(Canvas c, float value) {
        for (int i = 0; i < 3; i++) {
            float phase = clamp(value * 1.3f - i * .12f);
            c.save();
            c.translate(155 + i * 19 + (float) Math.sin(phase * 5) * 7, 151 - phase * 70);
            c.scale(.65f, .65f);
            p.setColor(0xffbe9973);
            p.setAlpha((int) (Math.sin(phase * Math.PI) * 215));
            Path heart = new Path();
            heart.moveTo(0, 3);
            heart.cubicTo(-13, -5, -3, -13, 0, -6);
            heart.cubicTo(3, -13, 13, -5, 0, 3);
            c.drawPath(heart, p);
            p.setAlpha(255);
            c.restore();
        }
    }
}

package com.example.pkmapp.forest;

import android.animation.ValueAnimator;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Short, interruptible feedback; motion and haptics follow the device's accessibility settings. */
public final class ForestMotion {

    private ForestMotion() {}

    public static boolean enabled() {
        return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
    }

    public static void press(View view, boolean pressed) {
        view.animate().cancel();
        float scale = pressed ? .975f : 1f;
        if (!enabled() || !view.isAttachedToWindow()) {
            view.setScaleX(1);
            view.setScaleY(1);
            return;
        }
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(pressed ? 75 : 150)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    public static void tap(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static void confirm(View view) {
        view.performHapticFeedback(
            Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY
        );
    }

    public static void bind(View view) {
        if (view instanceof ForestButton) return;
        view.setOnTouchListener((target, event) -> {
            if (!target.isEnabled()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    press(target, true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (
                        event.getX() < 0 ||
                        event.getX() > target.getWidth() ||
                        event.getY() < 0 ||
                        event.getY() > target.getHeight()
                    ) press(target, false);
                    break;
                case MotionEvent.ACTION_UP:
                    press(target, false);
                    if (target.isPressed()) tap(target);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    press(target, false);
                    break;
                default:
                    break;
            }
            return false;
        });
        view.addOnAttachStateChangeListener(
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View target) {}

                @Override
                public void onViewDetachedFromWindow(View target) {
                    reset(target);
                }
            }
        );
    }

    public static void reveal(View view) {
        view.post(() -> {
            if (!view.isAttachedToWindow() || !enabled()) return;
            view.setAlpha(.55f);
            view.setTranslationY(ForestUi.dp(view.getContext(), 4));
            view.animate()
                .alpha(1)
                .translationY(0)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        });
    }

    public static void stamp(View view) {
        view.post(() -> {
            if (!view.isAttachedToWindow()) return;
            confirm(view);
            if (!enabled()) return;
            view.setScaleX(.86f);
            view.setScaleY(.86f);
            view.setAlpha(.45f);
            view.animate()
                .scaleX(1)
                .scaleY(1)
                .alpha(1)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        });
    }

    public static void reset(View view) {
        view.animate().cancel();
        view.setScaleX(1);
        view.setScaleY(1);
    }
}

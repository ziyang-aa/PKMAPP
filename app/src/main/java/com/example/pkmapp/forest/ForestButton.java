package com.example.pkmapp.forest;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import com.google.android.material.button.MaterialButton;

/** Keeps existing MaterialButton semantics while sharing the app's paper-and-ink appearance. */
public final class ForestButton extends MaterialButton {

    private ForestSurface surface;
    private boolean navigation;

    public ForestButton(Context context) {
        super(context);
    }

    public ForestButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ForestButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (isEnabled() && !navigation) ForestMotion.press(this, pressed);
    }

    @Override
    public boolean performClick() {
        if (!isEnabled()) return false;
        ForestMotion.tap(this);
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        ForestMotion.reset(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled && surface != null) ForestMotion.reset(this);
        setAlpha(enabled ? 1f : isSelected() ? .85f : .55f);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (!isEnabled()) setAlpha(selected ? .85f : .55f);
    }

    public void present(ForestSurface.Kind kind, int fill, int border, int accent) {
        navigation = kind == ForestSurface.Kind.NAV;
        int radius = navigation ? 15 : 12;
        surface = new ForestSurface(
            getContext(),
            kind,
            fill,
            border,
            radius
        );
        surface.setAccent(accent);
        super.setBackgroundTintList(null);
        if (navigation) {
            setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x264e8146),
                surface,
                ForestUi.rounded(getContext(), 0xffffffff, 0, radius)
            ));
        } else {
            setBackground(ForestUi.ripple(getContext(), surface, kind == ForestSurface.Kind.PRIMARY));
        }
        refreshDrawableState();
    }

    @Override
    public void setBackgroundTintList(ColorStateList colors) {
        if (surface == null) super.setBackgroundTintList(colors);
        else if (colors != null) surface.setFill(colors.getDefaultColor());
    }

    @Override
    public void setStrokeColor(ColorStateList colors) {
        if (surface == null) super.setStrokeColor(colors);
        else if (colors != null) surface.setBorder(colors.getDefaultColor());
    }

    @Override
    public void setStrokeWidth(int width) {
        if (surface == null) super.setStrokeWidth(width);
        else surface.setStroke(width);
    }

    @Override
    public void setCornerRadius(int radius) {
        if (surface == null) super.setCornerRadius(radius);
        else surface.setRadius(radius);
    }
}

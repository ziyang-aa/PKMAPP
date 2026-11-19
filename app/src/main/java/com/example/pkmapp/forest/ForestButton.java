package com.example.pkmapp.forest;

import android.content.Context;
import android.content.res.ColorStateList;
import com.google.android.material.button.MaterialButton;

/** Keeps existing MaterialButton semantics while sharing the app's paper-and-ink appearance. */
public final class ForestButton extends MaterialButton {

    private ForestSurface surface;

    public ForestButton(Context context) {
        super(context);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (isEnabled()) ForestMotion.press(this, pressed);
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
        surface = new ForestSurface(
            getContext(),
            kind,
            fill,
            border,
            kind == ForestSurface.Kind.NAV ? 15 : 12
        );
        surface.setAccent(accent);
        super.setBackgroundTintList(null);
        setBackground(ForestUi.ripple(getContext(), surface, kind == ForestSurface.Kind.PRIMARY));
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

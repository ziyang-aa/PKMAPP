package com.example.pkmapp.forest;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.example.pkmapp.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Clean, zero-nested-box editorial alert dialog aligned with Material Design 3 & shadcn/ui
 * hierarchy: single warm-porcelain surface, open typography, and uncluttered action buttons.
 */
public final class ForestDialogBuilder extends MaterialAlertDialogBuilder {

    private CharSequence title;

    public ForestDialogBuilder(Context context) {
        super(context, R.style.ThemeOverlay_PkmAPP_ForestDialog);
        setBackground(buildDialogSurface(context));
        setBackgroundInsetStart(0);
        setBackgroundInsetEnd(0);
        setBackgroundInsetTop(0);
        setBackgroundInsetBottom(0);
    }

    @Override
    public ForestDialogBuilder setTitle(CharSequence value) {
        title = value;
        super.setTitle(value);
        return this;
    }

    @Override
    public ForestDialogBuilder setTitle(int resource) {
        return setTitle(getContext().getText(resource));
    }

    @Override
    public ForestDialogBuilder setView(View view) {
        if (view instanceof EditText) {
            LinearLayout field = ForestUi.column(getContext());
            field.setPadding(dp(24), dp(8), dp(24), dp(4));
            field.addView(view, new LinearLayout.LayoutParams(-1, -2));
            super.setView(field);
        } else {
            super.setView(view);
        }
        return this;
    }

    private boolean isDangerTitle() {
        if (title == null) return false;
        String text = title.toString();
        return text.contains("删除") || text.contains("清空") || text.contains("覆盖") || text.contains("放弃")
            || text.contains("移除");
    }

    @Override
    public AlertDialog create() {
        Context context = getContext();
        final AlertDialog[] reference = new AlertDialog[1];
        if (title != null) {
            boolean danger = isDangerTitle();
            LinearLayout heading = ForestUi.column(context);
            heading.setPadding(dp(24), dp(22), dp(16), dp(6));

            LinearLayout top = ForestUi.row(context);
            top.setGravity(Gravity.CENTER_VERTICAL);

            ImageView leaf = new ImageView(context);
            leaf.setImageResource(R.drawable.ic_leaf);
            leaf.setImageTintList(ColorStateList.valueOf(danger ? 0xff9e4c37 : ForestUi.DEEP));
            leaf.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
            iconLp.rightMargin = dp(10);
            top.addView(leaf, iconLp);

            TextView label = ForestUi.heading(context, title.toString(), 19);
            label.setTextColor(0xff243624);
            label.setTypeface(Typeface.create("serif", Typeface.BOLD));
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
            titleParams.rightMargin = dp(6);
            top.addView(label, titleParams);

            TextView close = ForestUi.text(context, "×", 20, 0xff8e8475);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription("关闭弹窗");
            close.setBackground(buildGhostPressBackground(dp(18)));
            close.setOnClickListener(v -> reference[0].cancel());
            ForestMotion.bind(close);
            top.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));

            heading.addView(top);
            super.setCustomTitle(heading);
        }

        AlertDialog dialog = super.create();
        reference[0] = dialog;
        Window window = dialog.getWindow();
        if (window != null) {
            View decor = window.getDecorView();
            decor.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        decor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        style(dialog);
                    }
                }
            );
        }
        return dialog;
    }

    private void style(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        int width = Math.min(
            getContext().getResources().getDisplayMetrics().widthPixels - dp(40),
            dp(360)
        );
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setDimAmount(.38f);

        boolean danger = isDangerTitle();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(danger ? 0xff7a4234 : 0xff62594b);
            message.setTextSize(14);
            message.setLineSpacing(dp(5), 1);
            message.setBackground(null);
            message.setPadding(dp(24), dp(8), dp(24), dp(10));
            if (message.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) message.getLayoutParams();
                lp.leftMargin = 0;
                lp.rightMargin = 0;
                lp.topMargin = 0;
                lp.bottomMargin = 0;
                message.setLayoutParams(lp);
            }
        }

        styleInputs(window.getDecorView());

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        Button anchor = positive != null ? positive : negative != null ? negative : neutral;
        if (anchor == null || !(anchor.getParent() instanceof ViewGroup)) return;
        ViewGroup oldPanel = (ViewGroup) anchor.getParent();
        if (!(oldPanel.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) oldPanel.getParent();
        int index = parent.indexOfChild(oldPanel);

        LinearLayout actions = ForestUi.column(getContext());
        actions.setPadding(dp(24), dp(14), dp(24), dp(22));

        boolean hasPositive = positive != null && positive.getVisibility() != View.GONE;
        boolean hasNegative = negative != null && negative.getVisibility() != View.GONE;
        boolean hasNeutral = neutral != null && neutral.getVisibility() != View.GONE;

        boolean stackTwoButtons = false;
        if (hasPositive && hasNegative && !hasNeutral) {
            int posLen = positive.getText() != null ? positive.getText().length() : 0;
            int negLen = negative.getText() != null ? negative.getText().length() : 0;
            stackTwoButtons = posLen > 6 || negLen > 6 || (posLen + negLen) > 11;
        }

        for (Button button : new Button[] { positive, negative, neutral }) {
            if (button == null || button.getVisibility() == View.GONE) continue;
            ((ViewGroup) button.getParent()).removeView(button);
            boolean primary = button == positive;
            boolean destructivePrimary = primary && danger;
            button.setAllCaps(false);
            button.setMaxLines(1);
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setTextSize(14);
            button.setLetterSpacing(0.02f);
            button.setTypeface(Typeface.create(
                primary ? "sans-serif-medium" : "sans-serif",
                primary ? Typeface.BOLD : Typeface.NORMAL
            ));
            button.setTextColor(
                primary ? 0xfffffdf8 : button == neutral ? ForestUi.DEEP : 0xff6e6557
            );
            button.setBackgroundTintList(null);
            button.setStateListAnimator(null);

            if (primary) {
                button.setBackground(buildPrimaryPill(destructivePrimary));
                button.setElevation(dp(2));
                if (Build.VERSION.SDK_INT >= 28) {
                    button.setOutlineSpotShadowColor(destructivePrimary ? 0x448d4836 : 0x44385738);
                }
            } else if (stackTwoButtons && button == negative) {
                button.setBackground(buildGhostPressBackground(dp(22)));
            } else {
                button.setBackground(buildSecondarySoftPill());
            }

            button.setPadding(dp(16), dp(10), dp(16), dp(10));
            button.setMinHeight(dp(44));
            button.setMinimumWidth(0);
            button.setMinWidth(0);
            ForestMotion.bind(button);
        }

        if (hasPositive && hasNegative && !hasNeutral) {
            if (stackTwoButtons) {
                actions.addView(positive, new LinearLayout.LayoutParams(-1, dp(46)));
                LinearLayout.LayoutParams negParams = new LinearLayout.LayoutParams(-1, dp(42));
                negParams.topMargin = dp(8);
                actions.addView(negative, negParams);
            } else {
                LinearLayout row = ForestUi.row(getContext());
                row.addView(negative, new LinearLayout.LayoutParams(0, dp(44), 1f));
                LinearLayout.LayoutParams posParams = new LinearLayout.LayoutParams(0, dp(44), 1.15f);
                posParams.leftMargin = dp(10);
                row.addView(positive, posParams);
                actions.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
        } else if (hasPositive && hasNegative && hasNeutral) {
            LinearLayout topRow = ForestUi.row(getContext());
            topRow.addView(neutral, new LinearLayout.LayoutParams(0, dp(44), 1f));
            LinearLayout.LayoutParams posParams = new LinearLayout.LayoutParams(0, dp(44), 1.15f);
            posParams.leftMargin = dp(10);
            topRow.addView(positive, posParams);
            actions.addView(topRow, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams negParams = new LinearLayout.LayoutParams(-1, dp(42));
            negParams.topMargin = dp(8);
            negative.setBackground(buildGhostPressBackground(dp(22)));
            actions.addView(negative, negParams);
        } else {
            for (Button button : new Button[] { positive, neutral, negative }) {
                if (button == null || button.getVisibility() == View.GONE) continue;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(46));
                if (actions.getChildCount() > 0) params.topMargin = dp(8);
                actions.addView(button, params);
            }
        }

        parent.removeView(oldPanel);
        parent.addView(actions, index, new ViewGroup.LayoutParams(-1, -2));
    }

    private void styleInputs(View view) {
        if (view instanceof EditText) {
            EditText input = (EditText) view;
            input.setTextSize(15);
            input.setTextColor(0xff243624);
            input.setHintTextColor(0xff9a9080);
            input.setBackgroundTintList(null);
            input.setBackground(buildUnderlineInputBackground());
            input.setPadding(dp(2), dp(10), dp(2), dp(10));
            input.setMinimumHeight(dp(44));
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleInputs(group.getChildAt(i));
        }
    }

    private Drawable buildDialogSurface(Context context) {
        GradientDrawable surface = new GradientDrawable();
        surface.setShape(GradientDrawable.RECTANGLE);
        surface.setColor(0xfffffefa);
        surface.setCornerRadius(ForestUi.dp(context, 26));
        surface.setStroke(Math.max(1, ForestUi.dp(context, 0.75f)), 0x1a5c4938);
        return surface;
    }

    private Drawable buildPrimaryPill(boolean danger) {
        float radius = dp(23);
        int topColor = danger ? 0xffad553f : 0xff52764f;
        int bottomColor = danger ? 0xff91412e : 0xff3b5b3a;
        int pressTop = danger ? 0xff91412e : 0xff3f5e3e;
        int pressBottom = danger ? 0xff7b3424 : 0xff2f4a2e;

        GradientDrawable normalBase = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { topColor, bottomColor }
        );
        normalBase.setCornerRadius(radius);

        GradientDrawable highlight = new GradientDrawable();
        highlight.setCornerRadius(radius - dp(1));
        highlight.setColor(Color.TRANSPARENT);
        highlight.setStroke(Math.max(1, dp(0.75f)), 0x2effffff);

        LayerDrawable normal = new LayerDrawable(new Drawable[] {
            normalBase,
            new InsetDrawable(highlight, dp(1))
        });

        GradientDrawable pressed = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { pressTop, pressBottom }
        );
        pressed.setCornerRadius(radius);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_pressed }, pressed);
        states.addState(new int[] {}, normal);
        return states;
    }

    private Drawable buildSecondarySoftPill() {
        float radius = dp(22);
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(0xfff3efe6);
        normal.setCornerRadius(radius);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(0xffe9e3d6);
        pressed.setCornerRadius(radius);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_pressed }, pressed);
        states.addState(new int[] {}, normal);
        return states;
    }

    private Drawable buildGhostPressBackground(float radius) {
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(0x127b5c3b);
        pressed.setCornerRadius(radius);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_pressed }, pressed);
        states.addState(new int[] {}, normal);
        return states;
    }

    private Drawable buildUnderlineInputBackground() {
        GradientDrawable normalLine = new GradientDrawable();
        normalLine.setColor(0x335c4938);
        LayerDrawable normal = new LayerDrawable(new Drawable[] { normalLine });
        normal.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        normal.setLayerHeight(0, Math.max(1, dp(1)));

        GradientDrawable focusLine = new GradientDrawable();
        focusLine.setColor(0xff456444);
        LayerDrawable focused = new LayerDrawable(new Drawable[] { focusLine });
        focused.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        focused.setLayerHeight(0, Math.max(2, dp(1.8f)));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_focused }, focused);
        states.addState(new int[] {}, normal);
        return states;
    }

    private int dp(float value) {
        return ForestUi.dp(getContext(), value);
    }
}

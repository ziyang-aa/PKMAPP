package com.example.pkmapp.forest;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

/** Paper dialogs with the prototype's leaf heading, framed fields and full-width actions. */
public final class ForestDialogBuilder extends MaterialAlertDialogBuilder {

    private CharSequence title;

    public ForestDialogBuilder(Context context) {
        super(context, R.style.ThemeOverlay_PkmAPP_ForestDialog);
        setBackground(new Paper(context));
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
        return text.contains("删除") || text.contains("清空") || text.contains("覆盖") || text.contains("放弃");
    }

    @Override
    public AlertDialog create() {
        Context context = getContext();
        final AlertDialog[] reference = new AlertDialog[1];
        if (title != null) {
            boolean danger = isDangerTitle();
            LinearLayout heading = ForestUi.column(context);
            heading.setPadding(dp(22), dp(18), dp(18), dp(8));
            LinearLayout top = ForestUi.row(context);
            ImageView leaf = new ImageView(context);
            leaf.setImageResource(R.drawable.ic_leaf);
            leaf.setImageTintList(ColorStateList.valueOf(danger ? 0xff8d4836 : ForestUi.DEEP));
            leaf.setPadding(dp(8), dp(8), dp(8), dp(8));
            leaf.setBackground(ForestUi.rounded(
                context,
                danger ? 0xfff6e7e2 : 0xffe8eedb,
                danger ? 0xffe2c5bc : 0xffced8bc,
                11
            ));
            top.addView(leaf, new LinearLayout.LayoutParams(dp(36), dp(36)));
            TextView label = ForestUi.heading(context, title.toString(), 20);
            label.setTextColor(0xff283f2c);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
            titleParams.leftMargin = dp(11);
            titleParams.rightMargin = dp(8);
            top.addView(label, titleParams);
            TextView close = ForestUi.text(context, "×", 18, ForestUi.MUTED);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription("关闭弹窗");
            close.setBackground(ForestUi.ripple(
                context,
                ForestUi.rounded(context, 0xfff4f1e4, 0xffe1dece, 11),
                false
            ));
            close.setOnClickListener(v -> reference[0].cancel());
            ForestMotion.bind(close);
            top.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
            heading.addView(top);
            super.setCustomTitle(heading);
        }
        AlertDialog dialog = super.create();
        reference[0] = dialog;
        Window window = dialog.getWindow();
        if (window != null) {
            // A layout observer preserves callers' OnShowListener and input validation handlers.
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
            getContext().getResources().getDisplayMetrics().widthPixels - dp(32),
            dp(380)
        );
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setDimAmount(.34f);
        boolean danger = isDangerTitle();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(danger ? 0xff774133 : 0xff5c5547);
            message.setTextSize(13);
            message.setLineSpacing(dp(4), 1);
            message.setPadding(dp(14), dp(12), dp(14), dp(12));
            message.setBackground(ForestUi.rounded(
                getContext(),
                danger ? 0xfffaf0ec : 0xfff5f2e6,
                danger ? 0xffe6cdc5 : 0xffdcd8c5,
                13
            ));
            if (message.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) message.getLayoutParams();
                lp.leftMargin = dp(22);
                lp.rightMargin = dp(22);
                lp.topMargin = dp(4);
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
        actions.setPadding(dp(22), dp(10), dp(22), dp(18));

        boolean hasPositive = positive != null && positive.getVisibility() != View.GONE;
        boolean hasNegative = negative != null && negative.getVisibility() != View.GONE;
        boolean hasNeutral = neutral != null && neutral.getVisibility() != View.GONE;

        for (Button button : new Button[] { positive, negative, neutral }) {
            if (button == null || button.getVisibility() == View.GONE) continue;
            ((ViewGroup) button.getParent()).removeView(button);
            boolean primary = button == positive;
            boolean destructivePrimary = primary && danger;
            button.setAllCaps(false);
            button.setTextSize(13);
            button.setTypeface(Typeface.create(primary ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            button.setTextColor(
                primary ? ForestUi.LIGHT : button == neutral ? ForestUi.DEEP : 0xff5c5547
            );
            button.setBackgroundTintList(null);
            button.setBackground(
                ForestUi.ripple(
                    getContext(),
                    new ForestSurface(
                        getContext(),
                        primary ? ForestSurface.Kind.PRIMARY : ForestSurface.Kind.SECONDARY,
                        destructivePrimary ? 0xff944a37 : primary ? 0xff456444 : ForestUi.LIGHT,
                        destructivePrimary ? 0xff753626 : primary ? 0xff345439 : 0xffd5d6bf,
                        12
                    ),
                    primary
                )
            );
            button.setPadding(dp(12), dp(8), dp(12), dp(8));
            button.setMinHeight(dp(46));
            button.setMinimumWidth(0);
            button.setMinWidth(0);
            ForestMotion.bind(button);
        }

        if (hasPositive && hasNegative && !hasNeutral) {
            LinearLayout tray = ForestUi.row(getContext());
            tray.setBackground(ForestUi.rounded(getContext(), 0xfff2efe3, 0xffdcd8c4, 15));
            tray.setPadding(dp(5), dp(5), dp(5), dp(5));
            tray.addView(negative, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams posParams = new LinearLayout.LayoutParams(0, -2, 1.32f);
            posParams.leftMargin = dp(8);
            tray.addView(positive, posParams);
            actions.addView(tray, new LinearLayout.LayoutParams(-1, -2));
        } else if (hasPositive && hasNegative && hasNeutral) {
            LinearLayout topRow = ForestUi.row(getContext());
            topRow.addView(neutral, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams posParams = new LinearLayout.LayoutParams(0, -2, 1.15f);
            posParams.leftMargin = dp(8);
            topRow.addView(positive, posParams);
            actions.addView(topRow, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams negParams = new LinearLayout.LayoutParams(-1, -2);
            negParams.topMargin = dp(8);
            actions.addView(negative, negParams);
        } else {
            for (Button button : new Button[] { negative, neutral, positive }) {
                if (button == null || button.getVisibility() == View.GONE) continue;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
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
            input.setTextSize(14);
            input.setTextColor(ForestUi.DEEP);
            input.setHintTextColor(ForestUi.MUTED);
            input.setBackgroundTintList(null);
            input.setBackground(
                new ForestSurface(
                    getContext(),
                    ForestSurface.Kind.FIELD,
                    0xfff5f4e8,
                    0xffcdd2bc,
                    10
                )
            );
            input.setPadding(dp(12), dp(12), dp(12), dp(12));
            input.setMinimumHeight(dp(46));
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleInputs(group.getChildAt(i));
        }
    }

    private int dp(float value) {
        return ForestUi.dp(getContext(), value);
    }

    private static final class Paper extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Shader texture;
        private final float radius, stroke;

        Paper(Context context) {
            texture = new BitmapShader(
                ForestBitmaps.get(context, R.drawable.forest_paper_clean),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            );
            radius = ForestUi.dp(context, 22);
            stroke = ForestUi.dp(context, 1);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(texture);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setShader(null);
            paint.setColor(0xd9fffcf3);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            bounds.inset(stroke / 2, stroke / 2);
            paint.setColor(0xffc9cfb2);
            paint.setStrokeWidth(stroke);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}

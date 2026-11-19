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

    @Override
    public AlertDialog create() {
        Context context = getContext();
        final AlertDialog[] reference = new AlertDialog[1];
        if (title != null) {
            LinearLayout heading = ForestUi.column(context);
            heading.setPadding(dp(24), dp(16), dp(12), dp(8));
            LinearLayout top = ForestUi.row(context);
            ImageView leaf = new ImageView(context);
            leaf.setImageResource(R.drawable.ic_leaf);
            leaf.setImageTintList(ColorStateList.valueOf(0xff91aa78));
            leaf.setPadding(dp(4), dp(4), dp(4), dp(4));
            top.addView(leaf, new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView label = ForestUi.heading(context, title.toString(), 20);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
            titleParams.leftMargin = dp(10);
            top.addView(label, titleParams);
            TextView close = ForestUi.text(context, "×", 25, ForestUi.MUTED);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription("关闭弹窗");
            close.setOnClickListener(v -> reference[0].cancel());
            ForestMotion.bind(close);
            top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
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
        window.setDimAmount(.3f);
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(ForestUi.MUTED);
            message.setTextSize(13);
            message.setLineSpacing(dp(4), 1);
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
        actions.setPadding(dp(24), dp(6), dp(24), dp(14));
        LinearLayout secondary = ForestUi.row(getContext());
        for (Button button : new Button[] { positive, negative, neutral }) {
            if (button == null || button.getVisibility() == View.GONE) continue;
            ((ViewGroup) button.getParent()).removeView(button);
            boolean primary = button == positive;
            button.setAllCaps(false);
            button.setTextSize(13);
            button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            button.setTextColor(
                primary ? ForestUi.LIGHT : button == neutral ? 0xff945f4e : ForestUi.DEEP
            );
            button.setBackgroundTintList(null);
            button.setBackground(
                ForestUi.ripple(
                    getContext(),
                    new ForestSurface(
                        getContext(),
                        primary ? ForestSurface.Kind.PRIMARY : ForestSurface.Kind.SECONDARY,
                        primary ? 0xff456444 : 0,
                        primary ? 0xff345439 : 0,
                        12
                    ),
                    primary
                )
            );
            button.setPadding(dp(12), dp(8), dp(12), dp(8));
            button.setMinHeight(dp(48));
            button.setMinimumWidth(0);
            button.setMinWidth(0);
            ForestMotion.bind(button);
            if (primary) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = dp(8);
                actions.addView(button, params);
            } else secondary.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (secondary.getChildCount() > 0) actions.addView(
            secondary,
            new LinearLayout.LayoutParams(-1, -2)
        );
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

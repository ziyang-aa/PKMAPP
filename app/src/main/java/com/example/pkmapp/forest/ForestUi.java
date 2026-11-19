package com.example.pkmapp.forest;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.pkmapp.R;
import com.google.android.material.button.MaterialButton;

public final class ForestUi {

    public static final int PAPER = 0xfff5f1e7,
        LIGHT = 0xfffffcf3,
        GREEN = 0xff4e8146,
        DEEP = 0xff315e3a,
        WOOD = 0xff7b5c3b,
        MUTED = 0xff736c5d,
        LINE = 0xffdddcc9;

    private ForestUi() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static TextView text(Context context, String value, int sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView heading(Context context, String value, int sp) {
        TextView view = text(context, value, sp, DEEP);
        view.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        return view;
    }

    public static MaterialButton button(Context context, String label, boolean primary) {
        ForestButton button = new ForestButton(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(primary ? LIGHT : DEEP);
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? GREEN : 0xffedf0e0));
        button.setStrokeColor(ColorStateList.valueOf(primary ? GREEN : 0xffcbd3ba));
        button.setStrokeWidth(dp(context, 1));
        button.setCornerRadius(dp(context, 11));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(dp(context, 46));
        button.setMinimumHeight(dp(context, 46));
        button.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        button.setLetterSpacing(.04f);
        button.present(
            primary ? ForestSurface.Kind.PRIMARY : ForestSurface.Kind.SECONDARY,
            primary ? 0xff456444 : LIGHT,
            primary ? 0xff345439 : 0xffd5d6bf,
            GREEN
        );
        return button;
    }

    public static Drawable ripple(Context context, Drawable content, boolean primary) {
        return new RippleDrawable(
            ColorStateList.valueOf(primary ? 0x28ffffff : 0x184e8146),
            content,
            rounded(context, 0xffffffff, 0, 12)
        );
    }

    public static void bookmark(MaterialButton button, int accent, boolean selected) {
        if (button instanceof ForestButton) ((ForestButton) button).present(
            ForestSurface.Kind.BOOKMARK,
            selected ? 0xffedf1e2 : LIGHT,
            selected ? 0xff9eaf88 : LINE,
            accent
        );
        button.setSelected(selected);
    }

    public static GradientDrawable rounded(Context context, int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static void paper(View view) {
        BitmapDrawable background = new BitmapDrawable(
            view.getResources(),
            ForestBitmaps.get(view.getContext(), R.drawable.forest_paper_clean)
        );
        background.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        view.setBackground(background);
    }

    public static ImageView image(Context context, int resource, String description) {
        ImageView image = new ImageView(context);
        image.setImageBitmap(ForestBitmaps.get(context, resource));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(description);
        return image;
    }

    public static void add(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(parent.getContext(), topDp);
        parent.addView(child, params);
    }

    public static void spacer(LinearLayout parent, int heightDp) {
        parent.addView(
            new View(parent.getContext()),
            new LinearLayout.LayoutParams(1, dp(parent.getContext(), heightDp))
        );
    }
}

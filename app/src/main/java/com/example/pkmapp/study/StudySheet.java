package com.example.pkmapp.study;

import static com.example.pkmapp.forest.ForestUi.*;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import com.example.pkmapp.R;
import com.example.pkmapp.forest.ForestCalendarView;
import com.example.pkmapp.forest.ForestMotion;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/** A single modal window with back navigation, pinned actions and independently scrollable content. */
final class StudySheet extends BottomSheetDialog {
    static final int MOSS = 0xffeef4ea;
    static final int OAT = 0xfff4efe4;
    static final int SAGE = 0xffedf1ec;
    private static final int SHEET = 0xfffffefa;
    private static final int INK = 0xff243624;
    private static final int HAIRLINE = 0x185c4938;
    private static final float MAX_HEIGHT = .93f;

    private final FrameLayout host;
    private final Deque<Pane> panes = new ArrayDeque<>();
    private final OnBackPressedCallback back = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            pop();
        }
    };
    private int bottomInset;

    StudySheet(Context context) {
        super(context, R.style.ThemeOverlay_PkmAPP_StudySheet);
        host = new FrameLayout(context);
        setContentView(host);
        getOnBackPressedDispatcher().addCallback(back);
        Window window = getWindow();
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.setDimAmount(.34f);
        }
        setDismissWithAnimation(ForestMotion.enabled());
        BottomSheetBehavior<FrameLayout> behavior = getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        ViewCompat.setOnApplyWindowInsetsListener(host, (view, insets) -> {
            int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            bottomInset = Math.max(nav, ime);
            updatePadding();
            return insets;
        });
    }

    void push(Pane pane) {
        if (!panes.isEmpty()) hideKeyboard();
        panes.push(pane);
        display(panes.size() > 1 ? 1 : 0);
        if (!isShowing()) show();
    }

    void replace(Pane pane) {
        hideKeyboard();
        if (!panes.isEmpty()) panes.pop();
        panes.push(pane);
        display(1);
    }

    void pop() {
        if (panes.size() <= 1) {
            dismiss();
            return;
        }
        hideKeyboard();
        panes.pop();
        display(-1);
    }

    void popTo(Pane target) {
        if (target == null || !panes.contains(target)) {
            pop();
            return;
        }
        if (panes.peek() == target) return;
        hideKeyboard();
        while (panes.size() > 1 && panes.peek() != target) {
            panes.pop();
        }
        display(-1);
    }

    void pushDatePicker(String title, String initialDayKey, Consumer<String> onConfirm) {
        Context context = getContext();
        Pane pane = new Pane(context, title, null, 0);
        addHairline(pane.body, 0, 8);

        TextView previewValue = text(context, "", 13, DEEP);
        previewValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        previewValue.setFontFeatureSettings("tnum");

        ForestCalendarView calendar = new ForestCalendarView(
            context,
            initialDayKey,
            null,
            dayKey -> previewValue.setText(ForestCalendarView.formatChineseFull(dayKey))
        );
        previewValue.setText(ForestCalendarView.formatChineseFull(calendar.selectedDayKey()));
        pane.body.addView(calendar, new LinearLayout.LayoutParams(-1, -2));

        addHairline(pane.body, 8, 12);
        LinearLayout previewRow = row(context);
        previewRow.addView(text(context, "已选日期", 13, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        previewRow.addView(previewValue);
        pane.body.addView(previewRow, new LinearLayout.LayoutParams(-1, -2));

        pane.primary("确认日期", () -> {
            String picked = calendar.selectedDayKey();
            pop();
            onConfirm.accept(picked);
        });
        push(pane);
    }

    void hideKeyboard() {
        View focus = host.findFocus();
        if (focus == null) return;
        InputMethodManager manager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }

    private void updatePadding() {
        host.setPadding(0, 0, 0, bottomInset);
    }

    private void display(int direction) {
        Pane pane = panes.peek();
        boolean canGoBack = panes.size() > 1;
        back.setEnabled(canGoBack);
        getBehavior().setDraggable(!canGoBack);
        host.removeAllViews();
        host.addView(pane.root, new FrameLayout.LayoutParams(-1, -2));
        pane.backButton.setVisibility(canGoBack ? View.VISIBLE : View.GONE);
        pane.grip.setVisibility(canGoBack ? View.INVISIBLE : View.VISIBLE);
        pane.backButton.setOnClickListener(v -> pop());
        pane.closeButton.setOnClickListener(v -> dismiss());
        pane.root.requestFocus();
        if (direction != 0 && ForestMotion.enabled()) {
            pane.content.animate().cancel();
            pane.content.setAlpha(.65f);
            pane.content.setTranslationX(direction * dp(getContext(), 8));
            pane.content.animate().alpha(1f).translationX(0f).setDuration(145)
                .setInterpolator(new PathInterpolator(.2f, 0, 0, 1)).start();
        } else {
            pane.content.setAlpha(1f);
            pane.content.setTranslationX(0f);
        }
    }

    static final class Pane {
        final LinearLayout above, body, footer;
        private final LinearLayout root, content, actionsRow;
        private final NestedScrollView scroll;
        private final ImageButton backButton, closeButton;
        private final View grip;
        private final Context context;

        Pane(Context context, String title, String detail, int glyph) {
            this.context = context;
            root = new LinearLayout(context) {
                @Override
                protected void onMeasure(int width, int height) {
                    int viewport = getResources().getDisplayMetrics().heightPixels;
                    int cap = Math.round(viewport * MAX_HEIGHT);
                    if (MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED)
                        cap = Math.min(cap, MeasureSpec.getSize(height));
                    super.onMeasure(width, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
                }
            };
            root.setOrientation(LinearLayout.VERTICAL);
            root.setFocusableInTouchMode(true);

            LinearLayout header = column(context);
            header.setPadding(dp(context, 22), dp(context, 10), dp(context, 22), dp(context, 12));
            grip = new View(context);
            grip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            grip.setBackground(rounded(context, 0x387b5c3b, 0, 3));
            LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4));
            gripParams.gravity = Gravity.CENTER_HORIZONTAL;
            header.addView(grip, gripParams);

            LinearLayout titleRow = row(context);
            backButton = iconButton(context, R.drawable.ic_study_sheet_back, "上一步");
            LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34));
            backParams.setMarginEnd(dp(context, 10));
            titleRow.addView(backButton, backParams);

            TextView heading = heading(context, title, 21);
            heading.setTypeface(Typeface.create("serif", Typeface.BOLD));
            heading.setTextColor(INK);
            heading.setLineSpacing(dp(context, 2), 1);
            ViewCompat.setAccessibilityHeading(heading, true);

            LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(0, -2, 1);
            label.setMarginEnd(dp(context, 8));
            titleRow.addView(heading, label);
            closeButton = iconButton(context, R.drawable.ic_study_sheet_close, "关闭弹窗");
            titleRow.addView(closeButton, new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)));
            add(header, titleRow, 12);
            if (detail != null && !detail.isEmpty()) {
                TextView subtitle = text(context, detail, 12, MUTED);
                subtitle.setLineSpacing(dp(context, 4), 1);
                add(header, subtitle, 5);
            }
            root.addView(header);

            content = column(context);
            // The content column shrinks at the viewport limit; the footer stays within reach.
            root.addView(content, new LinearLayout.LayoutParams(-1, -2, 1));
            above = column(context);
            above.setPadding(dp(context, 22), 0, dp(context, 22), 0);
            content.addView(above, new LinearLayout.LayoutParams(-1, -2));

            scroll = new NestedScrollView(context);
            scroll.setClipToPadding(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            body = column(context);
            body.setPadding(dp(context, 22), dp(context, 2), dp(context, 22), dp(context, 12));
            scroll.addView(body, new ViewGroup.LayoutParams(-1, -2));
            content.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1));

            footer = column(context);
            footer.setPadding(dp(context, 22), dp(context, 8), dp(context, 22), dp(context, 18));
            footer.setBackgroundColor(Color.TRANSPARENT);
            footer.setElevation(0f);
            footer.setVisibility(View.GONE);
            root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

            actionsRow = row(context);
        }

        private void ensureActionsRow() {
            footer.setVisibility(View.VISIBLE);
            if (actionsRow.getParent() == null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                if (footer.getChildCount() > 0) lp.topMargin = dp(context, 6);
                footer.addView(actionsRow, lp);
            }
        }

        void scrollToBottom() {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }

        private void syncActionMargins() {
            int count = actionsRow.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = actionsRow.getChildAt(i);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                lp.setMarginEnd(i < count - 1 ? dp(context, 10) : 0);
                child.setLayoutParams(lp);
            }
        }

        void listBody(View list, int height) {
            content.removeView(scroll);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height, 1);
            params.setMargins(dp(context, 22), dp(context, 8), dp(context, 22), dp(context, 12));
            content.addView(list, params);
        }

        MaterialButton primary(String label, Runnable click) {
            ensureActionsRow();
            MaterialButton button = button(context, label, true);
            button.setMinHeight(dp(context, 48));
            button.setMinimumHeight(dp(context, 48));
            button.setCornerRadius(dp(context, 14));
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            button.setOnClickListener(v -> click.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(context, 48), 1.32f);
            actionsRow.addView(button, lp);
            syncActionMargins();
            return button;
        }

        void secondary(String label, Runnable click) {
            ensureActionsRow();
            TextView secondaryBtn = link(context, label, 0xff435043, click);
            secondaryBtn.setMinHeight(dp(context, 48));
            secondaryBtn.setMinimumHeight(dp(context, 48));
            secondaryBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            secondaryBtn.setBackground(ripple(context, rounded(context, 0xfff2eee3, 0, 14), false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(context, 48), 1f);
            actionsRow.addView(secondaryBtn, 0, lp);
            syncActionMargins();
        }
    }

    static View hairline(Context context) {
        View line = new View(context);
        line.setBackgroundColor(HAIRLINE);
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return line;
    }

    static void addHairline(LinearLayout parent, int topDp, int bottomDp) {
        Context context = parent.getContext();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            -1, Math.max(1, Math.round(0.75f * context.getResources().getDisplayMetrics().density)));
        lp.topMargin = dp(context, topDp);
        lp.bottomMargin = dp(context, bottomDp);
        parent.addView(hairline(context), lp);
    }

    static void styleChoiceChip(TextView chip, boolean selected) {
        Context context = chip.getContext();
        chip.setSelected(selected);
        chip.setTextColor(selected ? 0xfffffcf3 : 0xff5a6458);
        chip.setTypeface(Typeface.create(
            selected ? "sans-serif-medium" : "sans-serif",
            selected ? Typeface.BOLD : Typeface.NORMAL
        ));
        chip.setBackground(ripple(context, rounded(context,
            selected ? 0xff456444 : 0xfff2eee3, 0, 999), selected));
    }

    static TextView link(Context context, String label, int ink, Runnable click) {
        TextView link = text(context, label, 13, ink);
        link.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        link.setGravity(Gravity.CENTER);
        link.setMinimumHeight(dp(context, 48));
        link.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        link.setBackground(ripple(context, rounded(context, Color.TRANSPARENT, 0, 13), false));
        link.setOnClickListener(v -> click.run());
        link.setFocusable(true);
        ForestMotion.bind(link);
        return link;
    }

    static TextView section(Context context, String name) {
        TextView title = text(context, name, 12, MUTED);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        ViewCompat.setAccessibilityHeading(title, true);
        return title;
    }

    static LinearLayout item(Context context, String title, String detail, int glyph, int tint,
                             String end, boolean selected, Runnable click) {
        LinearLayout row = row(context);
        row.setMinimumHeight(dp(context, detail == null ? 50 : 60));
        int bg = selected ? MOSS : tint;
        row.setPadding(dp(context, bg == Color.TRANSPARENT || bg == 0 ? 4 : 12),
            dp(context, 10), dp(context, bg == Color.TRANSPARENT || bg == 0 ? 4 : 12), dp(context, 10));
        row.setBackground(ripple(context, rounded(context, bg, 0, 14), false));
        row.setSelected(selected);
        if (glyph != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(glyph);
            icon.setImageTintList(ColorStateList.valueOf(DEEP));
            icon.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)));
        }
        LinearLayout copy = column(context);
        TextView name = text(context, title, 15, INK);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        copy.addView(name);
        if (detail != null && !detail.isEmpty()) {
            TextView supporting = text(context, detail, 12, MUTED);
            supporting.setLineSpacing(dp(context, 3), 1);
            add(copy, supporting, 4);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        if (glyph != 0) copyParams.setMarginStart(dp(context, 10));
        row.addView(copy, copyParams);
        if (end != null) {
            TextView trailing = text(context, end, end.equals("›") ? 20 : 13, selected ? DEEP : MUTED);
            trailing.setPadding(dp(context, 8), 0, 0, 0);
            trailing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(trailing);
        }
        row.setContentDescription(title + (detail == null || detail.isEmpty() ? "" : "，" + detail));
        row.setOnClickListener(v -> click.run());
        row.setFocusable(true);
        ForestMotion.bind(row);
        return row;
    }

    static void styleField(EditText input) {
        Context context = input.getContext();
        StateListDrawable background = new StateListDrawable();
        background.addState(
            new int[] {android.R.attr.state_focused},
            rounded(context, 0xfffffefa, 0xff456444, 12)
        );
        background.addState(new int[0], rounded(context, 0xfff3efe5, 0, 12));
        input.setBackground(background);
        input.setBackgroundTintList(null);
        input.setTextSize(14.5f);
        input.setTextColor(INK);
        input.setHintTextColor(0xff94998e);
        input.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11));
        input.setMinimumHeight(dp(context, 46));
    }

    static void styleUnderlineField(EditText input) {
        Context context = input.getContext();
        StateListDrawable background = new StateListDrawable();
        background.addState(
            new int[] {android.R.attr.state_focused},
            underlineDrawable(context, 0xff456444, 1.5f)
        );
        background.addState(new int[0], underlineDrawable(context, HAIRLINE, 1f));
        input.setBackground(background);
        input.setBackgroundTintList(null);
        input.setTextSize(15f);
        input.setTextColor(INK);
        input.setHintTextColor(0xff9ca195);
        input.setPadding(dp(context, 2), dp(context, 8), dp(context, 2), dp(context, 10));
        input.setMinimumHeight(dp(context, 44));
    }

    static void styleQuietInlineField(EditText input) {
        Context context = input.getContext();
        StateListDrawable background = new StateListDrawable();
        background.addState(
            new int[] {android.R.attr.state_focused},
            underlineDrawable(context, 0xff456444, 1.5f)
        );
        background.addState(new int[0], rounded(context, Color.TRANSPARENT, 0, 0));
        input.setBackground(background);
        input.setBackgroundTintList(null);
        input.setTextSize(14.5f);
        input.setTextColor(INK);
        input.setHintTextColor(0xff9ca195);
        input.setPadding(dp(context, 2), dp(context, 8), dp(context, 2), dp(context, 8));
        input.setMinimumHeight(dp(context, 42));
    }

    private static Drawable underlineDrawable(Context context, int color, float heightDp) {
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(color);
        LayerDrawable layer = new LayerDrawable(new Drawable[] { bar });
        layer.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        layer.setLayerHeight(0, Math.max(1, Math.round(heightDp * context.getResources().getDisplayMetrics().density)));
        return layer;
    }

    static ImageButton iconButton(Context context, int image, String name) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(image);
        button.setImageTintList(ColorStateList.valueOf(0xff525e50));
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        button.setContentDescription(name);
        button.setBackground(ripple(context, rounded(context, 0x125c4938, 0, 999), false));
        ForestMotion.bind(button);
        return button;
    }
}

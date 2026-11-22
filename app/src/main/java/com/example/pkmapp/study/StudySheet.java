package com.example.pkmapp.study;

import static com.example.pkmapp.forest.ForestUi.*;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
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
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import com.example.pkmapp.R;
import com.example.pkmapp.forest.ForestMotion;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayDeque;
import java.util.Deque;

/** A single modal window with back navigation, pinned actions and independently scrollable content. */
final class StudySheet extends BottomSheetDialog {
    static final int MOSS = 0xffe7edda;
    static final int OAT = 0xfff3e9da;
    static final int SAGE = 0xffe5ebe4;
    private static final int SHEET = 0xfffdfaf0;
    private static final int INK = 0xff283f2c;
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
            bottomInset = insets.isVisible(WindowInsetsCompat.Type.ime()) ? 0 : nav;
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
        private final TextView kickerView;
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
            header.setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 10));
            grip = new View(context);
            grip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            grip.setBackground(rounded(context, 0xffc6c3b4, 0, 3));
            LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4));
            gripParams.gravity = Gravity.CENTER_HORIZONTAL;
            header.addView(grip, gripParams);

            LinearLayout titleRow = row(context);
            backButton = iconButton(context, R.drawable.ic_study_sheet_back, "上一步");
            LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36));
            backParams.setMarginEnd(dp(context, 8));
            titleRow.addView(backButton, backParams);
            if (glyph != 0) {
                ImageView icon = new ImageView(context);
                icon.setImageResource(glyph);
                icon.setImageTintList(ColorStateList.valueOf(0xff355336));
                icon.setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9));
                icon.setBackground(rounded(context, 0xffe7efe3, 0, 11));
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                titleRow.addView(icon, new LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)));
            }
            LinearLayout titleStack = column(context);
            kickerView = text(context, defaultKicker(title), 10, 0xff7a8476);
            kickerView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            kickerView.setLetterSpacing(0.11f);
            kickerView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (kickerView.getText().length() == 0) {
                kickerView.setVisibility(View.GONE);
            }
            titleStack.addView(kickerView);

            TextView heading = heading(context, title, 20);
            heading.setTypeface(Typeface.create("serif", Typeface.BOLD));
            heading.setTextColor(0xff243624);
            heading.setLineSpacing(dp(context, 2), 1);
            ViewCompat.setAccessibilityHeading(heading, true);
            LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
            if (kickerView.getVisibility() == View.VISIBLE) {
                headingParams.topMargin = dp(context, 2);
            }
            titleStack.addView(heading, headingParams);

            LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(0, -2, 1);
            label.setMarginStart(glyph == 0 ? 0 : dp(context, 10));
            label.setMarginEnd(dp(context, 8));
            titleRow.addView(titleStack, label);
            closeButton = iconButton(context, R.drawable.ic_study_sheet_close, "关闭弹窗");
            titleRow.addView(closeButton, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));
            add(header, titleRow, 10);
            if (detail != null) {
                TextView subtitle = text(context, detail, 12, MUTED);
                subtitle.setLineSpacing(dp(context, 4), 1);
                add(header, subtitle, 6);
            }
            root.addView(header);

            content = column(context);
            // The content column shrinks at the viewport limit; the footer stays within reach.
            root.addView(content, new LinearLayout.LayoutParams(-1, -2, 1));
            above = column(context);
            above.setPadding(dp(context, 20), 0, dp(context, 20), 0);
            content.addView(above, new LinearLayout.LayoutParams(-1, -2));

            scroll = new NestedScrollView(context);
            scroll.setClipToPadding(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            body = column(context);
            body.setPadding(dp(context, 20), dp(context, 4), dp(context, 20), dp(context, 14));
            scroll.addView(body, new ViewGroup.LayoutParams(-1, -2));
            content.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1));

            footer = column(context);
            footer.setPadding(dp(context, 20), dp(context, 10), dp(context, 20), dp(context, 16));
            footer.setBackgroundColor(0xfffaf6ea);
            footer.setElevation(dp(context, 2));
            footer.setVisibility(View.GONE);
            root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

            actionsRow = row(context);
        }

        private static String defaultKicker(String title) {
            if (title == null) return "";
            if (title.contains("保存这段专注")) return "FOCUS RECEIPT";
            if (title.contains("学习已保存")) return "FOREST REWARD";
            if (title.contains("每日学习目标")) return "DAILY FOREST GOAL";
            if (title.contains("科目") || title.contains("学硕") || title.contains("专硕") || title.contains("学哪一门"))
                return "SUBJECT SHELF";
            if (title.contains("今天安排")) return "DAILY AGENDA";
            if (title.contains("计划")) return "STUDY PLAN";
            return "";
        }

        private void ensureActionsRow() {
            footer.setVisibility(View.VISIBLE);
            if (actionsRow.getParent() == null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                if (footer.getChildCount() > 0) lp.topMargin = dp(context, 4);
                footer.addView(actionsRow, lp);
            }
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
            params.setMargins(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 12));
            content.addView(list, params);
        }

        MaterialButton primary(String label, Runnable click) {
            ensureActionsRow();
            MaterialButton button = button(context, label, true);
            button.setMinHeight(dp(context, 46));
            button.setMinimumHeight(dp(context, 46));
            button.setCornerRadius(dp(context, 12));
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            button.setOnClickListener(v -> click.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(context, 46), 1.28f);
            actionsRow.addView(button, lp);
            syncActionMargins();
            return button;
        }

        void secondary(String label, Runnable click) {
            ensureActionsRow();
            TextView secondaryBtn = link(context, label, 0xff435043, click);
            secondaryBtn.setMinHeight(dp(context, 46));
            secondaryBtn.setMinimumHeight(dp(context, 46));
            secondaryBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            secondaryBtn.setBackground(ripple(context, rounded(context, 0xfff7f4ea, 0xffdcd8c8, 12), false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(context, 46), 1f);
            actionsRow.addView(secondaryBtn, 0, lp);
            syncActionMargins();
        }
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
        TextView title = text(context, name, 13, DEEP);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ViewCompat.setAccessibilityHeading(title, true);
        return title;
    }

    static LinearLayout item(Context context, String title, String detail, int glyph, int tint,
                             String end, boolean selected, Runnable click) {
        LinearLayout row = row(context);
        row.setMinimumHeight(dp(context, detail == null ? 53 : 64));
        row.setPadding(dp(context, 12), dp(context, 10), dp(context, 11), dp(context, 10));
        row.setBackground(ripple(context, rounded(context, tint, selected ? 0xff849b75 : 0, 16), false));
        row.setSelected(selected);
        if (glyph != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(glyph);
            icon.setImageTintList(ColorStateList.valueOf(DEEP));
            icon.setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9));
            icon.setBackground(rounded(context, tint == Color.TRANSPARENT ? MOSS : SHEET, 0, 11));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)));
        }
        LinearLayout copy = column(context);
        TextView name = text(context, title, 15, INK);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        copy.addView(name);
        if (detail != null && !detail.isEmpty()) {
            TextView supporting = text(context, detail, 12, MUTED);
            supporting.setLineSpacing(dp(context, 3), 1);
            add(copy, supporting, 5);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        if (glyph != 0) copyParams.setMarginStart(dp(context, 11));
        row.addView(copy, copyParams);
        if (end != null) {
            TextView trailing = text(context, end, end.equals("›") ? 21 : 12, DEEP);
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
            rounded(context, 0xffffffff, 0xff456444, 12)
        );
        background.addState(new int[0], rounded(context, 0xfff7f4ea, 0xffdcd8c8, 12));
        input.setBackground(background);
        input.setBackgroundTintList(null);
        input.setTextSize(14.5f);
        input.setTextColor(0xff243624);
        input.setHintTextColor(0xff8a9285);
        input.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11));
        input.setMinimumHeight(dp(context, 46));
    }

    static ImageButton iconButton(Context context, int image, String name) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(image);
        button.setImageTintList(ColorStateList.valueOf(0xff4c594a));
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        button.setContentDescription(name);
        button.setBackground(ripple(context, rounded(context, 0xfff3efe3, 0xffe0ddce, 11), false));
        ForestMotion.bind(button);
        return button;
    }
}

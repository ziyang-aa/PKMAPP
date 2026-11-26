package com.example.pkmapp.borrowing;

import static com.example.pkmapp.forest.ForestUi.*;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.forest.ForestCalendarView;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.ForestSegmentedControl;
import com.example.pkmapp.forest.ForestSheetHelper;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.life.LifeSharedViewModel;
import com.example.pkmapp.navigation.AppDestination;
import com.example.pkmapp.record.MoneyParser;
import com.example.pkmapp.record.RecordDateState;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class BorrowingFragment extends Fragment {
    public static final String ARG_DIRECTION = "borrowing_direction";
    public static final String ARG_AMOUNT_CENTS = "borrowing_amount_cents";
    public static final String ARG_OCCURRED_AT = "borrowing_occurred_at";
    public static final String ARG_SOURCE_TRANSACTION = "borrowing_source_transaction";

    private LifeSharedViewModel viewModel;
    private int filterTab = 0; // 0: 全部, 1: 我借出去, 2: 我借进来
    private ForestSegmentedControl filterBar;
    private RecyclerView recordList;
    private BorrowingListAdapter recordAdapter;
    private View emptyState;
    private TextView lendTotal;
    private TextView lendCount;
    private TextView borrowTotal;
    private TextView borrowCount;
    private TextView netSummary;
    private BottomSheetDialog activeSheet;

    public static BorrowingFragment newComposer(BorrowingDirection direction, long amountCents,
            long occurredAtMillis, @Nullable String sourceTransactionId) {
        BorrowingFragment fragment = new BorrowingFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DIRECTION, direction.name());
        args.putLong(ARG_AMOUNT_CENTS, amountCents);
        args.putLong(ARG_OCCURRED_AT, occurredAtMillis);
        if (sourceTransactionId != null) {
            args.putString(ARG_SOURCE_TRANSACTION, sourceTransactionId);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_borrowing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = LifeSharedViewModel.get(requireActivity());
        lendTotal = view.findViewById(R.id.borrowing_lend_total);
        lendCount = view.findViewById(R.id.borrowing_lend_count);
        borrowTotal = view.findViewById(R.id.borrowing_borrow_total);
        borrowCount = view.findViewById(R.id.borrowing_borrow_count);
        netSummary = view.findViewById(R.id.borrowing_net_summary);
        recordList = view.findViewById(R.id.borrowing_record_list);
        recordList.setLayoutManager(new LinearLayoutManager(requireContext()));
        recordList.setItemAnimator(null);
        recordList.setNestedScrollingEnabled(false);
        recordAdapter = new BorrowingListAdapter();
        recordList.setAdapter(recordAdapter);
        emptyState = view.findViewById(R.id.borrowing_empty_state);

        lendTotal.setFontFeatureSettings("tnum");
        borrowTotal.setFontFeatureSettings("tnum");
        netSummary.setFontFeatureSettings("tnum");

        View backBtn = view.findViewById(R.id.borrowing_back_button);
        backBtn.setBackground(ripple(requireContext(), rounded(requireContext(), 0x125c4938, 0, 999), false));
        backBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigateBackFromLifeTool());
        ForestMotion.bind(backBtn);

        TextView addBtn = view.findViewById(R.id.borrowing_add_button);
        addBtn.setBackground(ripple(requireContext(), rounded(requireContext(), 0xff456444, 0, 999), true));
        addBtn.setOnClickListener(v -> showComposerSheet(BorrowingDirection.LEND, 0L,
                System.currentTimeMillis(), null));
        ForestMotion.bind(addBtn);

        LinearLayout filterHost = view.findViewById(R.id.borrowing_filter_host);
        filterBar = new ForestSegmentedControl(
                requireContext(),
                new String[]{"全部", "借出", "借入"},
                null,
                filterTab,
                idx -> {
                    filterTab = idx;
                    renderRecords();
                }
        );
        filterHost.addView(filterBar, new LinearLayout.LayoutParams(-1, -2));

        viewModel.observe(getViewLifecycleOwner(), snapshot -> renderRecords(snapshot.getBorrowingRecords()));
        openComposerIfRequested();
    }

    private void openComposerIfRequested() {
        Bundle args = getArguments();
        if (args == null) return;
        String dirName = args.getString(ARG_DIRECTION);
        long amountCents = args.getLong(ARG_AMOUNT_CENTS, 0L);
        long occurredAt = args.getLong(ARG_OCCURRED_AT, 0L);
        String sourceTx = args.getString(ARG_SOURCE_TRANSACTION);
        if (dirName == null && amountCents <= 0L && sourceTx == null) return;
        BorrowingDirection dir = BorrowingDirection.LEND;
        if (dirName != null) {
            try {
                dir = BorrowingDirection.valueOf(dirName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        setArguments(null);
        long finalOccurred = occurredAt > 0L ? occurredAt : System.currentTimeMillis();
        BorrowingDirection finalDir = dir;
        requireView().post(() -> {
            if (isAdded()) showComposerSheet(finalDir, amountCents, finalOccurred, sourceTx);
        });
    }

    private void renderRecords() {
        if (viewModel != null) {
            renderRecords(viewModel.getCurrentSnapshot().getBorrowingRecords());
        }
    }

    private void renderRecords(List<BorrowingRecord> all) {
        if (recordList == null || !isAdded()) return;
        BorrowingSummary lend = BorrowingSummary.from(all, BorrowingDirection.LEND);
        BorrowingSummary borrow = BorrowingSummary.from(all, BorrowingDirection.BORROW);
        lendTotal.setText(MoneyParser.formatCents(lend.getTotalInCents()));
        lendCount.setText(lend.getRecordCount() + " 笔");
        borrowTotal.setText(MoneyParser.formatCents(borrow.getTotalInCents()));
        borrowCount.setText(borrow.getRecordCount() + " 笔");

        long netCents = lend.getTotalInCents() - borrow.getTotalInCents();
        if (netCents > 0) {
            netSummary.setText("应收净额 · " + MoneyParser.formatCents(netCents));
            netSummary.setTextColor(0xff9e4c37);
        } else if (netCents < 0) {
            netSummary.setText("待还净额 · " + MoneyParser.formatCents(-netCents));
            netSummary.setTextColor(DEEP);
        } else {
            netSummary.setText("往来已两清 · ¥0.00");
            netSummary.setTextColor(DEEP);
        }

        if (filterBar != null) {
            filterBar.setTabLabels(new CharSequence[]{
                    all.isEmpty() ? "全部" : "全部 " + all.size(),
                    lend.getRecordCount() == 0 ? "借出" : "借出 " + lend.getRecordCount(),
                    borrow.getRecordCount() == 0 ? "借入" : "借入 " + borrow.getRecordCount()
            });
        }

        List<BorrowingRecord> filtered = new ArrayList<>();
        for (BorrowingRecord rec : all) {
            if (filterTab == 1 && rec.getDirection() != BorrowingDirection.LEND) continue;
            if (filterTab == 2 && rec.getDirection() != BorrowingDirection.BORROW) continue;
            filtered.add(rec);
        }

        if (recordAdapter != null) {
            recordAdapter.submitList(BorrowingListAdapter.buildItems(filtered));
        }
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showComposerSheet(BorrowingDirection initialDirection, long initialAmountCents,
            long initialOccurredAt, @Nullable String initialSourceTxId) {
        Context ctx = requireContext();
        if (activeSheet != null) activeSheet.dismiss();
        BottomSheetDialog sheet = ForestSheetHelper.createSheet(ctx);
        activeSheet = sheet;
        sheet.setOnDismissListener(d -> {
            if (activeSheet == sheet) activeSheet = null;
        });

        LinearLayout root = column(ctx);
        root.setPadding(dp(ctx, 22), dp(ctx, 10), dp(ctx, 22), dp(ctx, 20));

        View grip = new View(ctx);
        grip.setBackground(rounded(ctx, 0x387b5c3b, 0, 3));
        LinearLayout.LayoutParams gripLp = new LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 4));
        gripLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(grip, gripLp);

        LinearLayout header = row(ctx);
        TextView title = heading(ctx, "记录一笔往来", 21);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setTextColor(0xff243624);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text(ctx, "✕", 15, 0xff525e50);
        close.setGravity(Gravity.CENTER);
        close.setBackground(ripple(ctx, rounded(ctx, 0x125c4938, 0, 999), false));
        close.setOnClickListener(v -> sheet.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)));
        ForestUi.add(root, header, 12);

        addSheetHairline(root, 12, 14);

        // 1. Direct Borderless Direction Chips
        BorrowingDirection[] chosenDir = {initialDirection};
        LinearLayout dirRow = row(ctx);
        TextView lendChip = text(ctx, "借出给别人", 13, 0xff243624);
        TextView borrowChip = text(ctx, "向别人借入", 13, 0xff243624);
        for (int i = 0; i < 2; i++) {
            TextView c = i == 0 ? lendChip : borrowChip;
            c.setGravity(Gravity.CENTER);
            c.setMinimumHeight(dp(ctx, 40));
            c.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i == 1) clp.setMarginStart(dp(ctx, 10));
            dirRow.addView(c, clp);
            ForestMotion.bind(c);
        }
        Runnable syncDir = () -> {
            boolean isLend = chosenDir[0] == BorrowingDirection.LEND;
            stylePill(lendChip, isLend);
            stylePill(borrowChip, !isLend);
        };
        lendChip.setOnClickListener(v -> { chosenDir[0] = BorrowingDirection.LEND; syncDir.run(); });
        borrowChip.setOnClickListener(v -> { chosenDir[0] = BorrowingDirection.BORROW; syncDir.run(); });
        syncDir.run();
        root.addView(dirRow, new LinearLayout.LayoutParams(-1, -2));

        // 2. Direct Borderless Underline Inputs (ZERO nested cards!)
        ForestUi.add(root, label(ctx, "金额（元）"), 16);
        EditText amountInput = underlineInput(ctx, "0.00", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setTextSize(24f);
        amountInput.setTypeface(Typeface.create("serif", Typeface.BOLD));
        amountInput.setFontFeatureSettings("tnum");
        if (initialAmountCents > 0L) {
            amountInput.setText(MoneyParser.formatCents(initialAmountCents).replace("¥", "").trim());
        }
        root.addView(amountInput, new LinearLayout.LayoutParams(-1, -2));

        ForestUi.add(root, label(ctx, "对方姓名"), 14);
        EditText personInput = underlineInput(ctx, "例如：小林", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        root.addView(personInput, new LinearLayout.LayoutParams(-1, -2));

        // 3. Date Row -> opens handcrafted ForestCalendarView sheet
        Calendar initCal = Calendar.getInstance();
        initCal.setTimeInMillis(initialOccurredAt);
        RecordDateState[] chosenDate = {new RecordDateState(initCal)};
        LinearLayout dateRow = row(ctx);
        dateRow.setMinimumHeight(dp(ctx, 48));
        dateRow.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 8));
        dateRow.setBackground(ripple(ctx, rounded(ctx, Color.TRANSPARENT, 0, 10), false));
        dateRow.addView(label(ctx, "发生日期"), new LinearLayout.LayoutParams(0, -2, 1));
        TextView dateVal = text(ctx, formatDateLabel(chosenDate[0]), 14, DEEP);
        dateVal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        dateVal.setFontFeatureSettings("tnum");
        dateRow.addView(dateVal);
        dateRow.setOnClickListener(v -> showForestDatePicker(chosenDate[0], picked -> {
            chosenDate[0] = picked;
            dateVal.setText(formatDateLabel(picked));
        }));
        ForestUi.add(root, dateRow, 10);
        addSheetHairline(root, 0, 12);

        root.addView(label(ctx, "备注（选填）"));
        EditText noteInput = underlineInput(ctx, "例如：晚饭先垫付", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(noteInput, new LinearLayout.LayoutParams(-1, -2));

        MaterialButton saveBtn = button(ctx, "保存这笔往来", true);
        saveBtn.setMinHeight(dp(ctx, 48));
        saveBtn.setMinimumHeight(dp(ctx, 48));
        saveBtn.setCornerRadius(dp(ctx, 14));
        saveBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        saveBtn.setOnClickListener(v -> {
            try {
                long amountCents = MoneyParser.parseYuanToCents(amountInput.getText().toString());
                String person = personInput.getText().toString().trim();
                if (person.isEmpty()) {
                    personInput.setError("请填写对方姓名");
                    personInput.requestFocus();
                    return;
                }
                viewModel.addBorrowingRecord(
                        chosenDir[0],
                        amountCents,
                        chosenDate[0].timeInMillis(),
                        person,
                        noteInput.getText().toString(),
                        initialSourceTxId
                );
                sheet.dismiss();
                Toast.makeText(ctx, "已记录这笔往来", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                amountInput.setError(e.getMessage());
                amountInput.requestFocus();
            }
        });
        ForestUi.add(root, saveBtn, 18);

        ForestSheetHelper.attachContentWithImeAvoidance(sheet, root);
        sheet.show();
    }

    private void showForestDatePicker(RecordDateState current, java.util.function.Consumer<RecordDateState> onConfirm) {
        Context ctx = requireContext();
        BottomSheetDialog calSheet = new BottomSheetDialog(ctx, R.style.ThemeOverlay_PkmAPP_StudySheet);
        calSheet.getBehavior().setSkipCollapsed(true);
        calSheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

        LinearLayout root = column(ctx);
        root.setPadding(dp(ctx, 22), dp(ctx, 10), dp(ctx, 22), dp(ctx, 20));

        View grip = new View(ctx);
        grip.setBackground(rounded(ctx, 0x387b5c3b, 0, 3));
        LinearLayout.LayoutParams gripLp = new LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 4));
        gripLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(grip, gripLp);

        LinearLayout header = row(ctx);
        TextView title = heading(ctx, "选择发生日期", 20);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setTextColor(0xff243624);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text(ctx, "✕", 15, 0xff525e50);
        close.setGravity(Gravity.CENTER);
        close.setBackground(ripple(ctx, rounded(ctx, 0x125c4938, 0, 999), false));
        close.setOnClickListener(v -> calSheet.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)));
        ForestUi.add(root, header, 12);

        addSheetHairline(root, 10, 8);
        String initKey = String.format(Locale.ROOT, "%04d-%02d-%02d", current.year(), current.month(), current.day());
        ForestCalendarView calView = new ForestCalendarView(ctx, initKey, null, null);
        root.addView(calView, new LinearLayout.LayoutParams(-1, -2));

        MaterialButton okBtn = button(ctx, "确认日期", true);
        okBtn.setMinHeight(dp(ctx, 48));
        okBtn.setCornerRadius(dp(ctx, 14));
        okBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        okBtn.setOnClickListener(v -> {
            String[] p = calView.selectedDayKey().split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 12, 0, 0);
            onConfirm.accept(new RecordDateState(cal));
            calSheet.dismiss();
        });
        ForestUi.add(root, okBtn, 12);

        calSheet.setContentView(root);
        calSheet.show();
    }

    private static String formatDateLabel(RecordDateState d) {
        return String.format(Locale.CHINA, "%d年%02d月%02d日 ›", d.year(), d.month(), d.day());
    }

    private void stylePill(TextView tv, boolean active) {
        tv.setTextColor(active ? 0xfffffcf3 : 0xff5a6458);
        tv.setTypeface(Typeface.create(active ? "sans-serif-medium" : "sans-serif",
                active ? Typeface.BOLD : Typeface.NORMAL));
        tv.setBackground(ripple(requireContext(),
                rounded(requireContext(), active ? 0xff456444 : 0xfff2eee3, 0, 999), active));
    }

    private TextView label(Context ctx, String textVal) {
        TextView tv = text(ctx, textVal, 12, MUTED);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return tv;
    }

    private EditText underlineInput(Context ctx, String hint, int inputType) {
        EditText et = new EditText(ctx);
        et.setSingleLine(true);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setTextSize(15f);
        et.setTextColor(0xff243624);
        et.setHintTextColor(0xff9ca195);
        et.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 10));
        et.setMinimumHeight(dp(ctx, 44));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, lineDrawable(ctx, 0xff456444, 1.5f));
        bg.addState(new int[0], lineDrawable(ctx, 0x185c4938, 1f));
        et.setBackground(bg);
        et.setBackgroundTintList(null);
        return et;
    }

    private Drawable lineDrawable(Context ctx, int color, float heightDp) {
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(color);
        LayerDrawable layer = new LayerDrawable(new Drawable[]{bar});
        layer.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        layer.setLayerHeight(0, Math.max(1, Math.round(heightDp * ctx.getResources().getDisplayMetrics().density)));
        return layer;
    }

    private void addSheetHairline(LinearLayout parent, int topDp, int bottomDp) {
        Context ctx = parent.getContext();
        View line = new View(ctx);
        line.setBackgroundColor(0x185c4938);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,
                Math.max(1, Math.round(0.75f * ctx.getResources().getDisplayMetrics().density)));
        lp.topMargin = dp(ctx, topDp);
        lp.bottomMargin = dp(ctx, bottomDp);
        parent.addView(line, lp);
    }

    @Override
    public void onDestroyView() {
        if (activeSheet != null) {
            activeSheet.dismiss();
            activeSheet = null;
        }
        filterBar = null;
        recordList = null;
        recordAdapter = null;
        emptyState = null;
        lendTotal = null;
        lendCount = null;
        borrowTotal = null;
        borrowCount = null;
        netSummary = null;
        super.onDestroyView();
    }
}

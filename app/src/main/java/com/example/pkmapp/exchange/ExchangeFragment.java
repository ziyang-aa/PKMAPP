package com.example.pkmapp.exchange;

import static com.example.pkmapp.forest.ForestUi.*;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.forest.ForestMotion;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.navigation.AppDestination;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

public final class ExchangeFragment extends Fragment {
    private static final long AUTO_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L;

    private ExchangeViewModel viewModel;
    private EditText amountInput;
    private TextView baseButton;
    private TextView baseName;
    private TextView quoteButton;
    private TextView quoteName;
    private TextView result;
    private TextView status;
    private TextView caption;
    private TextView error;
    private LinearLayout quickAmountsRow;
    private LinearLayout multiList;
    private BottomSheetDialog activeSheet;
    private boolean updatingInputFromState;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && viewModel != null) {
                viewModel.refreshRates();
                refreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MILLIS);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exchange, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = ExchangeViewModel.get(this);
        amountInput = view.findViewById(R.id.exchange_amount_input);
        baseButton = view.findViewById(R.id.exchange_base_button);
        baseName = view.findViewById(R.id.exchange_base_name);
        quoteButton = view.findViewById(R.id.exchange_quote_button);
        quoteName = view.findViewById(R.id.exchange_quote_name);
        result = view.findViewById(R.id.exchange_result);
        status = view.findViewById(R.id.exchange_status);
        caption = view.findViewById(R.id.exchange_rate_caption);
        error = view.findViewById(R.id.exchange_error);
        quickAmountsRow = view.findViewById(R.id.exchange_quick_amounts);
        multiList = view.findViewById(R.id.exchange_multi_list);

        amountInput.setFontFeatureSettings("tnum");
        result.setFontFeatureSettings("tnum");
        caption.setFontFeatureSettings("tnum");

        View backBtn = view.findViewById(R.id.exchange_back_button);
        backBtn.setBackground(ripple(requireContext(), rounded(requireContext(), 0x125c4938, 0, 999), false));
        backBtn.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.DETAILS));
        ForestMotion.bind(backBtn);

        View refreshBtn = view.findViewById(R.id.exchange_refresh_button);
        refreshBtn.setBackground(ripple(requireContext(), rounded(requireContext(), 0xffeef4ea, 0, 999), false));
        refreshBtn.setOnClickListener(v -> viewModel.refreshRates());
        ForestMotion.bind(refreshBtn);

        View swapBtn = view.findViewById(R.id.exchange_swap_button);
        swapBtn.setBackground(ripple(requireContext(), rounded(requireContext(), 0xffeef4ea, 0, 999), false));
        swapBtn.setOnClickListener(v -> viewModel.swapCurrencies());
        ForestMotion.bind(swapBtn);

        view.findViewById(R.id.exchange_base_selector).setOnClickListener(v -> showCurrencySheet(true));
        view.findViewById(R.id.exchange_quote_selector).setOnClickListener(v -> showCurrencySheet(false));
        result.setOnClickListener(v -> copyResultToClipboard());

        buildQuickAmountChips();

        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!updatingInputFromState) {
                    viewModel.setAmountInput(s == null ? "" : s.toString());
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        viewModel.observe(getViewLifecycleOwner(), this::renderState);
        viewModel.ensureInitialLoad();
    }

    private void buildQuickAmountChips() {
        if (quickAmountsRow == null) return;
        quickAmountsRow.removeAllViews();
        String[] presets = {"100", "500", "1000", "5000", "清空"};
        for (int i = 0; i < presets.length; i++) {
            String p = presets[i];
            boolean isClear = "清空".equals(p);
            TextView chip = text(requireContext(), p, 12, isClear ? MUTED : 0xff435043);
            chip.setGravity(Gravity.CENTER);
            chip.setFontFeatureSettings("tnum");
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setMinimumHeight(dp(requireContext(), 32));
            chip.setPadding(dp(requireContext(), 8), dp(requireContext(), 5),
                    dp(requireContext(), 8), dp(requireContext(), 5));
            chip.setBackground(ripple(requireContext(), rounded(requireContext(), 0xfff2eee3, 0, 999), false));
            chip.setOnClickListener(v -> {
                String target = isClear ? "" : p;
                amountInput.setText(target);
                amountInput.setSelection(amountInput.getText().length());
            });
            ForestMotion.bind(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.setMarginStart(dp(requireContext(), 6));
            quickAmountsRow.addView(chip, lp);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            refreshHandler.removeCallbacks(autoRefresh);
            refreshHandler.postDelayed(autoRefresh, AUTO_REFRESH_INTERVAL_MILLIS);
        }
    }

    @Override
    public void onPause() {
        refreshHandler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    private void renderState(ExchangeUiState state) {
        if (!isAdded() || result == null) return;
        baseButton.setText(state.baseInfo.code + " ▾");
        baseName.setText(state.baseInfo.name);
        quoteButton.setText(state.quoteInfo.code + " ▾");
        quoteName.setText(state.quoteInfo.name);

        if (!amountInput.getText().toString().equals(state.amountInput)) {
            updatingInputFromState = true;
            amountInput.setText(state.amountInput);
            amountInput.setSelection(amountInput.getText().length());
            updatingInputFromState = false;
        }

        status.setText(state.statusText);
        caption.setText(state.captionText);
        if (state.errorText == null) {
            error.setVisibility(View.GONE);
        } else {
            error.setText(state.errorText);
            error.setVisibility(View.VISIBLE);
        }
        result.setText(state.resultText);
        renderMultiCurrencyList(state);
    }

    private void renderMultiCurrencyList(ExchangeUiState state) {
        if (multiList == null || !isAdded()) return;
        multiList.removeAllViews();
        int rendered = 0;
        for (MultiCurrencyItem rowModel : state.multiItems) {
            if (rendered > 0) {
                View line = new View(requireContext());
                line.setBackgroundColor(0x165c4938);
                multiList.addView(line, new LinearLayout.LayoutParams(-1,
                        Math.max(1, Math.round(0.75f * getResources().getDisplayMetrics().density))));
            }
            LinearLayout item = row(requireContext());
            item.setMinimumHeight(dp(requireContext(), 54));
            item.setPadding(dp(requireContext(), 4), dp(requireContext(), 10),
                    dp(requireContext(), 4), dp(requireContext(), 10));
            boolean isCurrentQuote = rowModel.isCurrentQuote;
            item.setBackground(ripple(requireContext(),
                    rounded(requireContext(), isCurrentQuote ? 0x16456444 : Color.TRANSPARENT, 0, 12), false));

            LinearLayout left = column(requireContext());
            TextView codeTv = text(requireContext(), rowModel.code + " · " + rowModel.name, 14,
                    isCurrentQuote ? DEEP : 0xff243624);
            codeTv.setTypeface(Typeface.create("sans-serif-medium", isCurrentQuote ? Typeface.BOLD : Typeface.NORMAL));
            left.addView(codeTv);
            if (rowModel.unitRateText != null) {
                TextView sub = text(requireContext(), rowModel.unitRateText, 11, MUTED);
                sub.setFontFeatureSettings("tnum");
                ForestUi.add(left, sub, 3);
            }
            item.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

            TextView valTv = text(requireContext(), rowModel.convertedText, 16, isCurrentQuote ? DEEP : 0xff243624);
            valTv.setTypeface(Typeface.create("serif", Typeface.BOLD));
            valTv.setFontFeatureSettings("tnum");
            item.addView(valTv);

            item.setOnClickListener(v -> viewModel.selectQuoteCurrency(rowModel.code));
            ForestMotion.bind(item);
            multiList.addView(item, new LinearLayout.LayoutParams(-1, -2));
            rendered++;
        }
    }

    private void copyResultToClipboard() {
        if (result == null || viewModel == null) return;
        ExchangeUiState current = viewModel.getUiState().getValue();
        String val = result.getText().toString().trim();
        if (val.isEmpty() || "--".equals(val)) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("exchange_result", val + " " + current.quoteInfo.code));
            Toast.makeText(requireContext(), "已复制 " + val, Toast.LENGTH_SHORT).show();
        }
    }

    private void showCurrencySheet(boolean selectingBase) {
        Context ctx = requireContext();
        if (activeSheet != null) activeSheet.dismiss();
        BottomSheetDialog sheet = new BottomSheetDialog(ctx, R.style.ThemeOverlay_PkmAPP_StudySheet);
        activeSheet = sheet;
        sheet.setOnDismissListener(d -> {
            if (activeSheet == sheet) activeSheet = null;
        });
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

        LinearLayout root = column(ctx);
        root.setPadding(dp(ctx, 22), dp(ctx, 10), dp(ctx, 22), dp(ctx, 18));

        View grip = new View(ctx);
        grip.setBackground(rounded(ctx, 0x387b5c3b, 0, 3));
        LinearLayout.LayoutParams gripLp = new LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 4));
        gripLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(grip, gripLp);

        LinearLayout header = row(ctx);
        TextView title = heading(ctx, selectingBase ? "选择原币种" : "选择目标币种", 20);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setTextColor(0xff243624);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text(ctx, "✕", 15, 0xff525e50);
        close.setGravity(Gravity.CENTER);
        close.setBackground(ripple(ctx, rounded(ctx, 0x125c4938, 0, 999), false));
        close.setOnClickListener(v -> sheet.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)));
        ForestUi.add(root, header, 12);

        EditText search = new EditText(ctx);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setHint("搜索币种代码或名称，如 JPY / 欧元");
        search.setTextSize(14.5f);
        search.setTextColor(0xff243624);
        search.setHintTextColor(0xff9ca195);
        search.setBackground(null);
        search.setPadding(dp(ctx, 2), dp(ctx, 10), dp(ctx, 2), dp(ctx, 10));
        ForestUi.add(root, search, 8);

        View topRule = new View(ctx);
        topRule.setBackgroundColor(0x1a5c4938);
        root.addView(topRule, new LinearLayout.LayoutParams(-1, Math.max(1, dp(ctx, 1))));

        NestedScrollView listScroll = new NestedScrollView(ctx);
        LinearLayout list = column(ctx);
        listScroll.addView(list, new ViewGroup.LayoutParams(-1, -2));
        int maxH = Math.round(getResources().getDisplayMetrics().heightPixels * 0.56f);
        root.addView(listScroll, new LinearLayout.LayoutParams(-1, maxH));

        ExchangeUiState state = viewModel.getUiState().getValue();
        String currentSelected = selectingBase ? state.baseInfo.code : state.quoteInfo.code;
        Runnable populate = () -> {
            list.removeAllViews();
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int idx = 0;
            for (CurrencyInfo info : ExchangeViewModel.CURRENCIES) {
                if (!q.isEmpty() && !info.code.toLowerCase(Locale.ROOT).contains(q)
                        && !info.name.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                if (idx++ > 0) {
                    View line = new View(ctx);
                    line.setBackgroundColor(0x145c4938);
                    list.addView(line, new LinearLayout.LayoutParams(-1,
                            Math.max(1, Math.round(0.75f * getResources().getDisplayMetrics().density))));
                }
                boolean selected = info.code.equals(currentSelected);
                LinearLayout rowView = row(ctx);
                rowView.setMinimumHeight(dp(ctx, 54));
                rowView.setPadding(dp(ctx, 6), dp(ctx, 10), dp(ctx, 6), dp(ctx, 10));
                rowView.setBackground(ripple(ctx,
                        rounded(ctx, selected ? 0xffeef4ea : Color.TRANSPARENT, 0, 12), false));

                TextView sym = text(ctx, info.symbol, 14, selected ? DEEP : WOOD);
                sym.setGravity(Gravity.CENTER);
                sym.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                rowView.addView(sym, new LinearLayout.LayoutParams(dp(ctx, 42), -2));

                LinearLayout copy = column(ctx);
                TextView nameTv = text(ctx, info.code + "  " + info.name, 15, 0xff243624);
                nameTv.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
                copy.addView(nameTv);
                rowView.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

                if (selected) {
                    TextView check = text(ctx, "✓", 15, DEEP);
                    check.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    rowView.addView(check);
                }
                rowView.setOnClickListener(v -> {
                    sheet.dismiss();
                    if (selectingBase) {
                        viewModel.selectBaseCurrency(info.code);
                    } else {
                        viewModel.selectQuoteCurrency(info.code);
                    }
                });
                ForestMotion.bind(rowView);
                list.addView(rowView, new LinearLayout.LayoutParams(-1, -2));
            }
            if (idx == 0) {
                TextView empty = text(ctx, "没有找到匹配的币种", 13, MUTED);
                empty.setPadding(dp(ctx, 4), dp(ctx, 20), dp(ctx, 4), dp(ctx, 20));
                list.addView(empty);
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { populate.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        populate.run();
        sheet.setContentView(root);
        sheet.show();
    }

    @Override
    public void onDestroyView() {
        refreshHandler.removeCallbacks(autoRefresh);
        if (activeSheet != null) {
            activeSheet.dismiss();
            activeSheet = null;
        }
        amountInput = null;
        baseButton = null;
        baseName = null;
        quoteButton = null;
        quoteName = null;
        result = null;
        status = null;
        caption = null;
        error = null;
        quickAmountsRow = null;
        multiList = null;
        super.onDestroyView();
    }
}

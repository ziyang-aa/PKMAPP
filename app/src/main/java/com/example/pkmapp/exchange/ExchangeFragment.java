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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExchangeFragment extends Fragment {
    private static final long AUTO_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L;

    static final class CurrencyInfo {
        final String code;
        final String name;
        final String symbol;
        final int decimals;

        CurrencyInfo(String code, String name, String symbol, int decimals) {
            this.code = code;
            this.name = name;
            this.symbol = symbol;
            this.decimals = decimals;
        }
    }

    private static final List<CurrencyInfo> CURRENCIES = Arrays.asList(
            new CurrencyInfo("CNY", "人民币", "¥", 2),
            new CurrencyInfo("USD", "美元", "$", 2),
            new CurrencyInfo("EUR", "欧元", "€", 2),
            new CurrencyInfo("JPY", "日元", "¥", 0),
            new CurrencyInfo("HKD", "港元", "HK$", 2),
            new CurrencyInfo("GBP", "英镑", "£", 2),
            new CurrencyInfo("KRW", "韩元", "₩", 0),
            new CurrencyInfo("AUD", "澳元", "A$", 2),
            new CurrencyInfo("CAD", "加元", "C$", 2),
            new CurrencyInfo("SGD", "新加坡元", "S$", 2),
            new CurrencyInfo("CHF", "瑞士法郎", "CHF", 2),
            new CurrencyInfo("NZD", "新西兰元", "NZ$", 2),
            new CurrencyInfo("THB", "泰铢", "฿", 2),
            new CurrencyInfo("MYR", "马来西亚林吉特", "RM", 2),
            new CurrencyInfo("IDR", "印尼盾", "Rp", 0),
            new CurrencyInfo("PHP", "菲律宾比索", "₱", 2),
            new CurrencyInfo("INR", "印度卢比", "₹", 2),
            new CurrencyInfo("BRL", "巴西雷亚尔", "R$", 2),
            new CurrencyInfo("MXN", "墨西哥比索", "Mex$", 2),
            new CurrencyInfo("SEK", "瑞典克朗", "kr", 2),
            new CurrencyInfo("NOK", "挪威克朗", "kr", 2),
            new CurrencyInfo("TRY", "土耳其里拉", "₺", 2),
            new CurrencyInfo("ZAR", "南非兰特", "R", 2),
            new CurrencyInfo("PLN", "波兰兹罗提", "zł", 2)
    );

    private static final String[] POPULAR_QUOTES = {"USD", "EUR", "JPY", "HKD", "GBP", "KRW", "AUD", "SGD"};

    private ExchangeRateService service;
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
    private String baseCurrency = "CNY";
    private String quoteCurrency = "USD";
    private final Map<String, ExchangeRateSnapshot> ratesByQuote = new LinkedHashMap<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private long requestToken;

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && service != null) {
                loadRates();
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
        service = new ExchangeRateService(requireContext());
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
        refreshBtn.setOnClickListener(v -> loadRates());
        ForestMotion.bind(refreshBtn);

        View swapBtn = view.findViewById(R.id.exchange_swap_button);
        swapBtn.setBackground(ripple(requireContext(), rounded(requireContext(), 0xffeef4ea, 0, 999), false));
        swapBtn.setOnClickListener(v -> {
            String oldBase = baseCurrency;
            baseCurrency = quoteCurrency;
            quoteCurrency = oldBase;
            updateCurrencyLabels();
            loadRates();
        });
        ForestMotion.bind(swapBtn);

        View baseSel = view.findViewById(R.id.exchange_base_selector);
        View quoteSel = view.findViewById(R.id.exchange_quote_selector);
        baseSel.setOnClickListener(v -> showCurrencySheet(true));
        quoteSel.setOnClickListener(v -> showCurrencySheet(false));

        result.setOnClickListener(v -> copyResultToClipboard());

        buildQuickAmountChips();

        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderResult();
                renderMultiCurrencyList();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        updateCurrencyLabels();
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
                amountInput.setText(isClear ? "" : p);
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
        if (service != null) {
            loadRates();
            refreshHandler.removeCallbacks(autoRefresh);
            refreshHandler.postDelayed(autoRefresh, AUTO_REFRESH_INTERVAL_MILLIS);
        }
    }

    @Override
    public void onPause() {
        refreshHandler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    private void loadRates() {
        if (service == null || status == null) return;
        long currentRequest = ++requestToken;
        status.setText("正在联网同步最新汇率…");
        error.setVisibility(View.GONE);
        String[] allCodes = new String[CURRENCIES.size()];
        for (int i = 0; i < CURRENCIES.size(); i++) allCodes[i] = CURRENCIES.get(i).code;

        service.fetchBatch(baseCurrency, allCodes, new ExchangeRateService.BatchCallback() {
            @Override
            public void onSuccess(Map<String, ExchangeRateSnapshot> loaded, boolean fromCache) {
                if (!isAdded() || result == null || currentRequest != requestToken) return;
                ratesByQuote.clear();
                ratesByQuote.putAll(loaded);
                status.setText(fromCache ? "显示最近一次同步" : "刚刚同步 · " + syncTime() + " · 支持 " + CURRENCIES.size() + " 种货币");
                updateCaption();
                renderResult();
                renderMultiCurrencyList();
            }

            @Override
            public void onError(String message, Map<String, ExchangeRateSnapshot> cachedSnapshots) {
                if (!isAdded() || result == null || currentRequest != requestToken) return;
                // Fallback to single-pair endpoint if batch endpoint is unavailable
                service.fetch(baseCurrency, quoteCurrency, new ExchangeRateService.Callback() {
                    @Override
                    public void onSuccess(ExchangeRateSnapshot single, boolean fromCache) {
                        if (!isAdded() || result == null || currentRequest != requestToken) return;
                        ratesByQuote.clear();
                        ratesByQuote.putAll(cachedSnapshots);
                        ratesByQuote.put(single.getQuoteCurrency(), single);
                        status.setText("刚刚同步 · " + syncTime());
                        error.setVisibility(View.GONE);
                        updateCaption();
                        renderResult();
                        renderMultiCurrencyList();
                    }

                    @Override
                    public void onError(String singleErr, @Nullable ExchangeRateSnapshot singleCached) {
                        if (!isAdded() || result == null || currentRequest != requestToken) return;
                        ratesByQuote.clear();
                        ratesByQuote.putAll(cachedSnapshots);
                        if (singleCached != null) {
                            ratesByQuote.put(singleCached.getQuoteCurrency(), singleCached);
                        }
                        boolean hasAny = ratesByQuote.containsKey(quoteCurrency);
                        status.setText(hasAny ? "离线模式 · 使用最近一次同步" : "暂未获取到汇率");
                        error.setText(hasAny ? "网络暂不可用，已自动展示上次同步的参考汇率" : singleErr);
                        error.setVisibility(View.VISIBLE);
                        updateCaption();
                        renderResult();
                        renderMultiCurrencyList();
                    }
                });
            }
        });
    }

    private void updateCaption() {
        ExchangeRateSnapshot snap = currentSnapshot();
        if (snap == null) {
            caption.setText("");
            return;
        }
        String rateStr = snap.getRate().setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        String datePart = snap.getDate() == null || snap.getDate().isEmpty() ? "" : " · " + snap.getDate();
        caption.setText("1 " + snap.getBaseCurrency() + " ≈ " + rateStr + " " + snap.getQuoteCurrency() + datePart);
    }

    @Nullable
    private ExchangeRateSnapshot currentSnapshot() {
        if (baseCurrency.equals(quoteCurrency)) {
            return new ExchangeRateSnapshot(baseCurrency, quoteCurrency, BigDecimal.ONE, "即时");
        }
        return ratesByQuote.get(quoteCurrency);
    }

    private String syncTime() {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
    }

    @Nullable
    private BigDecimal parseInputAmount() {
        if (amountInput == null) return null;
        String raw = amountInput.getText().toString().trim();
        if (raw.isEmpty()) return null;
        try {
            BigDecimal val = new BigDecimal(raw);
            return val.compareTo(BigDecimal.ZERO) < 0 ? null : val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void renderResult() {
        if (result == null) return;
        ExchangeRateSnapshot snap = currentSnapshot();
        BigDecimal amount = parseInputAmount();
        if (snap == null) {
            result.setText("--");
            return;
        }
        if (amount == null) {
            result.setText("0.00");
            return;
        }
        CurrencyInfo quoteInfo = infoFor(quoteCurrency);
        BigDecimal converted = ExchangeRateCalculator.convert(amount, snap);
        if (quoteInfo.decimals == 0) {
            converted = converted.setScale(0, RoundingMode.HALF_UP);
        }
        result.setText(quoteInfo.symbol + " " + converted.toPlainString());
    }

    private void renderMultiCurrencyList() {
        if (multiList == null || !isAdded()) return;
        multiList.removeAllViews();
        BigDecimal amount = parseInputAmount();
        if (amount == null) amount = BigDecimal.valueOf(100);

        int rendered = 0;
        for (String code : POPULAR_QUOTES) {
            if (code.equals(baseCurrency)) continue;
            ExchangeRateSnapshot snap = ratesByQuote.get(code);
            CurrencyInfo info = infoFor(code);
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
            boolean isCurrentQuote = code.equals(quoteCurrency);
            item.setBackground(ripple(requireContext(),
                    rounded(requireContext(), isCurrentQuote ? 0x16456444 : Color.TRANSPARENT, 0, 12), false));

            LinearLayout left = column(requireContext());
            TextView codeTv = text(requireContext(), info.code + " · " + info.name, 14,
                    isCurrentQuote ? DEEP : 0xff243624);
            codeTv.setTypeface(Typeface.create("sans-serif-medium", isCurrentQuote ? Typeface.BOLD : Typeface.NORMAL));
            left.addView(codeTv);
            if (snap != null) {
                String unitRate = "1 " + baseCurrency + " ≈ "
                        + snap.getRate().setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                        + " " + code;
                TextView sub = text(requireContext(), unitRate, 11, MUTED);
                sub.setFontFeatureSettings("tnum");
                ForestUi.add(left, sub, 3);
            }
            item.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

            String valText = "--";
            if (snap != null) {
                BigDecimal converted = ExchangeRateCalculator.convert(amount, snap);
                if (info.decimals == 0) converted = converted.setScale(0, RoundingMode.HALF_UP);
                valText = info.symbol + " " + converted.toPlainString();
            }
            TextView valTv = text(requireContext(), valText, 16, isCurrentQuote ? DEEP : 0xff243624);
            valTv.setTypeface(Typeface.create("serif", Typeface.BOLD));
            valTv.setFontFeatureSettings("tnum");
            item.addView(valTv);

            item.setOnClickListener(v -> {
                quoteCurrency = code;
                updateCurrencyLabels();
                updateCaption();
                renderResult();
                renderMultiCurrencyList();
            });
            ForestMotion.bind(item);
            multiList.addView(item, new LinearLayout.LayoutParams(-1, -2));
            rendered++;
        }
    }

    private void copyResultToClipboard() {
        if (result == null) return;
        String val = result.getText().toString().trim();
        if (val.isEmpty() || "--".equals(val)) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("exchange_result", val + " " + quoteCurrency));
            Toast.makeText(requireContext(), "已复制 " + val, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCurrencyLabels() {
        if (baseButton == null) return;
        CurrencyInfo baseInfo = infoFor(baseCurrency);
        CurrencyInfo quoteInfo = infoFor(quoteCurrency);
        baseButton.setText(baseInfo.code + " ▾");
        baseName.setText(baseInfo.name);
        quoteButton.setText(quoteInfo.code + " ▾");
        quoteName.setText(quoteInfo.name);
    }

    private CurrencyInfo infoFor(String code) {
        for (CurrencyInfo info : CURRENCIES) {
            if (info.code.equals(code)) return info;
        }
        return new CurrencyInfo(code, code, code, 2);
    }

    private void showCurrencySheet(boolean selectingBase) {
        Context ctx = requireContext();
        BottomSheetDialog sheet = new BottomSheetDialog(ctx, R.style.ThemeOverlay_PkmAPP_StudySheet);
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

        // Borderless editorial search input directly on porcelain sheet
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

        String currentSelected = selectingBase ? baseCurrency : quoteCurrency;
        Runnable populate = () -> {
            list.removeAllViews();
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int idx = 0;
            for (CurrencyInfo info : CURRENCIES) {
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
                    if (selectingBase) {
                        baseCurrency = info.code;
                        updateCurrencyLabels();
                        sheet.dismiss();
                        loadRates();
                    } else {
                        quoteCurrency = info.code;
                        updateCurrencyLabels();
                        sheet.dismiss();
                        if (ratesByQuote.containsKey(quoteCurrency) || baseCurrency.equals(quoteCurrency)) {
                            updateCaption();
                            renderResult();
                            renderMultiCurrencyList();
                        } else {
                            loadRates();
                        }
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
        if (service != null) service.close();
        service = null;
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
        ratesByQuote.clear();
        super.onDestroyView();
    }
}

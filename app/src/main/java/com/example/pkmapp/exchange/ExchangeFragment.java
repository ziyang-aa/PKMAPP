package com.example.pkmapp.exchange;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.navigation.AppDestination;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ExchangeFragment extends Fragment {
    private static final long AUTO_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L;
    private static final List<String> CURRENCIES = Arrays.asList("CNY", "USD", "EUR", "JPY", "HKD");
    private static final List<String> CURRENCY_NAMES = Arrays.asList("人民币", "美元", "欧元", "日元", "港币");

    private ExchangeRateService service;
    private EditText amountInput;
    private TextView baseButton;
    private TextView quoteButton;
    private TextView result;
    private TextView status;
    private TextView caption;
    private TextView error;
    private String baseCurrency = "CNY";
    private String quoteCurrency = "USD";
    private ExchangeRateSnapshot snapshot;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private long requestToken;
    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && service != null) {
                loadRate();
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
        quoteButton = view.findViewById(R.id.exchange_quote_button);
        result = view.findViewById(R.id.exchange_result);
        status = view.findViewById(R.id.exchange_status);
        caption = view.findViewById(R.id.exchange_rate_caption);
        error = view.findViewById(R.id.exchange_error);

        view.findViewById(R.id.exchange_back_button).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showDestination(AppDestination.DETAILS));
        baseButton.setOnClickListener(v -> showCurrencyPopup(baseButton, true));
        quoteButton.setOnClickListener(v -> showCurrencyPopup(quoteButton, false));
        view.findViewById(R.id.exchange_swap_button).setOnClickListener(v -> {
            String oldBase = baseCurrency;
            baseCurrency = quoteCurrency;
            quoteCurrency = oldBase;
            updateCurrencyLabels();
            loadRate();
        });
        view.findViewById(R.id.exchange_refresh_button).setOnClickListener(v -> loadRate());
        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderResult();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        updateCurrencyLabels();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (service != null) {
            loadRate();
            refreshHandler.removeCallbacks(autoRefresh);
            refreshHandler.postDelayed(autoRefresh, AUTO_REFRESH_INTERVAL_MILLIS);
        }
    }

    @Override
    public void onPause() {
        refreshHandler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    private void loadRate() {
        if (service == null || status == null) {
            return;
        }
        long currentRequest = ++requestToken;
        status.setText("正在联网同步最新汇率…");
        error.setVisibility(View.GONE);
        service.fetch(baseCurrency, quoteCurrency, new ExchangeRateService.Callback() {
            @Override
            public void onSuccess(ExchangeRateSnapshot loaded, boolean fromCache) {
                if (!isAdded() || result == null || currentRequest != requestToken) return;
                snapshot = loaded;
                status.setText(fromCache ? "显示最近一次同步" : "刚刚同步 · " + syncTime());
                caption.setText("1 " + loaded.getBaseCurrency() + " ≈ "
                        + loaded.getRate().stripTrailingZeros().toPlainString() + " "
                        + loaded.getQuoteCurrency() + " · " + loaded.getDate());
                renderResult();
            }

            @Override
            public void onError(String message, @Nullable ExchangeRateSnapshot cachedSnapshot) {
                if (!isAdded() || result == null || currentRequest != requestToken) return;
                snapshot = cachedSnapshot;
                status.setText(cachedSnapshot == null ? "还没有可用的汇率" : "当前显示最近一次同步");
                error.setText(cachedSnapshot == null ? message : "网络暂不可用，已使用最近一次同步数据");
                error.setVisibility(View.VISIBLE);
                if (cachedSnapshot != null) {
                    caption.setText("1 " + cachedSnapshot.getBaseCurrency() + " ≈ "
                            + cachedSnapshot.getRate().stripTrailingZeros().toPlainString() + " "
                            + cachedSnapshot.getQuoteCurrency() + " · " + cachedSnapshot.getDate());
                } else {
                    caption.setText("");
                }
                renderResult();
            }
        });
    }

    private String syncTime() {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
    }

    private void renderResult() {
        if (result == null || snapshot == null) {
            if (result != null) result.setText("输入金额后显示结果");
            return;
        }
        String raw = amountInput.getText().toString().trim();
        if (raw.isEmpty()) {
            result.setText("输入金额后显示结果");
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(raw);
            BigDecimal converted = ExchangeRateCalculator.convert(amount, snapshot)
                    .setScale(2, RoundingMode.HALF_UP);
            result.setText(formatAmount(amount, baseCurrency) + "  →  "
                    + formatAmount(converted, quoteCurrency));
        } catch (IllegalArgumentException exception) {
            result.setText("请输入有效金额");
        }
    }

    private String formatAmount(BigDecimal amount, String currency) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency;
    }

    private void updateCurrencyLabels() {
        if (baseButton == null) return;
        baseButton.setText(currencyLabel(baseCurrency));
        quoteButton.setText(currencyLabel(quoteCurrency));
    }

    private String currencyLabel(String code) {
        int index = CURRENCIES.indexOf(code);
        return code + " " + CURRENCY_NAMES.get(index);
    }

    private void showCurrencyPopup(TextView anchor, boolean selectingBase) {
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        list.setBackgroundResource(R.drawable.bg_details_picker_sheet);
        PopupWindow popup = new PopupWindow(list, anchor.getWidth(), dp(300), true);
        popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popup.setElevation(dp(8));
        for (String currency : CURRENCIES) {
            TextView option = new TextView(requireContext());
            option.setText(currencyLabel(currency));
            option.setTextSize(14);
            option.setGravity(Gravity.CENTER);
            option.setMinHeight(dp(48));
            option.setTextColor(ContextCompat.getColor(requireContext(), R.color.wood_brown));
            option.setBackgroundResource(R.drawable.bg_details_picker_option);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
            params.bottomMargin = dp(4);
            list.addView(option, params);
            option.setOnClickListener(v -> {
                if (selectingBase) baseCurrency = currency; else quoteCurrency = currency;
                updateCurrencyLabels();
                popup.dismiss();
                loadRate();
            });
        }
        popup.showAsDropDown(anchor, 0, -anchor.getHeight());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        refreshHandler.removeCallbacks(autoRefresh);
        if (service != null) service.close();
        service = null;
        amountInput = null;
        baseButton = null;
        quoteButton = null;
        result = null;
        status = null;
        caption = null;
        error = null;
        snapshot = null;
        super.onDestroyView();
    }
}

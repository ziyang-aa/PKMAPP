package com.example.pkmapp.exchange;

import java.math.BigDecimal;
import java.util.Objects;

public final class ExchangeRateSnapshot {
    private final String baseCurrency;
    private final String quoteCurrency;
    private final BigDecimal rate;
    private final String date;

    public ExchangeRateSnapshot(String baseCurrency, String quoteCurrency, BigDecimal rate,
            String date) {
        this.baseCurrency = required(baseCurrency, "原币种不能为空").toUpperCase(java.util.Locale.US);
        this.quoteCurrency = required(quoteCurrency, "目标币种不能为空").toUpperCase(java.util.Locale.US);
        this.rate = Objects.requireNonNull(rate, "汇率不能为空");
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("汇率必须大于零");
        }
        this.date = required(date, "汇率日期不能为空");
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getDate() {
        return date;
    }

    private static String required(String value, String message) {
        String trimmed = Objects.requireNonNull(value, message).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}

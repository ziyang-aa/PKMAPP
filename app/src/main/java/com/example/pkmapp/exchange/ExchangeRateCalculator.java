package com.example.pkmapp.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class ExchangeRateCalculator {
    private ExchangeRateCalculator() {
    }

    public static BigDecimal convert(BigDecimal amount, ExchangeRateSnapshot snapshot) {
        Objects.requireNonNull(amount, "金额不能为空");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        Objects.requireNonNull(snapshot, "汇率不能为空");
        return amount.multiply(snapshot.getRate()).setScale(2, RoundingMode.HALF_UP);
    }
}

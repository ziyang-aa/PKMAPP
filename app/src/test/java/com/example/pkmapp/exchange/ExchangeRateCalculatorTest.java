package com.example.pkmapp.exchange;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.math.BigDecimal;

public final class ExchangeRateCalculatorTest {
    @Test
    public void convertsAmountWithTwoDecimalPlaces() {
        ExchangeRateSnapshot snapshot = new ExchangeRateSnapshot("CNY", "USD",
                new BigDecimal("0.13765"), "2026-09-08");

        BigDecimal result = ExchangeRateCalculator.convert(new BigDecimal("100.00"), snapshot);

        assertEquals(new BigDecimal("13.77"), result);
    }
}

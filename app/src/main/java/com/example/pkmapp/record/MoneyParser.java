package com.example.pkmapp.record;

import java.math.BigDecimal;

public final class MoneyParser {
    private static final String AMOUNT_PATTERN = "[0-9]+(?:\\.[0-9]{1,2})?";

    private MoneyParser() {
    }

    public static long parseYuanToCents(String amountText) {
        if (amountText == null) {
            throw new IllegalArgumentException("请输入金额");
        }
        String normalized = amountText.trim();
        if (!normalized.matches(AMOUNT_PATTERN)) {
            throw new IllegalArgumentException("金额最多保留两位小数");
        }
        try {
            long cents = new BigDecimal(normalized).movePointRight(2).longValueExact();
            if (cents <= 0L) {
                throw new IllegalArgumentException("金额必须大于零");
            }
            return cents;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("请输入有效金额", exception);
        }
    }

    public static String formatCents(long cents) {
        return "¥" + BigDecimal.valueOf(cents, 2).toPlainString();
    }
}

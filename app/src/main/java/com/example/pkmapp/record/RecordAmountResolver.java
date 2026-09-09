package com.example.pkmapp.record;

/** Resolves the amount field, falling back to the visible calculator expression. */
public final class RecordAmountResolver {
    private RecordAmountResolver() {
    }

    public static long resolveCents(String amountText, String calculatorExpression) {
        if (amountText != null && !amountText.trim().isEmpty()) {
            return MoneyParser.parseYuanToCents(amountText);
        }
        if (calculatorExpression == null || calculatorExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入金额");
        }
        CalculatorEngine calculator = new CalculatorEngine();
        for (int index = 0; index < calculatorExpression.length(); index++) {
            calculator.append(String.valueOf(calculatorExpression.charAt(index)));
        }
        return calculator.evaluateToCents();
    }
}

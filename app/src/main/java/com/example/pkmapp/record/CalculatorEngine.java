package com.example.pkmapp.record;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;

public final class CalculatorEngine {
    private final StringBuilder expression = new StringBuilder();

    public void append(String token) {
        if (token == null || token.length() != 1 || !isAllowedToken(token.charAt(0))) {
            throw new IllegalArgumentException("不支持的计算符号");
        }
        expression.append(token);
    }

    public void backspace() {
        if (expression.length() > 0) {
            expression.deleteCharAt(expression.length() - 1);
        }
    }

    public void clear() {
        expression.setLength(0);
    }

    public String getExpression() {
        return expression.toString();
    }

    public long evaluateToCents() {
        BigDecimal yuan = evaluateExpression(expression.toString());
        try {
            long cents = yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            if (cents <= 0L) {
                throw new IllegalArgumentException("计算结果必须大于零");
            }
            return cents;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("计算结果过大", exception);
        }
    }

    private static BigDecimal evaluateExpression(String source) {
        if (source.isEmpty()) {
            throw new IllegalArgumentException("请输入完整算式");
        }
        Deque<BigDecimal> values = new ArrayDeque<>();
        Deque<Character> operators = new ArrayDeque<>();
        boolean expectingNumber = true;
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (isNumberCharacter(current)) {
                if (!expectingNumber) {
                    throw new IllegalArgumentException("请输入完整算式");
                }
                int start = index;
                int decimalPoints = 0;
                while (index < source.length() && isNumberCharacter(source.charAt(index))) {
                    if (source.charAt(index) == '.') {
                        decimalPoints++;
                    }
                    index++;
                }
                String number = source.substring(start, index);
                if (decimalPoints > 1 || ".".equals(number)) {
                    throw new IllegalArgumentException("请输入有效数字");
                }
                values.push(new BigDecimal(number));
                expectingNumber = false;
                continue;
            }
            if (!isOperator(current) || expectingNumber) {
                throw new IllegalArgumentException("请输入完整算式");
            }
            while (!operators.isEmpty() && precedence(operators.peek()) >= precedence(current)) {
                apply(values, operators.pop());
            }
            operators.push(current);
            expectingNumber = true;
            index++;
        }
        if (expectingNumber) {
            throw new IllegalArgumentException("请输入完整算式");
        }
        while (!operators.isEmpty()) {
            apply(values, operators.pop());
        }
        if (values.size() != 1) {
            throw new IllegalArgumentException("请输入完整算式");
        }
        return values.pop();
    }

    private static void apply(Deque<BigDecimal> values, char operator) {
        if (values.size() < 2) {
            throw new IllegalArgumentException("请输入完整算式");
        }
        BigDecimal right = values.pop();
        BigDecimal left = values.pop();
        switch (operator) {
            case '+':
                values.push(left.add(right));
                break;
            case '-':
                values.push(left.subtract(right));
                break;
            case '×':
                values.push(left.multiply(right));
                break;
            case '÷':
                if (BigDecimal.ZERO.compareTo(right) == 0) {
                    throw new ArithmeticException("除数不能为零");
                }
                values.push(left.divide(right, 8, RoundingMode.HALF_UP));
                break;
            default:
                throw new IllegalArgumentException("不支持的计算符号");
        }
    }

    private static boolean isAllowedToken(char value) {
        return isNumberCharacter(value) || isOperator(value);
    }

    private static boolean isNumberCharacter(char value) {
        return (value >= '0' && value <= '9') || value == '.';
    }

    private static boolean isOperator(char value) {
        return value == '+' || value == '-' || value == '×' || value == '÷';
    }

    private static int precedence(char operator) {
        return operator == '×' || operator == '÷' ? 2 : 1;
    }
}

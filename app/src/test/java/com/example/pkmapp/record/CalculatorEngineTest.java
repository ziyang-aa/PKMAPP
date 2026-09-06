package com.example.pkmapp.record;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CalculatorEngineTest {
    @Test
    public void evaluateToCents_respectsMultiplyBeforeAdd() {
        CalculatorEngine calculator = new CalculatorEngine();
        calculator.append("1");
        calculator.append("+");
        calculator.append("2");
        calculator.append("×");
        calculator.append("3");

        assertEquals(700L, calculator.evaluateToCents());
    }

    @Test
    public void backspace_removesTheLastToken() {
        CalculatorEngine calculator = new CalculatorEngine();
        calculator.append("1");
        calculator.append("2");
        calculator.backspace();

        assertEquals("1", calculator.getExpression());
    }

    @Test(expected = ArithmeticException.class)
    public void evaluateToCents_rejectsDivisionByZero() {
        CalculatorEngine calculator = new CalculatorEngine();
        calculator.append("8");
        calculator.append("÷");
        calculator.append("0");

        calculator.evaluateToCents();
    }
}

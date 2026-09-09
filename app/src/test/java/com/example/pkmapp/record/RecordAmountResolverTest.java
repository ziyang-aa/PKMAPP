package com.example.pkmapp.record;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RecordAmountResolverTest {
    @Test
    public void usesCalculatorExpressionWhenAmountFieldIsEmpty() {
        assertEquals(725L, RecordAmountResolver.resolveCents("", "7+0.25"));
    }
}

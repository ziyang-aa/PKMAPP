package com.example.pkmapp.record;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MoneyParserTest {
    @Test
    public void parseYuanToCents_acceptsWholeAndTwoDecimalAmounts() {
        assertEquals(1_258L, MoneyParser.parseYuanToCents("12.58"));
        assertEquals(700L, MoneyParser.parseYuanToCents("7"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseYuanToCents_rejectsMoreThanTwoDecimalPlaces() {
        MoneyParser.parseYuanToCents("12.345");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseYuanToCents_rejectsZeroAmount() {
        MoneyParser.parseYuanToCents("0");
    }

    @Test
    public void formatCents_returnsTwoDecimalYuanText() {
        assertEquals("¥12.58", MoneyParser.formatCents(1_258L));
    }
}

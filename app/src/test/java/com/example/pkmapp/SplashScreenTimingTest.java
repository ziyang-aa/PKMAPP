package com.example.pkmapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SplashScreenTimingTest {
    @Test
    public void displayDuration_isTwoSeconds() {
        assertEquals(2_000L, SplashScreenTiming.displayDurationMillis());
    }
}

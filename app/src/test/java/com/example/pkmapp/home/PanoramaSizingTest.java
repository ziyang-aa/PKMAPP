package com.example.pkmapp.home;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PanoramaSizingTest {
    @Test
    public void panorama_isWiderThanTheViewport() {
        assertEquals(1080, PanoramaSizing.requiredWidthPx(360));
    }

    @Test
    public void panorama_neverShrinksBelowConfiguredMinimum() {
        assertEquals(1080, PanoramaSizing.requiredWidthPx(200));
    }
}

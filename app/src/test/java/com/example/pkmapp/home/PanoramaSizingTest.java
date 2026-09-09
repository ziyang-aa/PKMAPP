package com.example.pkmapp.home;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PanoramaSizingTest {
    @Test
    public void panorama_isWiderThanTheViewport() {
        assertEquals(1440, PanoramaSizing.requiredWidthPx(360));
    }

    @Test
    public void panorama_neverShrinksBelowConfiguredMinimum() {
        assertEquals(1080, PanoramaSizing.requiredWidthPx(200));
    }

    @Test
    public void panorama_expandsToFourViewportWidths() {
        assertEquals(5376, PanoramaSizing.requiredWidthPx(1344));
    }

    @Test
    public void panorama_startsAtHorizontalCenter() {
        assertEquals(2016, PanoramaSizing.initialScrollPx(5376, 1344));
    }
}

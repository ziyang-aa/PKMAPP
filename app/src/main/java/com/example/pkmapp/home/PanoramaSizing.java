package com.example.pkmapp.home;

public final class PanoramaSizing {
    private static final int PANORAMA_VIEWPORT_MULTIPLIER = 4;

    private PanoramaSizing() {
    }

    public static int requiredWidthPx(int viewportWidthPx) {
        return Math.max(1080, viewportWidthPx * PANORAMA_VIEWPORT_MULTIPLIER);
    }

    public static int initialScrollPx(int sceneWidthPx, int viewportWidthPx) {
        return Math.max(0, (sceneWidthPx - viewportWidthPx) / 2);
    }
}

package com.example.pkmapp.home;

public final class PanoramaSizing {
    private PanoramaSizing() {
    }

    public static int requiredWidthPx(int viewportWidthPx) {
        return Math.max(1080, viewportWidthPx * 3);
    }
}

package com.example.pkmapp.profile;

/** Small, UI-independent calculations used by the avatar crop surface. */
public final class AvatarCropMath {
    private AvatarCropMath() {
    }

    public static int[] centerSquareSourceRect(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Bitmap dimensions must be positive");
        }
        int side = Math.min(width, height);
        int left = (width - side) / 2;
        int top = (height - side) / 2;
        return new int[]{left, top, left + side, top + side};
    }

    public static float minimumScale(int bitmapWidth, int bitmapHeight, float cropSize) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || cropSize <= 0f) {
            throw new IllegalArgumentException("Bitmap dimensions and crop size must be positive");
        }
        return Math.max(cropSize / bitmapWidth, cropSize / bitmapHeight);
    }
}

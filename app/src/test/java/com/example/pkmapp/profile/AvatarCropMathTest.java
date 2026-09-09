package com.example.pkmapp.profile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AvatarCropMathTest {
    @Test
    public void centerSquareSourceRectKeepsTheWholeShortEdge() {
        assertArrayEquals(new int[]{200, 0, 1000, 800},
                AvatarCropMath.centerSquareSourceRect(1200, 800));
    }

    @Test
    public void minimumScaleCoversTheCropSquare() {
        assertEquals(0.35f, AvatarCropMath.minimumScale(1200, 800, 280f), 0.0001f);
    }
}

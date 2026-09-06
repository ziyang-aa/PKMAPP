package com.example.pkmapp;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public final class ForestThemeResourceTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void forestPalette_matchesApprovedDesign() {
        assertEquals(Color.rgb(245, 241, 231), ContextCompat.getColor(context, R.color.cream_background));
        assertEquals(Color.rgb(78, 129, 70), ContextCompat.getColor(context, R.color.forest_green));
        assertEquals(Color.rgb(199, 91, 75), ContextCompat.getColor(context, R.color.expense_red));
        assertEquals(Color.rgb(59, 53, 44), ContextCompat.getColor(context, R.color.ink));
    }
}

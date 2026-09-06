package com.example.pkmapp.navigation;

import static com.example.pkmapp.navigation.AppDestination.CHARTS;
import static com.example.pkmapp.navigation.AppDestination.DETAILS;
import static com.example.pkmapp.navigation.AppDestination.HOME;
import static com.example.pkmapp.navigation.AppDestination.PROFILE;
import static com.example.pkmapp.navigation.AppDestination.RECORD;
import static com.example.pkmapp.navigation.AppDestination.SAVINGS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;

import org.junit.Test;

public final class AppDestinationTest {
    @Test
    public void featureDestinations_haveExactlyFiveBottomBarItems() {
        assertEquals(
                Arrays.asList(DETAILS, CHARTS, RECORD, SAVINGS, PROFILE),
                AppDestination.bottomBarDestinations());
    }

    @Test
    public void home_isNotABottomBarItem() {
        assertFalse(AppDestination.bottomBarDestinations().contains(HOME));
    }
}

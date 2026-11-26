package com.example.pkmapp.navigation;

import static com.example.pkmapp.navigation.AppDestination.CHARTS;
import static com.example.pkmapp.navigation.AppDestination.DETAILS;
import static com.example.pkmapp.navigation.AppDestination.HOME;
import static com.example.pkmapp.navigation.AppDestination.RECORD;
import static com.example.pkmapp.navigation.AppDestination.SAVINGS;
import static com.example.pkmapp.navigation.AppDestination.VAULT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public final class AppDestinationTest {
    @Test
    public void featureDestinations_haveExactlyFourSymmetricalBottomBarItems() {
        assertEquals(
                Arrays.asList(DETAILS, CHARTS, SAVINGS, VAULT),
                AppDestination.bottomBarDestinations());
    }

    @Test
    public void home_isNotABottomBarItem() {
        assertFalse(AppDestination.bottomBarDestinations().contains(HOME));
    }

    @Test
    public void floatingRecordFab_showsOnlyOnLifeOverviewTabs() {
        assertTrue(DETAILS.showsLifeRecordFab());
        assertTrue(CHARTS.showsLifeRecordFab());
        assertTrue(SAVINGS.showsLifeRecordFab());
        assertFalse(VAULT.showsLifeRecordFab());
        assertFalse(RECORD.showsLifeRecordFab());
        assertFalse(HOME.showsLifeRecordFab());
    }
}

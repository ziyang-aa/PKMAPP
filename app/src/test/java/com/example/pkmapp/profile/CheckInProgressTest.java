package com.example.pkmapp.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class CheckInProgressTest {
    @Test
    public void canCheckInOnlyOnTheCurrentDay() {
        Set<String> checkedDates = new HashSet<>(Arrays.asList("2026-09-06"));

        assertTrue(CheckInProgress.canCheckInToday("2026-09-07", checkedDates));
        assertFalse(CheckInProgress.canCheckInToday("2026-09-06", checkedDates));
    }

    @Test
    public void addingACheckInNeverAllowsBackfillingAnotherDay() {
        Set<String> checkedDates = new HashSet<>();

        assertFalse(CheckInProgress.checkInToday("2026-09-07", "2026-09-06", checkedDates));
        assertTrue(CheckInProgress.checkInToday("2026-09-07", "2026-09-07", checkedDates));

        assertEquals(new HashSet<>(Arrays.asList("2026-09-07")), checkedDates);
        assertFalse(checkedDates.contains("2026-09-08"));
    }

    @Test
    public void levelIsCappedAtEight() {
        assertEquals(1, CheckInProgress.levelForDays(0));
        assertEquals(4, CheckInProgress.levelForDays(21));
        assertEquals(8, CheckInProgress.levelForDays(365));
        assertEquals(8, CheckInProgress.levelForDays(1000));
        assertEquals("森林贤者", CheckInProgress.nameForLevel(8));
        assertEquals(21, CheckInProgress.currentThresholdForLevel(4));
        assertEquals(45, CheckInProgress.nextThresholdForLevel(4));
    }
}

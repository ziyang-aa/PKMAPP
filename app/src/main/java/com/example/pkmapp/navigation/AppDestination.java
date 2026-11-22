package com.example.pkmapp.navigation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum AppDestination {
    HOME,
    DETAILS,
    CHARTS,
    RECORD,
    SAVINGS,
    PROFILE,
    EXCHANGE,
    BORROWING,
    STUDY_FOCUS,
    STUDY_TODO,
    STUDY_STATS,
    STUDY_GARDEN;

    private static final List<AppDestination> BOTTOM_BAR_DESTINATIONS =
            Collections.unmodifiableList(Arrays.asList(DETAILS, CHARTS, RECORD, SAVINGS, PROFILE));

    public static List<AppDestination> bottomBarDestinations() {
        return BOTTOM_BAR_DESTINATIONS;
    }

    public boolean isStudy() {
        return this == STUDY_FOCUS || this == STUDY_TODO || this == STUDY_STATS || this == STUDY_GARDEN;
    }
}

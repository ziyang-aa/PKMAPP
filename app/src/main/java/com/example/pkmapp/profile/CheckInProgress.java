package com.example.pkmapp.profile;

import java.util.Set;

/** Pure check-in rules shared by the profile UI and tests. */
public final class CheckInProgress {
    private static final int[] LEVEL_THRESHOLDS = {0, 7, 14, 21, 45, 90, 180, 365};
    private static final String[] LEVEL_NAMES = {
            "林间新芽", "见习护林员", "青叶学徒", "小树林守护者",
            "森林巡游者", "古树伙伴", "森林领主", "森林贤者"
    };

    private CheckInProgress() {
    }

    public static boolean canCheckInToday(String today, Set<String> checkedDates) {
        return today != null && !today.isEmpty() && !checkedDates.contains(today);
    }

    public static boolean checkInToday(String today, Set<String> checkedDates) {
        if (!canCheckInToday(today, checkedDates)) {
            return false;
        }
        checkedDates.add(today);
        return true;
    }

    public static boolean checkInToday(String currentDate, String requestedDate, Set<String> checkedDates) {
        if (currentDate == null || !currentDate.equals(requestedDate)) {
            return false;
        }
        return checkInToday(currentDate, checkedDates);
    }

    public static int levelForDays(int checkedDayCount) {
        int level = 1;
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            if (checkedDayCount >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return Math.min(level, LEVEL_NAMES.length);
    }

    public static String nameForLevel(int level) {
        int safeLevel = Math.max(1, Math.min(level, LEVEL_NAMES.length));
        return LEVEL_NAMES[safeLevel - 1];
    }

    public static int nextThresholdForLevel(int level) {
        return level >= LEVEL_THRESHOLDS.length ? LEVEL_THRESHOLDS[LEVEL_THRESHOLDS.length - 1] : LEVEL_THRESHOLDS[level];
    }

    public static int currentThresholdForLevel(int level) {
        int safeLevel = Math.max(1, Math.min(level, LEVEL_THRESHOLDS.length));
        return LEVEL_THRESHOLDS[safeLevel - 1];
    }

    public static int maxLevel() {
        return LEVEL_NAMES.length;
    }
}

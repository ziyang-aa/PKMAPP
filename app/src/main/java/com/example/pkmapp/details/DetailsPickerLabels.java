package com.example.pkmapp.details;

import java.util.Calendar;
import java.util.Locale;

public final class DetailsPickerLabels {
    private DetailsPickerLabels() {
    }

    public static String monthButtonText(int year, int zeroBasedMonth) {
        return String.format(Locale.CHINA, "%d年%d月", year, zeroBasedMonth + 1);
    }

    public static String pickerOptionText(int value, String suffix) {
        return ("月".equals(suffix) ? value + 1 : value) + suffix;
    }

    public static Calendar monthStart(Calendar source) {
        Calendar start = (Calendar) source.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 12);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start;
    }
}

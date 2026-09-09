package com.example.pkmapp.borrowing;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Pure calendar formatting and grid rules for the borrowing date sheet. */
public final class BorrowingCalendarModel {
    private BorrowingCalendarModel() {
    }

    public static List<Calendar> cellsForMonth(Calendar reference) {
        Calendar first = (Calendar) reference.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        first.set(Calendar.HOUR_OF_DAY, 0);
        first.set(Calendar.MINUTE, 0);
        first.set(Calendar.SECOND, 0);
        first.set(Calendar.MILLISECOND, 0);

        int mondayOffset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        first.add(Calendar.DAY_OF_MONTH, -mondayOffset);

        List<Calendar> cells = new ArrayList<>(42);
        for (int index = 0; index < 42; index++) {
            Calendar cell = (Calendar) first.clone();
            cell.add(Calendar.DAY_OF_MONTH, index);
            cells.add(cell);
        }
        return cells;
    }

    public static String monthTitle(Calendar date) {
        return String.format(Locale.CHINA, "%d年%d月", date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1);
    }

    public static String selectedDateLabel(Calendar date) {
        return String.format(Locale.CHINA, "%d年%d月%d日", date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
    }

    public static String weekdayLabel(Calendar date) {
        String[] weekdays = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        return weekdays[date.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY];
    }

    public static boolean isSameDate(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isSameMonth(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.MONTH) == second.get(Calendar.MONTH);
    }
}

package com.example.pkmapp.record;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Keeps the record date and exposes the three independent dropdown ranges. */
public final class RecordDateState {
    private static final int FIRST_YEAR = 2020;
    private static final int LAST_YEAR = 2035;
    private final Calendar date;

    public RecordDateState(Calendar initialDate) {
        date = (Calendar) initialDate.clone();
        date.setLenient(true);
        clampDay();
    }

    public static RecordDateState today() {
        return new RecordDateState(Calendar.getInstance());
    }

    public int year() {
        return date.get(Calendar.YEAR);
    }

    public int month() {
        return date.get(Calendar.MONTH) + 1;
    }

    public int day() {
        return date.get(Calendar.DAY_OF_MONTH);
    }

    public long timeInMillis() {
        return date.getTimeInMillis();
    }

    public List<Integer> years() {
        return range(FIRST_YEAR, LAST_YEAR);
    }

    public List<Integer> months() {
        return range(1, 12);
    }

    public List<Integer> days() {
        return range(1, date.getActualMaximum(Calendar.DAY_OF_MONTH));
    }

    public void selectYear(int value) {
        int selectedDay = day();
        date.set(Calendar.DAY_OF_MONTH, 1);
        date.set(Calendar.YEAR, value);
        restoreDay(selectedDay);
    }

    public void selectMonth(int value) {
        int selectedDay = day();
        date.set(Calendar.DAY_OF_MONTH, 1);
        date.set(Calendar.MONTH, value - 1);
        restoreDay(selectedDay);
    }

    public void selectDay(int value) {
        date.set(Calendar.DAY_OF_MONTH, value);
    }

    private void clampDay() {
        int maximum = date.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (day() > maximum) {
            date.set(Calendar.DAY_OF_MONTH, maximum);
        }
    }

    private void restoreDay(int requestedDay) {
        int maximum = date.getActualMaximum(Calendar.DAY_OF_MONTH);
        date.set(Calendar.DAY_OF_MONTH, Math.min(requestedDay, maximum));
    }

    private List<Integer> range(int first, int last) {
        List<Integer> values = new ArrayList<>();
        for (int value = first; value <= last; value++) {
            values.add(value);
        }
        return values;
    }
}

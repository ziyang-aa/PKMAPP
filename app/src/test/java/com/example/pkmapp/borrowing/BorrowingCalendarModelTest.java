package com.example.pkmapp.borrowing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;

public final class BorrowingCalendarModelTest {
    @Test
    public void calendarUsesMondayFirstSixWeekGrid() {
        Calendar month = Calendar.getInstance();
        month.clear();
        month.set(2026, Calendar.SEPTEMBER, 8);

        List<Calendar> cells = BorrowingCalendarModel.cellsForMonth(month);

        assertEquals(42, cells.size());
        assertEquals(Calendar.AUGUST, cells.get(0).get(Calendar.MONTH));
        assertEquals(31, cells.get(0).get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.SEPTEMBER, cells.get(1).get(Calendar.MONTH));
        assertEquals(1, cells.get(1).get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.OCTOBER, cells.get(41).get(Calendar.MONTH));
        assertEquals(11, cells.get(41).get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void calendarLabelsUsePaperBookChineseFormat() {
        Calendar date = Calendar.getInstance();
        date.clear();
        date.set(2026, Calendar.SEPTEMBER, 8);

        assertEquals("2026年9月", BorrowingCalendarModel.monthTitle(date));
        assertEquals("2026年9月8日", BorrowingCalendarModel.selectedDateLabel(date));
        assertEquals("星期二", BorrowingCalendarModel.weekdayLabel(date));
    }
}

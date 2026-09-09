package com.example.pkmapp.record;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import org.junit.Test;

public final class RecordDateStateTest {
    @Test
    public void changingMonthClampsDayAndExposesOnlyValidDays() {
        Calendar initial = Calendar.getInstance();
        initial.set(2026, Calendar.JANUARY, 31, 12, 0, 0);
        RecordDateState state = new RecordDateState(initial);

        state.selectMonth(2);

        assertEquals(28, state.day());
        assertEquals(28, state.days().size());
    }

    @Test
    public void todayUsesTheSystemCalendarDate() {
        Calendar systemToday = Calendar.getInstance();
        RecordDateState state = RecordDateState.today();

        assertEquals(systemToday.get(Calendar.YEAR), state.year());
        assertEquals(systemToday.get(Calendar.MONTH) + 1, state.month());
        assertEquals(systemToday.get(Calendar.DAY_OF_MONTH), state.day());
    }
}

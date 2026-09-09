package com.example.pkmapp.details;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

public final class DetailsPickerLabelTest {
    @Test
    public void monthButtonUsesReadablePaperStyleLabel() {
        assertEquals("2026年9月", DetailsPickerLabels.monthButtonText(2026, Calendar.SEPTEMBER));
    }

    @Test
    public void pickerOptionUsesLargeDropdownTextWithoutChangingMonthIndex() {
        assertEquals("2026年", DetailsPickerLabels.pickerOptionText(2026, "年"));
        assertEquals("1月", DetailsPickerLabels.pickerOptionText(0, "月"));
        assertEquals("12月", DetailsPickerLabels.pickerOptionText(11, "月"));
    }

    @Test
    public void currentMonthStartKeepsTodayYearAndMonth() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.SEPTEMBER, 8, 18, 24, 33);

        Calendar start = DetailsPickerLabels.monthStart(now);

        assertEquals(2026, start.get(Calendar.YEAR));
        assertEquals(Calendar.SEPTEMBER, start.get(Calendar.MONTH));
        assertEquals(1, start.get(Calendar.DAY_OF_MONTH));
        assertEquals(12, start.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, start.get(Calendar.MINUTE));
        assertEquals(0, start.get(Calendar.SECOND));
    }
}

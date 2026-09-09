package com.example.pkmapp.borrowing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public final class BorrowingDateGroupTest {
    @Test
    public void groupsByLocalDateAndSortsNewestDateFirst() {
        BorrowingRecord older = record("older", BorrowingDirection.LEND,
                1_000L, localMillis(2024, Calendar.JULY, 31, 9, 0), "小林");
        BorrowingRecord newest = record("newest", BorrowingDirection.BORROW,
                2_000L, localMillis(2024, Calendar.AUGUST, 1, 9, 0), "阿木");
        BorrowingRecord sameDay = record("same-day", BorrowingDirection.LEND,
                3_000L, localMillis(2024, Calendar.AUGUST, 1, 18, 0), "小夏");

        List<BorrowingDateGroup> groups = BorrowingDateGroup.from(
                Arrays.asList(older, newest, sameDay));

        assertEquals(2, groups.size());
        assertEquals("8月1日", groups.get(0).getLabel());
        assertEquals(2, groups.get(0).getRecords().size());
        assertEquals("7月31日", groups.get(1).getLabel());
        assertEquals(1, groups.get(1).getRecords().size());
    }

    @Test
    public void emptyInputProducesNoGroups() {
        assertEquals(0, BorrowingDateGroup.from(Arrays.<BorrowingRecord>asList()).size());
    }

    private static BorrowingRecord record(String id, BorrowingDirection direction,
            long amountInCents, long occurredAtMillis, String person) {
        return new BorrowingRecord(id, "life", direction, amountInCents,
                occurredAtMillis, person, "", null);
    }

    private static long localMillis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
}

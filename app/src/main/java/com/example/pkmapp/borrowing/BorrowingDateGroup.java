package com.example.pkmapp.borrowing;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Presentation grouping for the borrowing page, using the user's device timezone. */
public final class BorrowingDateGroup {
    private final String label;
    private final List<BorrowingRecord> records;

    private BorrowingDateGroup(String label, List<BorrowingRecord> records) {
        this.label = label;
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
    }

    public String getLabel() {
        return label;
    }

    public List<BorrowingRecord> getRecords() {
        return records;
    }

    public static List<BorrowingDateGroup> from(List<BorrowingRecord> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        List<BorrowingRecord> sorted = new ArrayList<>();
        for (BorrowingRecord record : input) {
            if (record != null) {
                sorted.add(record);
            }
        }
        sorted.sort(Comparator.comparingLong(BorrowingRecord::getOccurredAtMillis).reversed());

        Map<String, List<BorrowingRecord>> grouped = new TreeMap<>(Comparator.reverseOrder());
        Map<String, String> labels = new HashMap<>();
        for (BorrowingRecord record : sorted) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(record.getOccurredAtMillis());
            String key = String.format(Locale.US, "%04d-%02d-%02d",
                    calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
            labels.put(key, (calendar.get(Calendar.MONTH) + 1) + "月"
                    + calendar.get(Calendar.DAY_OF_MONTH) + "日");
        }

        List<BorrowingDateGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<BorrowingRecord>> entry : grouped.entrySet()) {
            result.add(new BorrowingDateGroup(labels.get(entry.getKey()), entry.getValue()));
        }
        return Collections.unmodifiableList(result);
    }
}

package com.example.pkmapp.borrowing;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Small local store for borrowing details, separate from the transaction repository. */
public final class BorrowingRecordStore {
    private static final String PREFERENCES = "borrowing_records";
    private static final String RECORDS_KEY = "records";

    private final SharedPreferences preferences;

    public BorrowingRecordStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
    }

    public synchronized List<BorrowingRecord> getAllRecords() {
        String encoded = preferences.getString(RECORDS_KEY, "[]");
        return BorrowingRecordCodec.decode(encoded);
    }

    public synchronized List<BorrowingRecord> getRecordsForLedger(String ledgerId) {
        if (ledgerId == null || ledgerId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<BorrowingRecord> filtered = new ArrayList<>();
        for (BorrowingRecord record : getAllRecords()) {
            if (ledgerId.equals(record.getLedgerId())) {
                filtered.add(record);
            }
        }
        filtered.sort(Comparator.comparingLong(BorrowingRecord::getOccurredAtMillis).reversed());
        return Collections.unmodifiableList(filtered);
    }

    public synchronized void add(BorrowingRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("借钱记录不能为空");
        }
        List<BorrowingRecord> records = new ArrayList<>(getAllRecords());
        records.removeIf(existing -> existing.getId().equals(record.getId()));
        records.add(record);
        preferences.edit().putString(RECORDS_KEY, BorrowingRecordCodec.encode(records)).apply();
    }

    public synchronized void clear() {
        preferences.edit().clear().apply();
    }
}

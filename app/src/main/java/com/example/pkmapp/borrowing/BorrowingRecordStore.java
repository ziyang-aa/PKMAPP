package com.example.pkmapp.borrowing;

import android.content.Context;

import com.example.pkmapp.core.database.AppDatabase;
import com.example.pkmapp.core.database.BorrowingDao;
import com.example.pkmapp.core.database.BorrowingRecordEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Room-backed local store for borrowing details, separate from the transaction repository. */
public final class BorrowingRecordStore {
    private final BorrowingDao borrowingDao;

    public BorrowingRecordStore(Context context) {
        borrowingDao = AppDatabase.getInstance(context).borrowingDao();
    }

    public synchronized List<BorrowingRecord> getAllRecords() {
        List<BorrowingRecord> result = new ArrayList<>();
        for (BorrowingRecordEntity row : borrowingDao.getAll()) {
            BorrowingRecord converted = fromEntity(row);
            if (converted != null) {
                result.add(converted);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized List<BorrowingRecord> getRecordsForLedger(String ledgerId) {
        if (ledgerId == null || ledgerId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<BorrowingRecord> filtered = new ArrayList<>();
        for (BorrowingRecordEntity row : borrowingDao.getForLedger(ledgerId.trim())) {
            BorrowingRecord converted = fromEntity(row);
            if (converted != null) {
                filtered.add(converted);
            }
        }
        filtered.sort(Comparator.comparingLong(BorrowingRecord::getOccurredAtMillis).reversed());
        return Collections.unmodifiableList(filtered);
    }

    public synchronized void add(BorrowingRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("借钱记录不能为空");
        }
        borrowingDao.upsert(toEntity(record));
    }

    public synchronized void replaceAll(List<BorrowingRecord> records) {
        if (records == null) {
            throw new IllegalArgumentException("借钱记录不能为空");
        }
        List<BorrowingRecordEntity> entities = new ArrayList<>(records.size());
        for (BorrowingRecord record : records) {
            if (record != null) {
                entities.add(toEntity(record));
            }
        }
        borrowingDao.replaceAll(entities);
    }

    public synchronized void deleteForLedger(String ledgerId) {
        if (ledgerId == null || ledgerId.trim().isEmpty()) {
            return;
        }
        borrowingDao.deleteForLedger(ledgerId.trim());
    }

    public synchronized void clear() {
        borrowingDao.clear();
    }

    private static BorrowingRecordEntity toEntity(BorrowingRecord record) {
        return new BorrowingRecordEntity(
                record.getId(),
                record.getLedgerId(),
                record.getDirection().name(),
                record.getPerson(),
                record.getAmountInCents(),
                record.getOccurredAtMillis(),
                record.getNote(),
                record.getSourceTransactionId(),
                false
        );
    }

    private static BorrowingRecord fromEntity(BorrowingRecordEntity row) {
        try {
            return new BorrowingRecord(
                    row.getId(),
                    row.getLedgerId(),
                    BorrowingDirection.valueOf(row.getDirection()),
                    row.getAmountCents(),
                    row.getOccurredAt(),
                    row.getCounterparty(),
                    row.getNote(),
                    row.getSourceTransactionId()
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

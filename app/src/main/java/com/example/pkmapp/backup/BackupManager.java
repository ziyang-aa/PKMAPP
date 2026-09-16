package com.example.pkmapp.backup;

import android.content.Context;

import com.example.pkmapp.borrowing.BorrowingRecordStore;
import com.example.pkmapp.data.AssetStore;
import com.example.pkmapp.data.InMemoryLedgerRepository;
import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.savings.SavingsGoalStore;

import java.util.ArrayList;
import java.util.List;

/** Coordinates the financial stores for a complete export or import. */
public final class BackupManager {
    private final InMemoryLedgerRepository repository;
    private final SavingsGoalStore savingsGoalStore;
    private final AssetStore assetStore;
    private final BorrowingRecordStore borrowingRecordStore;

    public BackupManager(Context context) {
        Context appContext = context.getApplicationContext();
        repository = InMemoryLedgerRepository.getInstance();
        savingsGoalStore = new SavingsGoalStore(appContext);
        assetStore = new AssetStore(appContext);
        borrowingRecordStore = new BorrowingRecordStore(appContext);
    }

    public BackupSnapshot capture() {
        List<BackupSnapshot.AssetValues> assets = new ArrayList<>();
        for (Ledger ledger : repository.getLedgers()) {
            AssetStore.StartingValues values = assetStore.getStartingValues(ledger.getId());
            assets.add(new BackupSnapshot.AssetValues(ledger.getId(),
                    values.getInitialAssetsInCents(), values.getLiabilitiesInCents()));
        }
        return new BackupSnapshot(System.currentTimeMillis(), repository.getLedgers(),
                repository.getAllTransactions(), repository.getCurrentLedger().getId(),
                savingsGoalStore.getGoals(), assets, borrowingRecordStore.getAllRecords());
    }

    public BackupImportResult importSnapshot(BackupSnapshot incoming, ImportMode mode) {
        if (incoming == null || mode == null) {
            throw new IllegalArgumentException("导入数据不能为空");
        }
        BackupSnapshot existing = capture();
        BackupSnapshot target;
        BackupImportResult result;
        if (mode == ImportMode.MERGE) {
            BackupMerger.Result merged = BackupMerger.merge(existing, incoming);
            target = merged.getSnapshot();
            result = new BackupImportResult(merged.getAddedLedgers(), merged.getAddedTransactions(),
                    merged.getSkippedTransactions(), merged.getAddedSavingsGoals(),
                    merged.getSkippedSavingsGoals(), merged.getAddedBorrowingRecords(),
                    merged.getSkippedBorrowingRecords());
        } else {
            target = incoming;
            result = new BackupImportResult(incoming.getLedgers().size(),
                    incoming.getTransactions().size(), 0, incoming.getSavingsGoals().size(), 0,
                    incoming.getBorrowingRecords().size(), 0);
        }

        try {
            apply(target);
            return result;
        } catch (RuntimeException exception) {
            try {
                apply(existing);
            } catch (RuntimeException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
    }

    private void apply(BackupSnapshot snapshot) {
        savingsGoalStore.replaceGoals(snapshot.getSavingsGoals());
        borrowingRecordStore.replaceAll(snapshot.getBorrowingRecords());
        assetStore.clear();
        for (BackupSnapshot.AssetValues values : snapshot.getAssetValues()) {
            assetStore.saveStartingValues(values.getLedgerId(), values.getInitialAssetsInCents(),
                    values.getLiabilitiesInCents());
        }
        repository.replaceState(snapshot.getLedgers(), snapshot.getTransactions(),
                snapshot.getCurrentLedgerId());
    }

    public enum ImportMode {
        OVERWRITE,
        MERGE
    }
}

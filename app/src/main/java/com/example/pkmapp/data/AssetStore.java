package com.example.pkmapp.data;

import android.content.Context;

import com.example.pkmapp.core.database.AppDatabase;
import com.example.pkmapp.core.database.LedgerDao;
import com.example.pkmapp.core.database.LedgerEntity;

/** Persists the starting asset and liability values for each ledger in Room. */
public final class AssetStore {
    private final LedgerDao ledgerDao;

    public AssetStore(Context context) {
        ledgerDao = AppDatabase.getInstance(context).ledgerDao();
    }

    public AssetSnapshot getSnapshot(String ledgerId, long ledgerNetFlowInCents) {
        String safeLedgerId = requireLedgerId(ledgerId);
        LedgerEntity row = ledgerDao.getById(safeLedgerId);
        long initialAssets = row == null ? 0L : row.getInitialAssetsCents();
        long liabilities = row == null ? 0L : row.getLiabilitiesCents();
        return new AssetSnapshot(initialAssets, liabilities, ledgerNetFlowInCents);
    }

    public StartingValues getStartingValues(String ledgerId) {
        String safeLedgerId = requireLedgerId(ledgerId);
        LedgerEntity row = ledgerDao.getById(safeLedgerId);
        long initialAssets = row == null ? 0L : row.getInitialAssetsCents();
        long liabilities = row == null ? 0L : row.getLiabilitiesCents();
        return new StartingValues(initialAssets, liabilities);
    }

    public void saveStartingValues(String ledgerId, long initialAssetsInCents,
            long liabilitiesInCents) {
        String safeLedgerId = requireLedgerId(ledgerId);
        if (initialAssetsInCents < 0L || liabilitiesInCents < 0L) {
            throw new IllegalArgumentException("资产和负债不能为负数");
        }
        ledgerDao.saveStartingValues(safeLedgerId, initialAssetsInCents, liabilitiesInCents);
    }

    public void addLiability(String ledgerId, long amountInCents) {
        String safeLedgerId = requireLedgerId(ledgerId);
        if (amountInCents <= 0L) {
            throw new IllegalArgumentException("负债金额必须大于零");
        }
        ledgerDao.addLiability(safeLedgerId, amountInCents);
    }

    public void clear() {
        ledgerDao.clearAllAssetValues();
    }

    public void deleteStartingValues(String ledgerId) {
        String safeLedgerId = requireLedgerId(ledgerId);
        ledgerDao.resetAssetValuesForLedger(safeLedgerId);
    }

    private static String requireLedgerId(String ledgerId) {
        if (ledgerId == null || ledgerId.trim().isEmpty()) {
            throw new IllegalArgumentException("账本编号不能为空");
        }
        return ledgerId.trim();
    }

    public static final class StartingValues {
        private final long initialAssetsInCents;
        private final long liabilitiesInCents;

        private StartingValues(long initialAssetsInCents, long liabilitiesInCents) {
            this.initialAssetsInCents = initialAssetsInCents;
            this.liabilitiesInCents = liabilitiesInCents;
        }

        public long getInitialAssetsInCents() {
            return initialAssetsInCents;
        }

        public long getLiabilitiesInCents() {
            return liabilitiesInCents;
        }
    }
}

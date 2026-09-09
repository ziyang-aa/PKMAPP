package com.example.pkmapp.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the starting asset and liability values for each ledger. */
public final class AssetStore {
    private static final String PREFERENCES = "forest_assets";
    private static final String INITIAL_ASSETS_PREFIX = "initial_assets_";
    private static final String LIABILITIES_PREFIX = "liabilities_";

    private final SharedPreferences preferences;

    public AssetStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
    }

    public AssetSnapshot getSnapshot(String ledgerId, long ledgerNetFlowInCents) {
        String safeLedgerId = requireLedgerId(ledgerId);
        return new AssetSnapshot(
                preferences.getLong(INITIAL_ASSETS_PREFIX + safeLedgerId, 0L),
                preferences.getLong(LIABILITIES_PREFIX + safeLedgerId, 0L),
                ledgerNetFlowInCents);
    }

    public void saveStartingValues(String ledgerId, long initialAssetsInCents,
            long liabilitiesInCents) {
        String safeLedgerId = requireLedgerId(ledgerId);
        if (initialAssetsInCents < 0L || liabilitiesInCents < 0L) {
            throw new IllegalArgumentException("资产和负债不能为负数");
        }
        preferences.edit()
                .putLong(INITIAL_ASSETS_PREFIX + safeLedgerId, initialAssetsInCents)
                .putLong(LIABILITIES_PREFIX + safeLedgerId, liabilitiesInCents)
                .apply();
    }

    public void addLiability(String ledgerId, long amountInCents) {
        String safeLedgerId = requireLedgerId(ledgerId);
        if (amountInCents <= 0L) {
            throw new IllegalArgumentException("负债金额必须大于零");
        }
        long current = preferences.getLong(LIABILITIES_PREFIX + safeLedgerId, 0L);
        preferences.edit()
                .putLong(LIABILITIES_PREFIX + safeLedgerId, current + amountInCents)
                .apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    private static String requireLedgerId(String ledgerId) {
        if (ledgerId == null || ledgerId.trim().isEmpty()) {
            throw new IllegalArgumentException("账本编号不能为空");
        }
        return ledgerId.trim();
    }
}

package com.example.pkmapp.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AssetSnapshotTest {
    @Test
    public void currentAssetsAndNetWorthUseInitialValuesAndLedgerFlow() {
        AssetSnapshot snapshot = new AssetSnapshot(100_000L, 30_000L, 16_500L);

        assertEquals(116_500L, snapshot.getCurrentAssetsInCents());
        assertEquals(30_000L, snapshot.getLiabilitiesInCents());
        assertEquals(86_500L, snapshot.getNetWorthInCents());
    }

    @Test
    public void snapshotAllowsNegativeNetWorthWhenLiabilitiesExceedAssets() {
        AssetSnapshot snapshot = new AssetSnapshot(10_000L, 50_000L, 0L);

        assertEquals(10_000L, snapshot.getCurrentAssetsInCents());
        assertEquals(-40_000L, snapshot.getNetWorthInCents());
    }
}

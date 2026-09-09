package com.example.pkmapp.data;

/** Current asset summary derived from the user's starting values and ledger flow. */
public final class AssetSnapshot {
    private final long initialAssetsInCents;
    private final long liabilitiesInCents;
    private final long ledgerNetFlowInCents;

    public AssetSnapshot(long initialAssetsInCents, long liabilitiesInCents,
            long ledgerNetFlowInCents) {
        requireNonNegative(initialAssetsInCents, "初始资产不能为负数");
        requireNonNegative(liabilitiesInCents, "初始负债不能为负数");
        this.initialAssetsInCents = initialAssetsInCents;
        this.liabilitiesInCents = liabilitiesInCents;
        this.ledgerNetFlowInCents = ledgerNetFlowInCents;
    }

    public long getInitialAssetsInCents() {
        return initialAssetsInCents;
    }

    public long getCurrentAssetsInCents() {
        return initialAssetsInCents + ledgerNetFlowInCents;
    }

    public long getLiabilitiesInCents() {
        return liabilitiesInCents;
    }

    public long getNetWorthInCents() {
        return getCurrentAssetsInCents() - liabilitiesInCents;
    }

    private static void requireNonNegative(long value, String message) {
        if (value < 0L) {
            throw new IllegalArgumentException(message);
        }
    }
}

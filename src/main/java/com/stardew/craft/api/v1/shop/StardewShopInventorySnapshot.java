package com.stardew.craft.api.v1.shop;

import com.stardew.craft.api.v1.requirement.StardewRequirementReport;

import java.util.Objects;

/**
 * Immutable server-side view of one listed or candidate shop row. The
 * requirement report states whether the row is currently listed, accepted by
 * a virtual product handler and sufficiently stocked.
 */
public record StardewShopInventorySnapshot(
        StardewShopRowKey key,
        StardewShopEntry entry,
        int remainingStock,
        boolean unlimitedStock,
        StardewRequirementReport requirements
) {
    public StardewShopInventorySnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(requirements, "requirements");
        if (remainingStock < 0) {
            throw new IllegalArgumentException(
                    "remainingStock must not be negative");
        }
        if (unlimitedStock
                != (remainingStock == Integer.MAX_VALUE)) {
            throw new IllegalArgumentException(
                    "unlimitedStock must match remainingStock");
        }
    }

    public boolean available() {
        return requirements.satisfied();
    }
}

package com.stardew.craft.api.v1.communitycenter;

import javax.annotation.Nullable;
import java.util.Objects;

/** Stable wire-compatible ingredient description for a Community Center bundle. */
public record StardewBundleIngredient(
        @Nullable String itemId,
        String legacyItemId,
        int category,
        int count,
        int minimumQuality
) {
    public StardewBundleIngredient {
        legacyItemId = Objects.requireNonNull(legacyItemId, "legacyItemId");
        if (count <= 0) {
            throw new IllegalArgumentException("Bundle ingredient count must be positive");
        }
        if (category != -1 && (minimumQuality < 0 || minimumQuality > 4)) {
            throw new IllegalArgumentException("Bundle ingredient quality must be in 0..4");
        }
        if (category >= 0 && (itemId == null || itemId.isBlank())) {
            throw new IllegalArgumentException("Exact bundle ingredients require an item ID");
        }
    }

    public boolean money() {
        return category == -1;
    }
}

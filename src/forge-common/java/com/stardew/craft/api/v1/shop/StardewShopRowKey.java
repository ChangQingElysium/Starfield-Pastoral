package com.stardew.craft.api.v1.shop;

import java.util.Objects;

/**
 * Stable compound identity for one standard-shop row.
 *
 * <p>Both legacy and namespaced shop IDs are supported. The entry ID is the
 * same stable item or virtual-product identity used by the authoritative
 * purchase protocol.
 */
public record StardewShopRowKey(
        String shopId,
        String entryId
) {
    public StardewShopRowKey {
        Objects.requireNonNull(shopId, "shopId");
        Objects.requireNonNull(entryId, "entryId");
        if (shopId.isBlank() || entryId.isBlank()) {
            throw new IllegalArgumentException(
                    "shopId and entryId must not be blank");
        }
    }
}

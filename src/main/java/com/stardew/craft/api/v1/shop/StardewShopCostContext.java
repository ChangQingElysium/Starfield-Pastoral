package com.stardew.craft.api.v1.shop;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server context for resolving the complete cost of one shop purchase request. */
public record StardewShopCostContext(
        ServerPlayer player,
        String shopId,
        StardewShopEntry entry,
        int quantity
) {
    public StardewShopCostContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(shopId, "shopId");
        Objects.requireNonNull(entry, "entry");
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
    }
}

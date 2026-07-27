package com.stardew.craft.api.v1.shop;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server context for identifying and delivering a non-item shop product. */
public record StardewShopProductContext(
        ServerPlayer player,
        String shopId,
        StardewShopEntry entry,
        int quantity
) {
    public StardewShopProductContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(shopId, "shopId");
        Objects.requireNonNull(entry, "entry");
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
    }
}

package com.stardew.craft.api.v1.shop;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server context supplied to an add-on's dynamic shop inventory provider. */
public record StardewShopInventoryContext(ServerPlayer player, ResourceLocation shop) {
    public StardewShopInventoryContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(shop, "shop");
    }
}

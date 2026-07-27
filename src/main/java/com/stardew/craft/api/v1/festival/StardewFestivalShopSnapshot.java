package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Canonical and legacy-compatible identity of a festival shop. */
public record StardewFestivalShopSnapshot(
        ResourceLocation shopId,
        String runtimeShopId
) {
    public StardewFestivalShopSnapshot {
        shopId = Objects.requireNonNull(shopId, "shopId");
        runtimeShopId = Objects.requireNonNull(
                runtimeShopId, "runtimeShopId");
    }
}

package com.stardew.craft.api.v1.world;

import net.minecraft.resources.ResourceLocation;

/** Core scope IDs used by the unified map-slot view. */
public final class StardewMapSlotScopes {
    public static final ResourceLocation WORLD =
            core("world");
    public static final ResourceLocation FARM =
            core("farm");

    private StardewMapSlotScopes() {
    }

    private static ResourceLocation core(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", path);
    }
}

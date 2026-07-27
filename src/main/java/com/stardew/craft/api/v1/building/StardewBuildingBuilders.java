package com.stardew.craft.api.v1.building;

import net.minecraft.resources.ResourceLocation;

/** Built-in building catalog IDs. */
public final class StardewBuildingBuilders {
    public static final ResourceLocation ROBIN =
            ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft", "robin");
    public static final ResourceLocation WIZARD =
            ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft", "wizard");

    private StardewBuildingBuilders() {
    }
}

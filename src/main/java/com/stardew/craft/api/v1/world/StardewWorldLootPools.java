package com.stardew.craft.api.v1.world;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

/** Built-in world-loot source IDs. Add-ons may use their own source IDs. */
public final class StardewWorldLootPools {
    public static final ResourceLocation ARTIFACT_SPOT = id("artifact_spot");
    public static final ResourceLocation QUARRY = id("quarry");
    public static final ResourceLocation SKULL_CAVERN_TREASURE = id("skull_cavern_treasure");

    private StardewWorldLootPools() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }
}

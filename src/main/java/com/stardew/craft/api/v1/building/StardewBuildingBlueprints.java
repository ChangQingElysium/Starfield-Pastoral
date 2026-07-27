package com.stardew.craft.api.v1.building;

import com.stardew.craft.building.BuildingBlueprintRegistry;
import com.stardew.craft.building.BuildingCatalogService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** Registration, lookup, and safe catalog-opening facade for building addons. */
public final class StardewBuildingBlueprints {
    private StardewBuildingBlueprints() {
    }

    public static void register(
            ResourceLocation id,
            StardewBuildingBlueprintDefinition definition
    ) {
        BuildingBlueprintRegistry.register(id, definition);
    }

    public static Optional<StardewBuildingBlueprint> find(
            ResourceLocation id
    ) {
        return BuildingBlueprintRegistry.find(id);
    }

    public static List<StardewBuildingBlueprint> all() {
        return BuildingBlueprintRegistry.all();
    }

    public static List<StardewBuildingBlueprint> forBuilder(
            ServerPlayer player,
            ResourceLocation builder
    ) {
        return BuildingBlueprintRegistry.availableFor(player, builder);
    }

    /**
     * Opens a short-lived, server-authorized catalog session.
     *
     * <p>Call this only after the addon has checked its own NPC, block, quest,
     * or location access rule. A later purchase must match this exact catalog
     * revision and one of the blueprint IDs sent by the server.
     */
    public static boolean open(
            ServerPlayer player,
            ResourceLocation builder
    ) {
        return BuildingCatalogService.open(player, builder);
    }
}

package com.stardew.craft.api.v1.world;

import com.stardew.craft.interior.InteriorRegionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Read-only logical-location facade shared by maps, fishing, NPCs, festivals and world rules.
 */
public final class StardewLocations {
    private StardewLocations() {
    }

    public static Optional<StardewLocation> find(
            Level level,
            BlockPos position
    ) {
        if (level == null || position == null) {
            return Optional.empty();
        }
        return find(level.dimension().location(), position);
    }

    public static Optional<StardewLocation> find(
            ResourceLocation dimension,
            BlockPos position
    ) {
        return InteriorRegionRegistry.locationAt(dimension, position);
    }

    public static Optional<StardewLocation> get(ResourceLocation id) {
        return InteriorRegionRegistry.location(id);
    }

    public static Optional<ResourceLocation> resolveId(String idOrAlias) {
        return InteriorRegionRegistry.canonicalLocationId(idOrAlias);
    }

    public static List<StardewLocation> all() {
        return InteriorRegionRegistry.locations();
    }

    /** Returns the location followed by each declared parent. */
    public static List<StardewLocation> hierarchy(
            ResourceLocation locationId
    ) {
        java.util.ArrayList<StardewLocation> hierarchy =
                new java.util.ArrayList<>();
        ResourceLocation cursor = locationId;
        while (cursor != null) {
            StardewLocation location = get(cursor).orElse(null);
            if (location == null) {
                break;
            }
            hierarchy.add(location);
            cursor = location.parentId();
        }
        return List.copyOf(hierarchy);
    }

    /** Returns the most-specific location and its parents at a position. */
    public static List<StardewLocation> hierarchy(
            ResourceLocation dimension,
            BlockPos position
    ) {
        return find(dimension, position)
                .map(location -> hierarchy(location.id()))
                .orElse(List.of());
    }

    /** True when {@code locationId} is the same as or descends from {@code ancestorId}. */
    public static boolean isWithin(
            ResourceLocation locationId,
            ResourceLocation ancestorId
    ) {
        if (locationId == null || ancestorId == null) {
            return false;
        }
        return hierarchy(locationId).stream()
                .anyMatch(location ->
                        location.id().equals(ancestorId));
    }
}

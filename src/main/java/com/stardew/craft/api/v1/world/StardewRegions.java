package com.stardew.craft.api.v1.world;

import com.stardew.craft.world.WorldRegionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Read-only facade for reloadable shared world regions. */
public final class StardewRegions {
    private StardewRegions() {
    }

    public static Optional<StardewRegion> get(ResourceLocation id) {
        return WorldRegionRegistry.get(id);
    }

    public static Optional<StardewRegion> find(
            Level level,
            BlockPos position
    ) {
        if (level == null) {
            return Optional.empty();
        }
        return find(level.dimension().location(), position);
    }

    public static Optional<StardewRegion> find(
            ResourceLocation dimension,
            BlockPos position
    ) {
        return WorldRegionRegistry.find(dimension, position);
    }

    public static List<StardewRegion> findAll(
            ResourceLocation dimension,
            BlockPos position
    ) {
        return WorldRegionRegistry.findAll(dimension, position);
    }

    public static List<StardewRegion> all() {
        return WorldRegionRegistry.all();
    }

    public static List<StardewRegion> withTag(ResourceLocation tag) {
        return WorldRegionRegistry.withTag(tag);
    }

    public static List<StardewRegion> forLocation(
            ResourceLocation locationId
    ) {
        return WorldRegionRegistry.forLocation(locationId);
    }
}

package com.stardew.craft.api.v1.fishpond;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable server-side view of one fish pond's persisted gameplay state. */
public record StardewFishPondSnapshot(
        String id,
        Optional<UUID> owner,
        ResourceKey<Level> dimension,
        BlockPos managerPosition,
        BlockPos bucketPosition,
        Optional<ResourceLocation> fishType,
        int population,
        int maxPopulation,
        Optional<ResourceLocation> outputItem,
        int outputCount,
        Optional<String> requestedItem,
        int requestedCount,
        boolean requestCompleted,
        int lastUnlockedPopulationGate,
        int daysSinceSpawn,
        int waterColor,
        boolean goldenAnimalCracker,
        boolean empty
) {
    public StardewFishPondSnapshot {
        id = Objects.requireNonNull(id, "id");
        owner = Objects.requireNonNull(owner, "owner");
        dimension = Objects.requireNonNull(dimension, "dimension");
        managerPosition = Objects.requireNonNull(
                managerPosition, "managerPosition").immutable();
        bucketPosition = Objects.requireNonNull(
                bucketPosition, "bucketPosition").immutable();
        fishType = Objects.requireNonNull(fishType, "fishType");
        outputItem = Objects.requireNonNull(outputItem, "outputItem");
        requestedItem = Objects.requireNonNull(
                requestedItem, "requestedItem");
        if (population < 0 || maxPopulation < 0
                || outputCount < 0 || requestedCount < 0
                || lastUnlockedPopulationGate < 0
                || daysSinceSpawn < 0) {
            throw new IllegalArgumentException(
                    "fish pond counts must be non-negative");
        }
    }
}

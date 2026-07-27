package com.stardew.craft.animal.model;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Immutable, once-per-building/day facts supplied to animal daily rules.
 * Block entities remain runtime adapters and are intentionally not exposed.
 */
public record AnimalBuildingDailyContext(
        String buildingId,
        long structureRevision,
        int absoluteDay,
        AnimalBuildingCapabilities capabilities,
        List<Long> animalIds,
        boolean winter,
        boolean raining,
        boolean autoPetter,
        boolean heater,
        List<BlockPos> autoGrabbers,
        List<BlockPos> feedTroughs,
        List<BlockPos> autoFeedTroughs,
        int pendingProduceCount
) {
    public AnimalBuildingDailyContext {
        animalIds = List.copyOf(animalIds);
        autoGrabbers = immutablePositions(autoGrabbers);
        feedTroughs = immutablePositions(feedTroughs);
        autoFeedTroughs =
                immutablePositions(autoFeedTroughs);
    }

    private static List<BlockPos> immutablePositions(
            List<BlockPos> positions
    ) {
        return positions.stream()
                .map(BlockPos::immutable)
                .toList();
    }
}

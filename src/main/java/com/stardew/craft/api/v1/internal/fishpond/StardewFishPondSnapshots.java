package com.stardew.craft.api.v1.internal.fishpond;

import com.stardew.craft.api.v1.fishpond.StardewFishPondSnapshot;
import com.stardew.craft.fishpond.model.FishPondRecord;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/** Internal projection from mutable pond storage to the public immutable view. */
public final class StardewFishPondSnapshots {
    private StardewFishPondSnapshots() {
    }

    public static StardewFishPondSnapshot from(
            ServerLevel level,
            FishPondRecord pond
    ) {
        return new StardewFishPondSnapshot(
                pond.pondId(),
                parseUuid(pond.ownerPlayerUuid()),
                level.dimension(),
                pond.managerPos(),
                pond.bucketPos(),
                parseId(pond.fishTypeId()),
                Math.max(0, pond.currentPopulation()),
                Math.max(0, pond.maxPopulation()),
                parseId(pond.outputItemId()),
                Math.max(0, pond.outputCount()),
                optionalText(pond.neededItemId()),
                Math.max(0, pond.neededItemCount()),
                pond.hasCompletedRequest(),
                Math.max(0, pond.lastUnlockedPopulationGate()),
                Math.max(0, pond.daysSinceSpawn()),
                pond.waterColor(),
                pond.goldenAnimalCracker(),
                pond.empty());
    }

    private static Optional<ResourceLocation> parseId(String raw) {
        return optionalText(raw).map(ResourceLocation::tryParse);
    }

    private static Optional<String> optionalText(String raw) {
        return raw == null || raw.isBlank()
                ? Optional.empty()
                : Optional.of(raw);
    }

    private static Optional<UUID> parseUuid(String raw) {
        try {
            return optionalText(raw).map(UUID::fromString);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}

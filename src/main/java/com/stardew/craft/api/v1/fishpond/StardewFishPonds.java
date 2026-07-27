package com.stardew.craft.api.v1.fishpond;

import com.stardew.craft.api.v1.internal.fishpond.StardewFishPondSnapshots;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only server-side queries for runtime fish ponds. */
public final class StardewFishPonds {
    private StardewFishPonds() {
    }

    public static List<StardewFishPondSnapshot> all(
            ServerLevel level
    ) {
        Objects.requireNonNull(level, "level");
        String dimension = level.dimension().location().toString();
        return FishPondWorldData.get(level).getPonds().stream()
                .filter(pond -> dimension.equals(pond.dimensionId()))
                .sorted(Comparator.comparing(pond -> pond.pondId()))
                .map(pond -> StardewFishPondSnapshots.from(level, pond))
                .toList();
    }

    public static Optional<StardewFishPondSnapshot> find(
            ServerLevel level,
            String pondId
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pondId, "pondId");
        String dimension = level.dimension().location().toString();
        return FishPondWorldData.get(level).getPond(pondId)
                .filter(pond -> dimension.equals(pond.dimensionId()))
                .map(pond -> StardewFishPondSnapshots.from(level, pond));
    }
}

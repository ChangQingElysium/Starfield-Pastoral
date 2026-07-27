package com.stardew.craft.animal.service;

import com.stardew.craft.animal.model.AnimalBuildingRecord;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Revision-aware candidate positions shared by relocation and world projections.
 *
 * <p>Collision remains entity-specific and is checked by the caller. This index only prevents
 * every animal or pending produce projection from independently rebuilding the same
 * building-volume candidate list.
 */
public final class AnimalBuildingPositionIndex {
    private static final Map<AnimalBuildingRecord, CandidateLists> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AnimalBuildingPositionIndex() {
    }

    public static List<BlockPos> interiorCandidates(
            AnimalBuildingRecord building
    ) {
        return candidates(building).interior();
    }

    public static List<BlockPos> exteriorCandidates(
            AnimalBuildingRecord building
    ) {
        return candidates(building).exterior();
    }

    private static CandidateLists candidates(
            AnimalBuildingRecord building
    ) {
        if (building == null) {
            return CandidateLists.EMPTY;
        }
        synchronized (CACHE) {
            CandidateLists cached = CACHE.get(building);
            if (cached != null
                    && cached.structureRevision()
                    == building.structureRevision()) {
                return cached;
            }
            CandidateLists rebuilt = build(building);
            CACHE.put(building, rebuilt);
            return rebuilt;
        }
    }

    private static CandidateLists build(
            AnimalBuildingRecord building
    ) {
        ArrayList<BlockPos> interior = new ArrayList<>();
        if (!building.interiorAirCells().isEmpty()) {
            for (Long packed : building.interiorAirCells()) {
                interior.add(BlockPos.of(packed).immutable());
            }
        } else {
            for (int y = building.minY();
                 y <= building.maxY();
                 y++) {
                for (int z = building.minZ();
                     z <= building.maxZ();
                     z++) {
                    for (int x = building.minX();
                         x <= building.maxX();
                         x++) {
                        BlockPos candidate =
                                new BlockPos(x, y, z);
                        if (building.isInBounds(candidate)) {
                            interior.add(candidate);
                        }
                    }
                }
            }
        }

        ArrayList<BlockPos> exterior = new ArrayList<>();
        for (int y = building.minY() - 1;
             y <= building.maxY() + 1;
             y++) {
            for (int z = building.minZ() - 1;
                 z <= building.maxZ() + 1;
                 z++) {
                for (int x = building.minX() - 1;
                     x <= building.maxX() + 1;
                     x++) {
                    BlockPos candidate =
                            new BlockPos(x, y, z);
                    if (!building.isInBounds(candidate)) {
                        exterior.add(candidate);
                    }
                }
            }
        }

        Comparator<BlockPos> nearestManagerFirst =
                Comparator.comparingDouble(
                                (BlockPos pos) -> pos.distSqr(
                                        building.managerPos()))
                        .thenComparingLong(BlockPos::asLong);
        interior.sort(nearestManagerFirst);
        exterior.sort(nearestManagerFirst);
        return new CandidateLists(
                building.structureRevision(),
                List.copyOf(interior),
                List.copyOf(exterior)
        );
    }

    private record CandidateLists(
            long structureRevision,
            List<BlockPos> interior,
            List<BlockPos> exterior
    ) {
        private static final CandidateLists EMPTY =
                new CandidateLists(-1L, List.of(), List.of());
    }
}

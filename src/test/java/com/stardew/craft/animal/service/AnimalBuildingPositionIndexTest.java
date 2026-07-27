package com.stardew.craft.animal.service;

import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalBuildingType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class AnimalBuildingPositionIndexTest {
    @Test
    void candidatesAreSharedUntilStructureRevisionChanges() {
        AnimalBuildingRecord building = building(Set.of(
                new BlockPos(4, 64, 4).asLong(),
                new BlockPos(1, 64, 1).asLong(),
                new BlockPos(2, 64, 2).asLong()
        ));

        List<BlockPos> first =
                AnimalBuildingPositionIndex.interiorCandidates(building);
        List<BlockPos> second =
                AnimalBuildingPositionIndex.interiorCandidates(building);

        assertSame(first, second);
        assertEquals(new BlockPos(1, 64, 1), first.getFirst());

        building.markStructureInvalid("wall changed");
        List<BlockPos> afterRevision =
                AnimalBuildingPositionIndex.interiorCandidates(building);
        assertNotSame(first, afterRevision);
        assertEquals(first, afterRevision);
    }

    @Test
    void legacyBoundingBoxBuildsCandidateVolumesOnlyOnce() {
        AnimalBuildingRecord building = building(Set.of());

        List<BlockPos> interior =
                AnimalBuildingPositionIndex.interiorCandidates(building);
        List<BlockPos> exterior =
                AnimalBuildingPositionIndex.exteriorCandidates(building);

        assertEquals(4 * 2 * 4, interior.size());
        assertEquals(
                (6 * 4 * 6) - interior.size(),
                exterior.size());
        assertSame(
                interior,
                AnimalBuildingPositionIndex.interiorCandidates(building));
        assertSame(
                exterior,
                AnimalBuildingPositionIndex.exteriorCandidates(building));
    }

    private static AnimalBuildingRecord building(
            Set<Long> interior
    ) {
        return new AnimalBuildingRecord(
                "coop-cache-test",
                "00000000-0000-0000-0000-000000000001",
                AnimalBuildingType.COOP_TIER_1,
                "",
                "stardewcraft:test",
                new BlockPos(1, 64, 1),
                8,
                1,
                64,
                1,
                4,
                65,
                4,
                4,
                0,
                true,
                false,
                interior,
                Set.of(),
                Set.of()
        );
    }
}

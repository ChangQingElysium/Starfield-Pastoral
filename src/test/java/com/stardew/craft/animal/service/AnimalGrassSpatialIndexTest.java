package com.stardew.craft.animal.service;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalGrassSpatialIndexTest {
    @Test
    void findsNearestIndexedGrassWithoutVolumeScan() {
        AnimalGrassSpatialIndex index =
                new AnimalGrassSpatialIndex();
        index.replaceSection(0, 4, 0, List.of(
                new BlockPos(3, 64, 2),
                new BlockPos(7, 64, 7)
        ));

        assertEquals(
                new BlockPos(3, 64, 2),
                index.findNearest(
                        new BlockPos(0, 64, 0),
                        7,
                        2,
                        ignored -> true)
        );
    }

    @Test
    void updatesOnlySectionsThatHaveBeenIndexed() {
        AnimalGrassSpatialIndex index =
                new AnimalGrassSpatialIndex();
        BlockPos indexed = new BlockPos(1, 64, 1);
        BlockPos notIndexed = new BlockPos(33, 64, 1);
        index.replaceSection(0, 4, 0, List.of());

        index.update(indexed, true);
        index.update(notIndexed, true);

        assertEquals(
                indexed,
                index.findNearest(
                        new BlockPos(0, 64, 0),
                        7,
                        2,
                        ignored -> true)
        );
        assertFalse(index.isIndexed(2, 4, 0));
    }

    @Test
    void excludesReservedCandidatesAndInvalidatedChunks() {
        AnimalGrassSpatialIndex index =
                new AnimalGrassSpatialIndex();
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(4, 64, 1);
        index.replaceSection(0, 4, 0, List.of(first, second));

        assertEquals(
                second,
                index.findNearest(
                        new BlockPos(0, 64, 0),
                        7,
                        2,
                        packed -> packed != first.asLong())
        );

        index.invalidateChunk(0, 0);
        assertFalse(index.isIndexed(0, 4, 0));
        assertNull(index.findNearest(
                new BlockPos(0, 64, 0),
                7,
                2,
                ignored -> true));
    }
}

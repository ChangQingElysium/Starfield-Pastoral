package com.stardew.craft.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmChunkManagerTest {

    @Test
    void includesEveryChunkTouchedByFarmBounds() {
        Set<ChunkPos> chunks = FarmChunkManager.chunkPositionsForBounds(
            new BlockPos(-17, 40, -1),
            new BlockPos(16, 90, 32)
        );

        assertEquals(16, chunks.size());
        assertTrue(chunks.contains(new ChunkPos(-2, -1)));
        assertTrue(chunks.contains(new ChunkPos(1, 2)));
    }

    @Test
    void normalizesReversedBounds() {
        Set<ChunkPos> chunks = FarmChunkManager.chunkPositionsForBounds(
            new BlockPos(31, 90, 31),
            new BlockPos(0, 40, 0)
        );

        assertEquals(Set.of(
            new ChunkPos(0, 0), new ChunkPos(0, 1),
            new ChunkPos(1, 0), new ChunkPos(1, 1)
        ), chunks);
    }
}

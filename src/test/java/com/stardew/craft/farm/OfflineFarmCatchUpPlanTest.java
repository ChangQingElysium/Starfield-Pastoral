package com.stardew.craft.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfflineFarmCatchUpPlanTest {
    @Test
    void keepsOnlyFarmObjectsAndLoadsRequiredNeighborhoods() {
        GlobalPos crop = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 1));
        GlobalPos tree = GlobalPos.of(Level.OVERWORLD, new BlockPos(15, 64, 15));
        GlobalPos sprinkler = GlobalPos.of(Level.OVERWORLD, new BlockPos(31, 64, 31));
        GlobalPos outside = GlobalPos.of(Level.OVERWORLD, new BlockPos(32, 64, 31));
        GlobalPos otherDimension = GlobalPos.of(Level.NETHER, new BlockPos(1, 64, 1));

        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                new BlockPos(0, 0, 0),
                new BlockPos(31, 255, 31),
                List.of(outside, crop, otherDimension),
                List.of(tree),
                List.of(sprinkler));

        assertEquals(List.of(crop), plan.crops());
        assertEquals(List.of(tree), plan.trees());
        assertEquals(List.of(sprinkler), plan.sprinklers());
        assertEquals(Set.of(
                new ChunkPos(0, 0), new ChunkPos(0, 1), new ChunkPos(1, 0), new ChunkPos(1, 1),
                new ChunkPos(1, 2), new ChunkPos(2, 1), new ChunkPos(2, 2)), plan.requiredChunks());
    }

    @Test
    void normalizesReversedFarmBoundsWithoutChangingManagerIterationOrder() {
        GlobalPos later = GlobalPos.of(Level.OVERWORLD, new BlockPos(20, 70, 20));
        GlobalPos earlier = GlobalPos.of(Level.OVERWORLD, new BlockPos(2, 70, 4));

        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                new BlockPos(31, 255, 31),
                new BlockPos(0, 0, 0),
                List.of(later, earlier), List.of(), List.of());

        assertEquals(List.of(later, earlier), plan.crops());
    }
}

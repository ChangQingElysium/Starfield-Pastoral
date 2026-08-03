package com.stardew.craft.fishpond.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FishPondWorldDataOwnershipTest {
    @Test
    void repairsClickingPlayerOwnershipFromPondLocation() {
        UUID clickingPlayer = UUID.fromString(
                "00000000-0000-0000-0000-000000000007");
        UUID farmOwner = UUID.fromString(
                "00000000-0000-0000-0000-000000000099");
        FishPondWorldData data = new FishPondWorldData();
        String pondId = data.createPond(
                clickingPlayer,
                "stardewcraft:stardew_valley",
                new BlockPos(10, 64, 10),
                new BlockPos(11, 64, 10),
                Set.of(),
                Set.of(),
                9, 63, 9,
                13, 65, 13);

        assertEquals(1, data.reconcileFarmOwnership(
                "stardewcraft:stardew_valley", ignored -> farmOwner));
        assertEquals(farmOwner.toString(), data.getPond(pondId)
                .orElseThrow().ownerPlayerUuid());
        assertEquals(0, data.reconcileFarmOwnership(
                "stardewcraft:stardew_valley", ignored -> farmOwner));
    }
}

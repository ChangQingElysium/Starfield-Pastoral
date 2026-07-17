package com.stardew.craft.world;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OldMasterCannoliServiceTest {
    @Test
    void claimStateIsStoredPerPlayer() {
        PlayerStardewData first = new PlayerStardewData(UUID.randomUUID());
        PlayerStardewData second = new PlayerStardewData(UUID.randomUUID());

        first.addMailFlag(OldMasterCannoliService.CLAIM_FLAG);

        assertTrue(OldMasterCannoliService.hasClaimed(first));
        assertFalse(OldMasterCannoliService.hasClaimed(second));
    }

    @Test
    void interactionOccupiesTheAuthoredTwoByTwoStatueArea() {
        assertEquals(new net.minecraft.core.BlockPos(-259, 68, 6),
                OldMasterCannoliService.INTERACTION_MIN);
        assertEquals(new net.minecraft.core.BlockPos(-258, 69, 6),
                OldMasterCannoliService.INTERACTION_MAX);
        assertEquals(4L, StreamSupport.stream(net.minecraft.core.BlockPos.betweenClosed(
                OldMasterCannoliService.INTERACTION_MIN,
                OldMasterCannoliService.INTERACTION_MAX).spliterator(), false).count());
    }
}

package com.stardew.craft.secretnote;

import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretNote20ServiceTest {
    @Test
    void truckRequiresTheNoteAndStopsAfterCompletion() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        assertFalse(SecretNote20Service.canUseTruck(data));

        data.markSecretNoteSeen(SecretNote20Service.NOTE_ID);
        assertTrue(SecretNote20Service.canUseTruck(data));

        data.addMailFlag(SecretNote20Service.DONE_FLAG);
        assertFalse(SecretNote20Service.canUseTruck(data));
    }

    @Test
    void specialCharmAddsVanillaDailyLuckBonus() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        data.setDailyLuckForDate(0.05D, 1);
        assertEquals(0.05D, data.getDailyLuck(), 0.000001D);

        data.addMailFlag(SecretNote20Service.SPECIAL_CHARM_FLAG);
        assertEquals(0.075D, data.getDailyLuck(), 0.000001D);
    }

    @Test
    void truckCoordinatesCoverExactlyTheTwoRequestedBlocks() {
        assertTrue(SecretNote20Service.isTruckBlock(new BlockPos(122, 67, -21)));
        assertTrue(SecretNote20Service.isTruckBlock(new BlockPos(123, 67, -21)));
        assertFalse(SecretNote20Service.isTruckBlock(new BlockPos(124, 67, -21)));
        assertFalse(SecretNote20Service.isTruckBlock(new BlockPos(122, 68, -21)));
    }
}

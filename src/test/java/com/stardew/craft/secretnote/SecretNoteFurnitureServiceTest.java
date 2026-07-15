package com.stardew.craft.secretnote;

import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretNoteFurnitureServiceTest {
    @Test
    void plushUsesTheAuthoredBushRange() {
        assertTrue(SecretNoteFurnitureService.isJunimoPlushBush(new BlockPos(-27, 66, -69)));
        assertTrue(SecretNoteFurnitureService.isJunimoPlushBush(new BlockPos(-25, 67, -67)));
        assertFalse(SecretNoteFurnitureService.isJunimoPlushBush(new BlockPos(-28, 66, -69)));
        assertFalse(SecretNoteFurnitureService.isJunimoPlushBush(new BlockPos(-25, 68, -67)));
    }

    @Test
    void plushMatchesVanillaExactDayAndNoonConditions() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());

        assertFalse(SecretNoteFurnitureService.canClaimJunimoPlush(data, 27, 12 * 60));
        assertFalse(SecretNoteFurnitureService.canClaimJunimoPlush(data, 28, 12 * 60 - 1));
        assertTrue(SecretNoteFurnitureService.canClaimJunimoPlush(data, 28, 12 * 60));
        assertTrue(SecretNoteFurnitureService.canClaimJunimoPlush(data, 28, 12 * 60 + 9));
        assertFalse(SecretNoteFurnitureService.canClaimJunimoPlush(data, 28, 12 * 60 + 10));

        data.addMailFlag(SecretNoteFurnitureService.JUNIMO_PLUSH_FLAG);
        assertFalse(SecretNoteFurnitureService.canClaimJunimoPlush(data, 28, 12 * 60));
    }

    @Test
    void stoneJunimoCanOnlyBeClaimedOncePerPlayer() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        assertTrue(SecretNoteFurnitureService.canClaimStoneJunimo(data));
        data.addMailFlag(SecretNoteFurnitureService.STONE_JUNIMO_FLAG);
        assertFalse(SecretNoteFurnitureService.canClaimStoneJunimo(data));
    }

    @Test
    void stoneJunimoAcceptsItsVisibleMainAndExtensionCells() {
        assertTrue(SecretNoteFurnitureService.isStoneJunimoPart(new BlockPos(52, 66, -63)));
        assertTrue(SecretNoteFurnitureService.isStoneJunimoPart(new BlockPos(52, 67, -63)));
        assertFalse(SecretNoteFurnitureService.isStoneJunimoPart(new BlockPos(52, 68, -63)));
    }
}

package com.stardew.craft.casino;

import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoAccessServiceTest {
    @Test
    void casinoBoundsTreatAuthoredMaximumBlocksAsInclusive() {
        assertTrue(CasinoAccessService.isCasinoPosition(-250.0D, 28.0D, -171.0D));
        assertTrue(CasinoAccessService.isCasinoPosition(-218.001D, 40.999D, -152.001D));
        assertFalse(CasinoAccessService.isCasinoPosition(-250.001D, 28.0D, -171.0D));
        assertFalse(CasinoAccessService.isCasinoPosition(-218.0D, 40.0D, -153.0D));
        assertFalse(CasinoAccessService.isCasinoPosition(-219.0D, 41.0D, -153.0D));
        assertFalse(CasinoAccessService.isCasinoPosition(-219.0D, 40.0D, -152.0D));
    }

    @Test
    void casinoInteractionCoordinatesMatchTheApprovedLayout() {
        assertEquals(new BlockPos(-237, 36, -170), CasinoAccessService.QI_COIN_MACHINE_MIN);
        assertEquals(new BlockPos(-236, 37, -170), CasinoAccessService.QI_COIN_MACHINE_MAX);
        assertEquals(new BlockPos(-222, 37, -170), CasinoAccessService.QI_COIN_SHOP_MIN);
        assertEquals(new BlockPos(-222, 38, -170), CasinoAccessService.QI_COIN_SHOP_MAX);
        assertEquals(-239.0D, CasinoAccessService.ENTRY_DESTINATION.x, 0.0001D);
        assertEquals(33.5D, CasinoAccessService.ENTRY_DESTINATION.y, 0.0001D);
        assertEquals(-155.0D, CasinoAccessService.ENTRY_DESTINATION.z, 0.0001D);
    }
}

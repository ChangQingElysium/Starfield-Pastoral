package com.stardew.craft.secretnote;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretNote25ServiceTest {
    @Test
    void acceptsEveryInclusiveBoundaryOfTheConfiguredSpaPool() {
        assertTrue(SecretNote25Service.isSpaPoolHookPosition(new BlockPos(-16, 84, -179)));
        assertTrue(SecretNote25Service.isSpaPoolHookPosition(new BlockPos(-11, 86, -174)));
        assertTrue(SecretNote25Service.isSpaPoolHookPosition(new BlockPos(-14, 85, -176)));
    }

    @Test
    void rejectsPositionsImmediatelyOutsideTheConfiguredSpaPool() {
        assertFalse(SecretNote25Service.isSpaPoolHookPosition(new BlockPos(-17, 85, -176)));
        assertFalse(SecretNote25Service.isSpaPoolHookPosition(new BlockPos(-10, 85, -176)));
        assertFalse(SecretNote25Service.isSpaPoolHookPosition(new BlockPos(-14, 83, -176)));
        assertFalse(SecretNote25Service.isSpaPoolHookPosition(new BlockPos(-14, 87, -176)));
        assertFalse(SecretNote25Service.isSpaPoolHookPosition(new BlockPos(-14, 85, -180)));
        assertFalse(SecretNote25Service.isSpaPoolHookPosition(new BlockPos(-14, 85, -173)));
    }
}

package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScarecrowBlockTest {
    private static final BlockPos CENTER = new BlockPos(10, 64, 10);

    @Test
    void protectionUsesTheSameInclusiveCircleAsCrowAttacks() {
        assertTrue(ScarecrowBlock.protects(CENTER, CENTER.offset(9, 0, 0), 9));
        assertFalse(ScarecrowBlock.protects(CENTER, CENTER.offset(9, 0, 1), 9));
    }

    @Test
    void protectionIgnoresHeightLikeTheServerManager() {
        assertTrue(ScarecrowBlock.protects(CENTER, CENTER.offset(3, 20, 4), 5));
    }
}

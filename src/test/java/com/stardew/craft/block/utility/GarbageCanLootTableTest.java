package com.stardew.craft.block.utility;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GarbageCanLootTableTest {
    @Test
    void userCapturedTownBinsResolveToVanillaCanIds() {
        assertEquals("Evelyn", GarbageCanLootTable.namedCanId(new BlockPos(40, 64, 2)));
        assertEquals("Saloon", GarbageCanLootTable.namedCanId(new BlockPos(33, 64, 12)));
        assertEquals("Blacksmith", GarbageCanLootTable.namedCanId(new BlockPos(112, 64, 27)));
        assertEquals("Museum", GarbageCanLootTable.namedCanId(new BlockPos(134, 64, 44)));
        assertNull(GarbageCanLootTable.namedCanId(new BlockPos(40, 64, 3)));
    }
}

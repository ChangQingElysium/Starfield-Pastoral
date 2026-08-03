package com.stardew.craft.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrismaticSlimeSpawnRulesTest {
    @Test
    void chanceMatchesOriginalDailyLuckFormula() {
        assertEquals(0.012D, MineMonsterSpawnHandler.prismaticSlimeChance(0.0D), 0.000001D);
        assertEquals(0.022D, MineMonsterSpawnHandler.prismaticSlimeChance(0.1D), 0.000001D);
        assertEquals(0.01D, MineMonsterSpawnHandler.prismaticSlimeChance(-0.1D), 0.000001D);
    }
}

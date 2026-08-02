package com.stardew.craft.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineGenerationBalanceTest {
    @Test
    void miningCostScalesWithOriginalStoneHealthAndPickaxeDamage() {
        assertEquals(4, MineGenerationBalance.pickaxeStrikes(16, 4));
        assertEquals(3, MineGenerationBalance.pickaxeStrikes(3, 0));
        assertEquals(1, MineGenerationBalance.pickaxeStrikes(4, 3));

        assertEquals(4.0F,
                MineGenerationBalance.miningEnergyCost(16, 4, 10),
                0.000_001F);
        assertEquals(6.0F,
                MineGenerationBalance.miningEnergyCost(3, 0, 0),
                0.000_001F);
    }

    @Test
    void resourcesScaleWithTraversableSurfaceWithoutDependingOnSolidVolume() {
        double stoneChance = 0.20;

        double compactProbability = MineGenerationBalance.metalChancePerExposedStone(
                91, stoneChance, 1_200, 4_000);
        double complexProbability = MineGenerationBalance.metalChancePerExposedStone(
                91, stoneChance, 1_200, 12_000);

        assertEquals(1_200 * stoneChance * 0.029, compactProbability * 4_000, 0.000_001);
        assertEquals(1_200 * stoneChance * 0.029, complexProbability * 12_000, 0.000_001);

        double doubleMapProbability = MineGenerationBalance.metalChancePerExposedStone(
                91, stoneChance, 2_400, 12_000);
        assertEquals(2.0, doubleMapProbability / complexProbability, 0.000_001);
    }

    @Test
    void regularMineUsesOriginalOreNodeAndTypeProbabilities() {
        assertEquals(0.20 * 0.029,
                MineGenerationBalance.metalChancePerTraversableTile(31, 0.20), 0.000_001);
        assertEquals(0.20 * 0.029,
                MineGenerationBalance.metalChancePerTraversableTile(1, 0.20), 0.000_001);
        assertEquals(0.0,
                MineGenerationBalance.metalChancePerTraversableTile(95, 0.20), 0.000_001);

        assertEquals("iron", MineGenerationBalance.pickRegularOreKey(30, 0.05, 0.90));
        assertEquals("copper", MineGenerationBalance.pickRegularOreKey(30, 0.50, 0.00));
        assertEquals("gold", MineGenerationBalance.pickRegularOreKey(70, 0.05, 0.90));
        assertEquals("iron", MineGenerationBalance.pickRegularOreKey(70, 0.50, 0.50));
        assertEquals("copper", MineGenerationBalance.pickRegularOreKey(70, 0.50, 0.90));
        assertEquals("gold", MineGenerationBalance.pickRegularOreKey(100, 0.50, 0.90));
        assertEquals("iron", MineGenerationBalance.pickRegularOreKey(100, 0.90, 0.50));
        assertEquals("copper", MineGenerationBalance.pickRegularOreKey(100, 0.90, 0.90));

        assertEquals(0.20 * (0.00025 + 61.0 / 120_000.0),
                MineGenerationBalance.diamondChancePerTraversableTile(61, 0.20),
                0.000_001);
        assertEquals(0.0,
                MineGenerationBalance.diamondChancePerTraversableTile(50, 0.20),
                0.000_001);
        assertTrue(MineGenerationBalance.diamondChancePerTraversableTile(55, 0.20) > 0.0);
        assertEquals(0.0,
                MineGenerationBalance.diamondChancePerTraversableTile(60, 0.20),
                0.000_001);
        assertEquals(0.20 * (0.003 + 1.0 / 24_000.0),
                MineGenerationBalance.gemChancePerTraversableTile(1, 0.20),
                0.000_001);
    }

    @Test
    void skullCavernDepthCurveMatchesOriginalProbabilities() {
        assertEquals(0.0205, MineGenerationBalance.skullCavernOreChance(121), 0.000_001);
        assertEquals(0.085, MineGenerationBalance.skullCavernOreChance(170), 0.000_001);
        assertEquals(0.16, MineGenerationBalance.skullCavernOreChance(220), 0.000_001);

        assertEquals(0.0003, MineGenerationBalance.skullCavernIridiumChance(121), 0.000_001);
        assertEquals(0.003, MineGenerationBalance.skullCavernIridiumChance(130), 0.000_001);
        assertEquals(0.215, MineGenerationBalance.skullCavernIridiumChance(170), 0.000_001);
        assertEquals(0.43, MineGenerationBalance.skullCavernIridiumChance(220), 0.000_001);

        assertEquals(0.20 * 0.16 * 0.43 * 12.0,
                MineGenerationBalance.richRoomIridiumChance(220, 0.20, 12.0),
                0.000_001);
        assertEquals(0.20 * 0.029 * 6.0,
                MineGenerationBalance.richRoomMetalChance(91, 0.20, 6.0),
                0.000_001);
    }

    @Test
    void secondaryRewardsAndProgressionUseTileDensityInsteadOfFixedCaps() {
        assertEquals(0.8 * 0.988 * 0.0025,
                MineGenerationBalance.surfaceItemChancePerTile(86, 0.20, 0.50),
                0.000_001);
        assertEquals(0.8 * 0.0025,
                MineGenerationBalance.surfaceItemChancePerTile(1, 0.20, 0.50),
                0.000_001);
        assertEquals(0.0,
                MineGenerationBalance.surfaceItemChancePerTile(80, 0.20, 0.50),
                0.000_001);
        assertEquals(1.0 / 1_200.0, MineGenerationBalance.barrelChancePerTile(81), 0.000_001);

        assertEquals(200, MineGenerationBalance.ladderStoneBudget(1_000, 0.20));
        assertEquals(1_000, MineGenerationBalance.ladderStoneBudget(5_000, 0.20));

        double normalMonsterChance = MineGenerationBalance.monsterChancePerTile(85, 0.20, 0.50);
        double skullMonsterChance = MineGenerationBalance.monsterChancePerTile(185, 0.20, 0.50);
        assertTrue(normalMonsterChance > 0.0);
        assertEquals(normalMonsterChance * 1.25, skullMonsterChance, 0.000_001);
        assertEquals(0.0,
                MineGenerationBalance.monsterChancePerTile(80, 0.20, 0.50),
                0.000_001);
        assertTrue(MineGenerationBalance.monsterChancePerTile(125, 0.20, 0.50) > 0.0);
    }
}

package com.stardew.craft.combat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossDimensionAttributeRulesTest {
    @Test
    void defenseMapsToMinecraftArmorWithoutChangingTheStardewValue() {
        assertEquals(0.0D, CrossDimensionAttributeRules.minecraftArmor(-1.0f));
        assertEquals(5.0D, CrossDimensionAttributeRules.minecraftArmor(5.0f));
    }

    @Test
    void attackKeepsStardewsThreeDamagePerLevelRule() {
        assertEquals(0.0D, CrossDimensionAttributeRules.minecraftAttackDamage(-1.0f));
        assertEquals(6.0D, CrossDimensionAttributeRules.minecraftAttackDamage(2.0f));
    }

    @Test
    void luckLevelAndDailyLuckBothReachMinecraftLuck() {
        assertEquals(2.0D, CrossDimensionAttributeRules.minecraftLuck(1.0f, 0.10D));
        assertEquals(0.0D, CrossDimensionAttributeRules.minecraftLuck(1.0f, -0.10D));
    }
}

package com.stardew.craft.combat.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatRingRulesTest {
    @Test
    void warriorChanceIncludesLuckAndIsBounded() {
        assertEquals(0.10f, CombatRingRules.warriorTriggerChance(0.0f));
        assertEquals(0.15f, CombatRingRules.warriorTriggerChance(5.0f));
        assertEquals(1.0f, CombatRingRules.warriorTriggerChance(1000.0f));
    }

    @Test
    void yobaChanceRisesAsHealthFalls() {
        assertEquals(0.0f, CombatRingRules.yobaProtectionChance(100, 0.0f));
        assertEquals(0.13333333f, CombatRingRules.yobaProtectionChance(50, 0.0f), 0.00001f);
        assertEquals(0.45f, CombatRingRules.yobaProtectionChance(15, 0.0f), 0.00001f);
    }

    @Test
    void yobaLuckUsesTheOriginalDenominator() {
        assertEquals(0.20f, CombatRingRules.yobaProtectionChance(50, 10.0f), 0.00001f);
        assertEquals(1.0f, CombatRingRules.yobaProtectionChance(1, 30.0f));
    }

    @Test
    void protectionRingExtendsPostHitInvulnerability() {
        assertEquals(24, CombatRingRules.invulnerabilityTicks(0));
        assertEquals(32, CombatRingRules.invulnerabilityTicks(1));
        assertEquals(40, CombatRingRules.invulnerabilityTicks(2));
        assertEquals(20, CombatRingRules.minecraftInvulnerabilityTicks(20, 0));
        assertEquals(28, CombatRingRules.minecraftInvulnerabilityTicks(20, 1));
        assertEquals(36, CombatRingRules.minecraftInvulnerabilityTicks(20, 2));
    }

    @Test
    void phoenixRestoresHalfHealthPlusEquippedRingCount() {
        assertEquals(51, CombatRingRules.phoenixReviveHealth(100, 1));
        assertEquals(52, CombatRingRules.phoenixReviveHealth(100, 2));
        assertEquals(51, CombatRingRules.phoenixReviveHealth(101, 1));
        assertEquals(1, CombatRingRules.phoenixReviveHealth(1, 2));
    }

    @Test
    void thornsMatchesTheOriginalLowDamageAverageAndStacks() {
        assertEquals(0, CombatRingRules.thornsDamage(16, 8, 0));
        assertEquals(12, CombatRingRules.thornsDamage(16, 8, 1));
        assertEquals(24, CombatRingRules.thornsDamage(16, 8, 2));
        assertEquals(16, CombatRingRules.thornsDamage(16, 10, 1));
    }

    @Test
    void weaponSpeedConvertsDurationReductionToAttackRate() {
        assertEquals(
                1.0f / 0.9f - 1.0f,
                CombatRingRules.weaponSpeedToAttackRateBonus(0.1f),
                0.00001f
        );
        assertEquals(
                0.25f,
                CombatRingRules.weaponSpeedToAttackRateBonus(0.2f),
                0.00001f
        );
    }

    @Test
    void yobaHealthProjectionUsesPercentageOutsideStardewDimensions() {
        assertEquals(100, CombatRingRules.healthOnStardewScale(20.0f, 20.0f));
        assertEquals(50, CombatRingRules.healthOnStardewScale(10.0f, 20.0f));
        assertEquals(15, CombatRingRules.healthOnStardewScale(3.0f, 20.0f));
    }

    @Test
    void phoenixHealthUsesTheHealthBoundaryOnlyForItsFlatBonus() {
        assertEquals(
                10.2f,
                CombatRingRules.phoenixMinecraftHealth(20.0f, 1, 5.0f),
                0.00001f
        );
        assertEquals(
                10.4f,
                CombatRingRules.phoenixMinecraftHealth(20.0f, 2, 5.0f),
                0.00001f
        );
    }
}

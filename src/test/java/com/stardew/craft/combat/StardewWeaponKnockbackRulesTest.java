package com.stardew.craft.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewWeaponKnockbackRulesTest {
    @Test
    void defaultsAndTooltipDeltasFollowMeleeWeaponTypes() {
        assertEquals(1.0F, StardewWeaponKnockbackRules.defaultRawKnockback(
                WeaponType.SWORD
        ));
        assertEquals(0, StardewWeaponKnockbackRules.tooltipWeightPoints(
                WeaponType.DAGGER, 0.5F
        ));
        assertEquals(5, StardewWeaponKnockbackRules.tooltipWeightPoints(
                WeaponType.DAGGER, 1.0F
        ));
        assertEquals(-2, StardewWeaponKnockbackRules.tooltipWeightPoints(
                WeaponType.SWORD, 0.8F
        ));
    }

    @Test
    void minecraftProjectionPreservesEachTypesRelativeStrength() {
        assertEquals(0.4F, StardewWeaponKnockbackRules.minecraftStrength(
                WeaponType.SWORD, 1.0F
        ), 0.00001F);
        assertEquals(0.6F, StardewWeaponKnockbackRules.minecraftStrength(
                WeaponType.DAGGER, 1.0F
        ), 0.00001F);
        assertEquals(0.5F, StardewWeaponKnockbackRules.minecraftStrength(
                WeaponType.CLUB, 1.2F
        ), 0.00001F);
    }
}

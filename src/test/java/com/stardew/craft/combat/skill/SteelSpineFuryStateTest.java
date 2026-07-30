package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteelSpineFuryStateTest {
    @Test
    void strongChargeAddsFortyPercentOfIncomingDamageRoundedUpAndCappedAtTwelve() {
        assertEquals(1, SteelSpineFuryState.calculateBonusDamage(1));
        assertEquals(1, SteelSpineFuryState.calculateBonusDamage(2));
        assertEquals(2, SteelSpineFuryState.calculateBonusDamage(3));
        assertEquals(12, SteelSpineFuryState.calculateBonusDamage(30));
        assertEquals(12, SteelSpineFuryState.calculateBonusDamage(100));
    }

    @Test
    void chargedStrikeUsesFlatBonusWithoutMultiplyingBaseDamage() {
        SteelSpineFuryState.AttackBoost boost =
                SteelSpineFuryState.createAttackBoost(false, 7);

        assertTrue(boost.strong());
        assertEquals(7, boost.bonusDamage());
        assertEquals(1.0F, boost.damageMultiplier());
    }

    @Test
    void expiredUnchargedStanceUsesFortyPercentMultiplierWithoutFlatBonus() {
        SteelSpineFuryState.AttackBoost boost =
                SteelSpineFuryState.createAttackBoost(true, 7);

        assertFalse(boost.strong());
        assertEquals(0, boost.bonusDamage());
        assertEquals(1.4F, boost.damageMultiplier());
    }
}

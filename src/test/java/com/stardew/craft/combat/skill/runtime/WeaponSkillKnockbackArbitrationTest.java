package com.stardew.craft.combat.skill.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillKnockbackArbitrationTest {
    @Test
    void onlyEffectiveKnockbackRevokesSkillMovement() {
        assertTrue(WeaponSkillKnockbackArbitration.hasEffectiveKnockback(
                0.7D,
                0.0D
        ));
        assertTrue(WeaponSkillKnockbackArbitration.hasEffectiveKnockback(
                0.7D,
                0.5D
        ));
        assertFalse(WeaponSkillKnockbackArbitration.hasEffectiveKnockback(
                0.7D,
                1.0D
        ));
        assertFalse(WeaponSkillKnockbackArbitration.hasEffectiveKnockback(
                0.0D,
                0.0D
        ));
    }
}

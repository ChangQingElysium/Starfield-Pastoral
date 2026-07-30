package com.stardew.craft.combat.debuff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmunitySystemTest {
    @Test
    void immunityUsesStardewsElevenOutcomeRoll() {
        assertFalse(ImmunitySystem.resistsWithRoll(0, 0));
        assertTrue(ImmunitySystem.resistsWithRoll(4, 0));
        assertTrue(ImmunitySystem.resistsWithRoll(4, 3));
        assertFalse(ImmunitySystem.resistsWithRoll(4, 4));
        assertFalse(ImmunitySystem.resistsWithRoll(4, 10));
        assertTrue(ImmunitySystem.resistsWithRoll(11, 10));
    }

    @Test
    void displayedChanceMatchesTheRollRule() {
        assertEquals(0.0f, ImmunitySystem.calculateResistChance(0));
        assertEquals(4.0f / 11.0f, ImmunitySystem.calculateResistChance(4));
        assertEquals(1.0f, ImmunitySystem.calculateResistChance(11));
    }

    @Test
    void sturdyHalvesFiniteNegativeEffectDuration() {
        assertEquals(100, ImmunitySystem.adjustDurationTicks(200, true));
        assertEquals(101, ImmunitySystem.adjustDurationTicks(101, false));
        assertEquals(1, ImmunitySystem.adjustDurationTicks(1, true));
        assertEquals(-1, ImmunitySystem.adjustDurationTicks(-1, true));
    }
}

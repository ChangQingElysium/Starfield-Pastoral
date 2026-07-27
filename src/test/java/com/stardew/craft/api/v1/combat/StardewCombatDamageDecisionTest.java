package com.stardew.craft.api.v1.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewCombatDamageDecisionTest {
    @Test
    void factoriesExposeExplicitPassSetAndDenySemantics() {
        assertEquals(
                StardewCombatDamageDecision.Kind.PASS,
                StardewCombatDamageDecision.pass().kind());
        assertEquals(
                2.5F,
                StardewCombatDamageDecision.set(2.5F).amount());
        assertEquals(
                StardewCombatDamageDecision.Kind.DENY,
                StardewCombatDamageDecision.deny().kind());
    }

    @Test
    void setRejectsInvalidDamageAmounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StardewCombatDamageDecision.set(-1.0F));
        assertThrows(
                IllegalArgumentException.class,
                () -> StardewCombatDamageDecision.set(Float.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> StardewCombatDamageDecision.set(
                        Float.POSITIVE_INFINITY));
    }
}

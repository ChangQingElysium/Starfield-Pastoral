package com.stardew.craft.animal.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalNameRulesTest {
    @Test
    void normalizesAndEnforcesOneSharedServerLimit() {
        assertEquals("Marnie", AnimalNameRules.normalize(
                "  Marnie  "));
        assertTrue(AnimalNameRules.isValidExplicitName(
                "x".repeat(AnimalNameRules.MAX_LENGTH)));
        assertFalse(AnimalNameRules.isValidExplicitName(
                "x".repeat(AnimalNameRules.MAX_LENGTH + 1)));
        assertFalse(AnimalNameRules.isValidExplicitName("   "));
        assertTrue(AnimalNameRules.isValidOptionalName("   "));
        assertFalse(AnimalNameRules.isValidOptionalName(
                "x".repeat(AnimalNameRules.MAX_LENGTH + 1)));
    }
}

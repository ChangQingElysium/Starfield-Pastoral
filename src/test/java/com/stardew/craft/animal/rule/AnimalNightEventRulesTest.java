package com.stardew.craft.animal.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalNightEventRulesTest {
    @Test
    void onlyClosedHomesWithAnAnimalActuallyOutsideAreEligible() {
        assertTrue(AnimalNightEventRules.buildingCanBeAttacked(false, 1));
        assertFalse(AnimalNightEventRules.buildingCanBeAttacked(true, 1));
        assertFalse(AnimalNightEventRules.buildingCanBeAttacked(false, 0));
    }

    @Test
    void sourceUsesOneOverTotalFarmBuildingsAsSelectionChance() {
        assertTrue(AnimalNightEventRules.selectsBuilding(0.2499D, 4));
        assertFalse(AnimalNightEventRules.selectsBuilding(0.25D, 4));
        assertFalse(AnimalNightEventRules.selectsBuilding(0.0D, 0));
    }
}

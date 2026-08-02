package com.stardew.craft.animal.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalCatchUpRulesTest {
    @Test
    void oldSaveStartsAtYesterdayAndSettlesCurrentDayOnce() {
        assertEquals(49, AnimalCatchUpRules.initializeCheckpoint(0, 50));
        AnimalCatchUpRules.Step step =
                AnimalCatchUpRules.nextStep(0, 50).orElseThrow();
        assertEquals(50, step.targetAbsDay());
        assertFalse(step.offlineCatchUp());
    }

    @Test
    void backlogAdvancesExactlyOneHistoricalDayWithoutSkipping() {
        AnimalCatchUpRules.Step first =
                AnimalCatchUpRules.nextStep(40, 50).orElseThrow();
        assertEquals(41, first.targetAbsDay());
        assertTrue(first.offlineCatchUp());

        AnimalCatchUpRules.Step finalStep =
                AnimalCatchUpRules.nextStep(49, 50).orElseThrow();
        assertEquals(50, finalStep.targetAbsDay());
        assertFalse(finalStep.offlineCatchUp());
        assertTrue(AnimalCatchUpRules.nextStep(50, 50).isEmpty());
    }

    @Test
    void initialOvernightBudgetVisitsEveryDueAnimalAtLeastOnce() {
        assertEquals(64, AnimalCatchUpRules.initialSettlementBudget(64, 12));
        assertEquals(96, AnimalCatchUpRules.initialSettlementBudget(64, 96));
    }
}

package com.stardew.craft.animal.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalPathfindingBudgetTest {
    @Test
    void capsRequestsAndResetsOnNextTick() {
        AnimalPathfindingBudget.TickBudget budget =
                new AnimalPathfindingBudget.TickBudget();

        assertTrue(budget.tryAcquire(10L, 2));
        assertTrue(budget.tryAcquire(10L, 2));
        assertFalse(budget.tryAcquire(10L, 2));
        assertTrue(budget.tryAcquire(11L, 2));
    }

    @Test
    void pressureBatchCannotBurstPastGlobalLimit() {
        AnimalPathfindingBudget.TickBudget budget =
                new AnimalPathfindingBudget.TickBudget();
        int accepted = 0;
        for (int animal = 0; animal < 96; animal++) {
            if (budget.tryAcquire(
                    42L,
                    AnimalPathfindingBudget
                            .MAX_REQUESTS_PER_TICK)) {
                accepted++;
            }
        }

        org.junit.jupiter.api.Assertions.assertEquals(
                AnimalPathfindingBudget.MAX_REQUESTS_PER_TICK,
                accepted);
    }
}

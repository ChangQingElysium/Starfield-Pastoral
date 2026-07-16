package com.stardew.craft.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewTimePauseServiceTest {

    @Test
    void pausesWhenNoPlayersUseTheStardewClock() {
        assertTrue(StardewTimePauseService.shouldPauseForCounts(0, 0));
    }

    @Test
    void pausesOnlyWhenEveryPresentPlayerIsNonGameplay() {
        assertTrue(StardewTimePauseService.shouldPauseForCounts(1, 1));
        assertTrue(StardewTimePauseService.shouldPauseForCounts(3, 3));
        assertFalse(StardewTimePauseService.shouldPauseForCounts(1, 0));
        assertFalse(StardewTimePauseService.shouldPauseForCounts(3, 2));
    }

    @Test
    void simulationClockAdvancesOnlyWhenExplicitlyRequested() {
        StardewTimeManager time = new StardewTimeManager();

        time.initializeSimulationGameTime(1200L);
        assertEquals(1200L, time.getSimulationGameTime());

        time.advanceSimulationGameTime();
        assertEquals(1201L, time.getSimulationGameTime());

        time.initializeSimulationGameTime(9000L);
        assertEquals(1201L, time.getSimulationGameTime());
    }
}

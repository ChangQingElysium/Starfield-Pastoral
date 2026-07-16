package com.stardew.craft.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewSimulationTaskSchedulerTest {

    @Test
    void doesNotReleaseTasksWhileSimulationIsPaused() {
        assertFalse(StardewSimulationTaskScheduler.isDue(true, 120L, 100L));
    }

    @Test
    void releasesTasksOnlyAfterTheirSimulationDeadline() {
        assertFalse(StardewSimulationTaskScheduler.isDue(false, 99L, 100L));
        assertTrue(StardewSimulationTaskScheduler.isDue(false, 100L, 100L));
        assertTrue(StardewSimulationTaskScheduler.isDue(false, 101L, 100L));
    }
}

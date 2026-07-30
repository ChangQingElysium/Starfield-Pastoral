package com.stardew.craft.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRealTickTaskSchedulerTest {
    @Test
    void futureTaskCannotRunBeforeItsRealServerDeadline() {
        assertFalse(ServerRealTickTaskScheduler.isDue(100, 260));
        assertFalse(ServerRealTickTaskScheduler.isDue(259, 260));
        assertTrue(ServerRealTickTaskScheduler.isDue(260, 260));
        assertTrue(ServerRealTickTaskScheduler.isDue(261, 260));
    }

    @Test
    void signedTickComparisonSurvivesCounterWraparound() {
        int due = Integer.MAX_VALUE - 2;
        assertFalse(ServerRealTickTaskScheduler.isDue(Integer.MAX_VALUE - 3, due));
        assertTrue(ServerRealTickTaskScheduler.isDue(Integer.MAX_VALUE - 2, due));
        assertTrue(ServerRealTickTaskScheduler.isDue(Integer.MIN_VALUE + 2, due));
    }
}

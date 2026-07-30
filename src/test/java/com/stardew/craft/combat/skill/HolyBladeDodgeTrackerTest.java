package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolyBladeDodgeTrackerTest {
    @Test
    void durationEndsAtTheExclusiveEndTick() {
        long startTick = 100L;
        long endTick = startTick + 40L;

        assertFalse(HolyBladeDodgeTracker.isExpired(startTick, endTick));
        assertFalse(HolyBladeDodgeTracker.isExpired(endTick - 1L, endTick));
        assertTrue(HolyBladeDodgeTracker.isExpired(endTick, endTick));
    }
}

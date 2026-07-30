package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolyBladeSanctuaryTrackerTest {
    @Test
    void durationEndsAtTheExclusiveEndTick() {
        long startTick = 100L;
        long endTick = startTick + 80L;

        assertFalse(HolyBladeSanctuaryTracker.isExpired(startTick, endTick));
        assertFalse(HolyBladeSanctuaryTracker.isExpired(endTick - 1L, endTick));
        assertTrue(HolyBladeSanctuaryTracker.isExpired(endTick, endTick));
    }

    @Test
    void pulseBecomesDueAtItsScheduledTick() {
        assertFalse(HolyBladeSanctuaryTracker.shouldPulse(119L, 120L));
        assertTrue(HolyBladeSanctuaryTracker.shouldPulse(120L, 120L));
        assertTrue(HolyBladeSanctuaryTracker.shouldPulse(121L, 120L));
    }
}

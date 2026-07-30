package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwarfDaggerRushTrackerTest {
    @Test
    void activeWindowMatchesTheExactEffectDuration() {
        long startTick = 100L;
        long endTick = startTick + 100L;

        assertTrue(DwarfDaggerRushTracker.isWithinActiveWindow(startTick, endTick));
        assertTrue(DwarfDaggerRushTracker.isWithinActiveWindow(endTick - 1L, endTick));
        assertFalse(DwarfDaggerRushTracker.isWithinActiveWindow(endTick, endTick));
    }
}

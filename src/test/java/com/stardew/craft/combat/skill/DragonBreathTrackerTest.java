package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonBreathTrackerTest {
    @Test
    void stackResourceClampsAndPreservesTheAuthoredMajorThreshold() {
        assertEquals(0, DragonBreathTracker.clampStacks(-1));
        assertEquals(12, DragonBreathTracker.clampStacks(12));
        assertEquals(20, DragonBreathTracker.clampStacks(25));
        assertEquals(20, DragonBreathTracker.MAX_STACKS);
        assertEquals(15, DragonBreathTracker.MAJOR_THRESHOLD);

        assertEquals(13, DragonBreathTracker.stacksAfterDelta(10, 3));
        assertEquals(20, DragonBreathTracker.stacksAfterDelta(19, 3));
        assertEquals(0, DragonBreathTracker.stacksAfterDelta(1, -3));
        assertFalse(DragonBreathTracker.canCastMajor(14));
        assertTrue(DragonBreathTracker.canCastMajor(15));
        assertEquals(0, DragonBreathTracker.consumableMajorStacks(14));
        assertEquals(15, DragonBreathTracker.consumableMajorStacks(15));
        assertEquals(20, DragonBreathTracker.consumableMajorStacks(25));
    }

}

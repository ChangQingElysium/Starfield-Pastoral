package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IridiumNeedleFrenzyTrackerTest {
    @Test
    void preservesTheInclusiveAuthoredEndTick() {
        assertTrue(IridiumNeedleFrenzyTracker.isWithinActiveWindow(120L, 120L));
        assertFalse(IridiumNeedleFrenzyTracker.isWithinActiveWindow(121L, 120L));
    }
}

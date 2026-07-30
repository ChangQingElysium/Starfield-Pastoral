package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssifiedMarkTrackerTest {
    @Test
    void markExpiresAtTheExclusiveEndTick() {
        long startTick = 100L;
        long endTick = startTick + 60L;

        assertFalse(OssifiedMarkTracker.isExpired(startTick, endTick));
        assertFalse(OssifiedMarkTracker.isExpired(endTick - 1L, endTick));
        assertTrue(OssifiedMarkTracker.isExpired(endTick, endTick));
    }

    @Test
    void unusedMarkShortensCooldownToFourSecondsFromCast() {
        long startTick = 100L;

        assertEquals(
                20,
                OssifiedMarkTracker.untriggeredCooldownRemaining(
                        startTick,
                        startTick + 60L
                )
        );
        assertEquals(
                0,
                OssifiedMarkTracker.untriggeredCooldownRemaining(
                        startTick,
                        startTick + 80L
                )
        );
    }
}

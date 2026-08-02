package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WickedKrisPoisonTrackerTest {
    @Test
    void stackClampAndDotDamagePreserveTheAuthoredPoisonRules() {
        assertEquals(5, WickedKrisPoisonTracker.MAX_STACKS);
        assertEquals(20L, WickedKrisPoisonTracker.DOT_INTERVAL_TICKS);
        assertEquals(0.10F, WickedKrisPoisonTracker.STACK_DAMAGE_RATIO);

        assertEquals(1, WickedKrisPoisonTracker.clampStacks(0));
        assertEquals(3, WickedKrisPoisonTracker.clampStacks(3));
        assertEquals(5, WickedKrisPoisonTracker.clampStacks(8));
        assertEquals(0.0F, WickedKrisPoisonTracker.dotDamageMultiplier(0));
        assertEquals(0.5F, WickedKrisPoisonTracker.dotDamageMultiplier(5));
    }

    @Test
    void detonationConstantsRemainAvailableForTheSiblingNestBurstSkill() {
        assertEquals(60, WickedKrisPoisonTracker.DETONATE_DELAY_TICKS);
        assertEquals(1.5F, WickedKrisPoisonTracker.DETONATE_MULTIPLIER);
        assertEquals(3.5F, WickedKrisPoisonTracker.DETONATE_RADIUS);

        assertEquals(7, WickedKrisPoisonTracker.remainingDotApplications(200L, 60L));
        assertEquals(1, WickedKrisPoisonTracker.remainingDotApplications(200L, 199L));
        assertEquals(0, WickedKrisPoisonTracker.remainingDotApplications(200L, 200L));
        assertEquals(
                5.25F,
                WickedKrisPoisonTracker.detonationDamageMultiplier(200L, 60L, 5)
        );
    }

    @Test
    void authoredDurationsIncludeTheirTerminalDotApplication() {
        assertEquals(
                5,
                WickedKrisPoisonTracker.scheduledDotApplications(100L, 20L)
        );
        assertEquals(
                10,
                WickedKrisPoisonTracker.scheduledDotApplications(200L, 20L)
        );
        assertEquals(
                7,
                WickedKrisPoisonTracker.scheduledDotApplications(200L, 80L)
        );
        assertEquals(
                3,
                WickedKrisPoisonTracker.scheduledDotApplications(110L, 70L)
        );
        assertEquals(
                1,
                WickedKrisPoisonTracker.scheduledDotApplications(200L, 200L)
        );
        assertEquals(
                0,
                WickedKrisPoisonTracker.scheduledDotApplications(200L, 201L)
        );
    }
}

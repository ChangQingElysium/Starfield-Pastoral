package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokenTridentThrustTrackerTest {
    @Test
    void fishCatchAddsTenPercentagePointsToEachActiveStrike() {
        assertEquals(
                0.40F,
                BrokenTridentThrustTracker.damageMultiplier(0.40F, false)
        );
        assertEquals(
                0.50F,
                BrokenTridentThrustTracker.damageMultiplier(0.40F, true)
        );
    }
}

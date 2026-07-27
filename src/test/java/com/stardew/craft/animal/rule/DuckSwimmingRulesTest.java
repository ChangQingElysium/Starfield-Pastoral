package com.stardew.craft.animal.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuckSwimmingRulesTest {
    @Test
    void sourceEligibilityRequiresPettingAnd3dTargetStaysOnFarm() {
        assertTrue(DuckSwimmingRules.canSeekWater(
                true, true, true, true, true));
        assertFalse(DuckSwimmingRules.canSeekWater(
                false, true, true, true, true));
        assertFalse(DuckSwimmingRules.canSeekWater(
                true, false, true, true, true));
        assertFalse(DuckSwimmingRules.canSeekWater(
                true, true, false, true, true));
        assertFalse(DuckSwimmingRules.canSeekWater(
                true, true, true, false, true));
        assertFalse(DuckSwimmingRules.canSeekWater(
                true, true, true, true, false));
    }
}

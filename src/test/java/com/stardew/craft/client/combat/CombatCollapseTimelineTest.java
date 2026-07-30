package com.stardew.craft.client.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatCollapseTimelineTest {
    @Test
    void bodyFallsQuicklyAndRemainsProne() {
        assertEquals(0.0F, CombatCollapseTimeline.bodyFallProgress(0, 0.0F));
        assertEquals(0.5F, CombatCollapseTimeline.bodyFallProgress(6, 0.0F));
        assertEquals(1.0F, CombatCollapseTimeline.bodyFallProgress(12, 0.0F));
        assertEquals(1.0F, CombatCollapseTimeline.bodyFallProgress(160, 0.0F));
    }

    @Test
    void initialJitterDecaysDuringFirstSecondAndHalf() {
        assertEquals(1.0F, CombatCollapseTimeline.jitterStrength(0, 0.0F));
        assertEquals(0.5F, CombatCollapseTimeline.jitterStrength(15, 0.0F));
        assertEquals(0.0F, CombatCollapseTimeline.jitterStrength(30, 0.0F));
    }

    @Test
    void redGlowRisesHoldsAndClearsBeforeBlackFade() {
        assertEquals(0.0F, CombatCollapseTimeline.redAlpha(0, 0.0F));
        assertEquals(CombatCollapseTimeline.MAX_RED_ALPHA,
            CombatCollapseTimeline.redAlpha(8, 0.0F));
        assertEquals(CombatCollapseTimeline.MAX_RED_ALPHA,
            CombatCollapseTimeline.redAlpha(60, 0.0F));
        assertEquals(0.0F, CombatCollapseTimeline.redAlpha(100, 0.0F));
        assertEquals(0.0F, CombatCollapseTimeline.blackAlpha(100, 0.0F));
    }

    @Test
    void blackArrivesLateAndIsCompleteBeforeEightSecondAck() {
        assertEquals(0.0F, CombatCollapseTimeline.blackAlpha(120, 0.0F));
        assertEquals(0.5F, CombatCollapseTimeline.blackAlpha(135, 0.0F));
        assertEquals(1.0F, CombatCollapseTimeline.blackAlpha(150, 0.0F));
        assertFalse(CombatCollapseTimeline.shouldAcknowledge(159));
        assertTrue(CombatCollapseTimeline.shouldAcknowledge(160));
    }
}

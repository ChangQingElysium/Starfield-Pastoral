package com.stardew.craft.network.overnight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvernightCollapseTimelineTest {
    @Test
    void bodyUsesVanillaFrame293DropWindow() {
        assertEquals(0.0F, OvernightCollapseTimeline.collapseProgress(0, 0.0F));
        assertEquals(0.0F, OvernightCollapseTimeline.collapseProgress(50, 0.0F));
        assertEquals(0.5F, OvernightCollapseTimeline.collapseProgress(52, 0.0F));
        assertEquals(1.0F, OvernightCollapseTimeline.collapseProgress(54, 0.0F));
        assertEquals(1.0F, OvernightCollapseTimeline.collapseProgress(174, 0.0F));
    }

    @Test
    void blackFadeOccupiesFinalVanillaProneFrameAndThenHolds() {
        assertEquals(0.0F, OvernightCollapseTimeline.blackAlpha(134, 0.0F));
        assertEquals(0.5F, OvernightCollapseTimeline.blackAlpha(154, 0.0F));
        assertEquals(1.0F, OvernightCollapseTimeline.blackAlpha(174, 0.0F));
        assertEquals(1.0F, OvernightCollapseTimeline.blackAlpha(200, 0.0F));
    }

    @Test
    void earlySettlementWaitsForCompleteEightPointSevenSecondAnimation() {
        assertFalse(OvernightCollapseTimeline.canOpenSettlement(173, true));
        assertTrue(OvernightCollapseTimeline.canOpenSettlement(174, true));
        assertFalse(OvernightCollapseTimeline.canOpenSettlement(240, false));
    }

    @Test
    void drowsyNodOnlyRunsDuringTheTwoVanillaFrame16Beats() {
        assertEquals(0.0F, OvernightCollapseTimeline.drowsyNodDegrees(0, 0.0F), 0.001F);
        assertEquals(12.0F, OvernightCollapseTimeline.drowsyNodDegrees(10, 0.0F), 0.001F);
        assertEquals(0.0F, OvernightCollapseTimeline.drowsyNodDegrees(25, 0.0F), 0.001F);
        assertEquals(12.0F, OvernightCollapseTimeline.drowsyNodDegrees(40, 0.0F), 0.001F);
        assertEquals(0.0F, OvernightCollapseTimeline.drowsyNodDegrees(54, 0.0F), 0.001F);
    }
}

package com.stardew.craft.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class WizardBuildingBlockEntityRendererTest {
    @Test
    void clockHandsFollowTwelveHourTimeIncludingMinuteProgress() {
        assertArrayEquals(new float[] {180.0F, 0.0F},
                WizardBuildingBlockEntityRenderer.clockHandAnglesForMinutes(6 * 60), 1.0E-4F);
        assertArrayEquals(new float[] {315.0F, 180.0F},
                WizardBuildingBlockEntityRenderer.clockHandAnglesForMinutes(10 * 60 + 30), 1.0E-4F);
        assertArrayEquals(new float[] {0.0F, 0.0F},
                WizardBuildingBlockEntityRenderer.clockHandAnglesForMinutes(12 * 60), 1.0E-4F);
        assertArrayEquals(new float[] {359.5F, 354.0F},
                WizardBuildingBlockEntityRenderer.clockHandAnglesForMinutes(23 * 60 + 59), 1.0E-4F);
    }
}

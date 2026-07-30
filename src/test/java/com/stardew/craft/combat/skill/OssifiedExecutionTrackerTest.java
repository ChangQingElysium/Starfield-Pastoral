package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssifiedExecutionTrackerTest {
    @Test
    void executionCircleEndsAtTheExclusiveEndTick() {
        long startTick = 100L;
        long endTick = startTick + 60L;

        assertFalse(OssifiedExecutionTracker.isExpired(startTick, endTick));
        assertFalse(OssifiedExecutionTracker.isExpired(
                endTick - 1L,
                endTick
        ));
        assertTrue(OssifiedExecutionTracker.isExpired(endTick, endTick));
    }

    @Test
    void pulseUsesTheAuthoredMajorOneHundredPercentDamageContext() {
        SkillContext pulse = OssifiedExecutionTracker.createPulseContext();

        assertEquals("ossified_execution_dot", pulse.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, pulse.getTier());
        assertEquals(1.0F, pulse.getDamageMultiplier());
        assertFalse(pulse.isIgnoreDefense());
        assertFalse(pulse.isGuaranteedCrit());
    }

    @Test
    void circleIsBoundToItsCastDimension() {
        assertTrue(OssifiedExecutionTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(OssifiedExecutionTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }
}

package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssifiedExecutionStateTest {
    @Test
    void executionCircleEndsAtTheExclusiveEndTick() {
        long startTick = 100L;
        long endTick = startTick + 60L;

        assertFalse(OssifiedExecutionState.isExpired(
                startTick,
                endTick
        ));
        assertFalse(OssifiedExecutionState.isExpired(
                endTick - 1L,
                endTick
        ));
        assertTrue(OssifiedExecutionState.isExpired(
                endTick,
                endTick
        ));
    }

    @Test
    void pulseUsesTheAuthoredMajorOneHundredPercentDamageContext() {
        SkillContext pulse = OssifiedExecutionState
                .createPulseContext();

        assertEquals("ossified_execution_dot", pulse.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, pulse.getTier());
        assertEquals(1.0F, pulse.getDamageMultiplier());
        assertFalse(pulse.isIgnoreDefense());
        assertFalse(pulse.isGuaranteedCrit());
    }

    @Test
    void activeWindowIsBoundToTheReleaseDimension() {
        OssifiedExecutionState state =
                new OssifiedExecutionState(
                        Vec3.ZERO,
                        4.0F,
                        Level.OVERWORLD,
                        100L,
                        60
                );

        assertTrue(state.isActive(100L, Level.OVERWORLD));
        assertTrue(state.isActive(159L, Level.OVERWORLD));
        assertFalse(state.isActive(160L, Level.OVERWORLD));
        assertFalse(state.isActive(100L, Level.NETHER));
    }

    @Test
    void pulseScheduleKeepsTheFirstEligibleTickAndTwentyTickCadence() {
        assertTrue(OssifiedExecutionState.shouldPulse(101L, 100L));
        assertFalse(OssifiedExecutionState.shouldPulse(119L, 120L));
        assertTrue(OssifiedExecutionState.shouldPulse(120L, 120L));
    }
}

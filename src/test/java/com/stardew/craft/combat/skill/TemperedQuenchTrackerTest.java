package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemperedQuenchTrackerTest {
    @Test
    void blastTriggersAtTheInclusiveDelayBoundary() {
        long triggerTick = 120L;

        assertFalse(TemperedQuenchTracker.shouldTrigger(
                triggerTick - 1L,
                triggerTick
        ));
        assertTrue(TemperedQuenchTracker.shouldTrigger(
                triggerTick,
                triggerTick
        ));
    }

    @Test
    void blastUsesTheAuthoredMinorFortyFivePercentDamageContext() {
        SkillContext blast = TemperedQuenchTracker.createBlastContext();

        assertEquals("tempered_quench_blast", blast.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, blast.getTier());
        assertEquals(0.45F, blast.getDamageMultiplier());
        assertFalse(blast.isIgnoreDefense());
        assertFalse(blast.isGuaranteedCrit());
    }

    @Test
    void pendingBlastIsBoundToItsCastDimension() {
        assertTrue(TemperedQuenchTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(TemperedQuenchTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }
}

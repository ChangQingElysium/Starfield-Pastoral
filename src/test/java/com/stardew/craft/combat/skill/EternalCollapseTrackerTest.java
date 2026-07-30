package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EternalCollapseTrackerTest {
    @Test
    void baseAndFinalStrikesPreserveTheAuthoredDamageContext() {
        SkillContext base = EternalCollapseTracker.createStrikeContext(
                "eternal_collapse",
                0.8F,
                0.2F
        );
        SkillContext finisher = EternalCollapseTracker.createStrikeContext(
                "eternal_collapse",
                3.0F,
                0.2F
        );

        assertEquals("eternal_collapse", base.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, base.getTier());
        assertEquals(0.8F, base.getDamageMultiplier());
        assertEquals(0.2F, base.getCritChanceBonus());
        assertFalse(base.isIgnoreDefense());
        assertFalse(base.isGuaranteedCrit());

        assertEquals(SkillContext.SkillTier.MAJOR, finisher.getTier());
        assertEquals(3.0F, finisher.getDamageMultiplier());
        assertEquals(0.2F, finisher.getCritChanceBonus());
        assertEquals(5, EternalCollapseTracker.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(0.10D, EternalCollapseTracker.PULL_STRENGTH);
    }

    @Test
    void adaptiveSchedulePreservesTheSeventyTickStrikeCadence() {
        assertEquals(11L, EternalCollapseTracker.strikeInterval(70L, 6));
        assertEquals(7L, EternalCollapseTracker.strikeInterval(70L, 10));
        assertEquals(4L, EternalCollapseTracker.strikeInterval(3L, 1));
        assertEquals(
                4,
                EternalCollapseTracker.MINIMUM_STRIKE_INTERVAL_TICKS
        );

        assertFalse(EternalCollapseTracker.shouldStrike(109L, 110L, 6));
        assertTrue(EternalCollapseTracker.shouldStrike(110L, 110L, 6));
        assertFalse(EternalCollapseTracker.shouldStrike(110L, 110L, 0));
    }

    @Test
    void fieldLifecycleRejectsDeathAndDimensionChanges() {
        assertTrue(EternalCollapseTracker.isValidContext(true, true));
        assertFalse(EternalCollapseTracker.isValidContext(false, true));
        assertFalse(EternalCollapseTracker.isValidContext(true, false));
        assertTrue(EternalCollapseTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(EternalCollapseTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }
}

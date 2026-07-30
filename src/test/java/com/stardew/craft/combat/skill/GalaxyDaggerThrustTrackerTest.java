package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalaxyDaggerThrustTrackerTest {
    @Test
    void eachStrikePreservesTheAuthoredCriticalDamageContract() {
        SkillContext context =
                GalaxyDaggerThrustTracker.createStrikeContext(
                        "galaxy_dagger_starstab",
                        0.5F
                );

        assertEquals("galaxy_dagger_starstab", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(0.5F, context.getDamageMultiplier());
        assertTrue(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());
        assertEquals(3, GalaxyDaggerThrustTracker.STRIKE_COUNT);
        assertEquals(2, GalaxyDaggerThrustTracker.STRIKE_INTERVAL_TICKS);
        assertEquals(60, GalaxyDaggerThrustTracker.MARK_DURATION_TICKS);
        assertEquals(4, GalaxyDaggerThrustTracker.STRIKE_ANIMATION_TICKS);
        assertEquals(
                5,
                GalaxyDaggerThrustTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(3.5D, GalaxyDaggerThrustTracker.RETARGET_RANGE);
    }

    @Test
    void strikeScheduleHonorsItsConfiguredInterval() {
        assertEquals(
                102L,
                GalaxyDaggerThrustTracker.nextStrikeTick(100L, 2)
        );
        assertEquals(
                101L,
                GalaxyDaggerThrustTracker.nextStrikeTick(100L, 0)
        );
        assertEquals(
                GalaxyDaggerThrustTracker.TickDecision.WAIT,
                GalaxyDaggerThrustTracker.tickDecision(
                        102L,
                        101L,
                        true,
                        true
                )
        );
        assertEquals(
                GalaxyDaggerThrustTracker.TickDecision.STRIKE,
                GalaxyDaggerThrustTracker.tickDecision(
                        102L,
                        102L,
                        true,
                        true
                )
        );
    }

    @Test
    void shortLivedComboCancelsWhenCasterContextChanges() {
        assertEquals(
                GalaxyDaggerThrustTracker.TickDecision.CANCEL,
                GalaxyDaggerThrustTracker.tickDecision(
                        102L,
                        101L,
                        false,
                        true
                )
        );
        assertEquals(
                GalaxyDaggerThrustTracker.TickDecision.CANCEL,
                GalaxyDaggerThrustTracker.tickDecision(
                        102L,
                        101L,
                        true,
                        false
                )
        );
        assertTrue(GalaxyDaggerThrustTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(GalaxyDaggerThrustTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void onlyLivePickableNonCasterTargetsCanRemainLocked() {
        assertTrue(GalaxyDaggerThrustTracker.canReuseStoredTarget(
                false,
                true,
                true
        ));
        assertFalse(GalaxyDaggerThrustTracker.canReuseStoredTarget(
                true,
                true,
                true
        ));
        assertFalse(GalaxyDaggerThrustTracker.canReuseStoredTarget(
                false,
                false,
                true
        ));
        assertFalse(GalaxyDaggerThrustTracker.canReuseStoredTarget(
                false,
                true,
                false
        ));
    }
}

package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfinityDaggerThrustTrackerTest {
    @Test
    void eachStrikePreservesTheAuthoredCriticalDamageContract() {
        SkillContext context =
                InfinityDaggerThrustTracker.createStrikeContext(
                        "infinity_dagger_singularity_stab",
                        0.22F
                );

        assertEquals(
                "infinity_dagger_singularity_stab",
                context.getSkillId()
        );
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(0.22F, context.getDamageMultiplier());
        assertTrue(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());
        assertEquals(4, InfinityDaggerThrustTracker.STRIKE_COUNT);
        assertEquals(2, InfinityDaggerThrustTracker.STRIKE_INTERVAL_TICKS);
        assertEquals(60, InfinityDaggerThrustTracker.MARK_DURATION_TICKS);
        assertEquals(
                4,
                InfinityDaggerThrustTracker.STRIKE_ANIMATION_TICKS
        );
        assertEquals(
                5,
                InfinityDaggerThrustTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(3.5D, InfinityDaggerThrustTracker.RETARGET_RANGE);
    }

    @Test
    void strikeScheduleHonorsItsConfiguredInterval() {
        assertEquals(
                202L,
                InfinityDaggerThrustTracker.nextStrikeTick(200L, 2)
        );
        assertEquals(
                201L,
                InfinityDaggerThrustTracker.nextStrikeTick(200L, 0)
        );
        assertEquals(
                InfinityDaggerThrustTracker.TickDecision.WAIT,
                InfinityDaggerThrustTracker.tickDecision(
                        202L,
                        201L,
                        true,
                        true
                )
        );
        assertEquals(
                InfinityDaggerThrustTracker.TickDecision.STRIKE,
                InfinityDaggerThrustTracker.tickDecision(
                        202L,
                        202L,
                        true,
                        true
                )
        );
    }

    @Test
    void shortLivedComboCancelsWhenCasterContextChanges() {
        assertEquals(
                InfinityDaggerThrustTracker.TickDecision.CANCEL,
                InfinityDaggerThrustTracker.tickDecision(
                        202L,
                        201L,
                        false,
                        true
                )
        );
        assertEquals(
                InfinityDaggerThrustTracker.TickDecision.CANCEL,
                InfinityDaggerThrustTracker.tickDecision(
                        202L,
                        201L,
                        true,
                        false
                )
        );
        assertTrue(InfinityDaggerThrustTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(InfinityDaggerThrustTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void onlyLivePickableNonCasterTargetsCanRemainLocked() {
        assertTrue(InfinityDaggerThrustTracker.canReuseStoredTarget(
                false,
                true,
                true
        ));
        assertFalse(InfinityDaggerThrustTracker.canReuseStoredTarget(
                true,
                true,
                true
        ));
        assertFalse(InfinityDaggerThrustTracker.canReuseStoredTarget(
                false,
                false,
                true
        ));
        assertFalse(InfinityDaggerThrustTracker.canReuseStoredTarget(
                false,
                true,
                false
        ));
    }
}

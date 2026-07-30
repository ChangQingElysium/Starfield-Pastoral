package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObsidianCrackTrackerTest {
    @Test
    void explosionUsesTheAuthoredMajorDamageAndCrowdControl() {
        SkillContext context =
                ObsidianCrackTracker.createExplosionContext("obsidian_crack");

        assertEquals("obsidian_crack", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, context.getTier());
        assertEquals(1.6F, context.getDamageMultiplier());
        assertEquals(5, ObsidianCrackTracker.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(3.0D, ObsidianCrackTracker.PULL_RADIUS);
        assertEquals(40, ObsidianCrackTracker.SLOW_DURATION_TICKS);
        assertEquals(0, ObsidianCrackTracker.SLOW_AMPLIFIER);
        assertEquals(20, ObsidianCrackTracker.EFFECT_DURATION_TICKS);
    }

    @Test
    void explosionStartsOnItsAuthoredEighthTick() {
        assertTrue(ObsidianCrackTracker.isWaitingForExplosion(107L, 108L));
        assertFalse(ObsidianCrackTracker.isWaitingForExplosion(108L, 108L));
    }

    @Test
    void pathDistanceAndNearestPointUseTheFiniteHorizontalSegment() {
        Vec3 start = new Vec3(-3.0D, 0.0D, 0.0D);
        Vec3 end = new Vec3(3.0D, 0.0D, 0.0D);

        assertEquals(
                4.0D,
                ObsidianCrackTracker.distanceToSegmentSqr2D(
                        new Vec3(0.0D, 12.0D, 2.0D),
                        start,
                        end
                )
        );
        assertEquals(
                8.0D,
                ObsidianCrackTracker.distanceToSegmentSqr2D(
                        new Vec3(5.0D, -4.0D, 2.0D),
                        start,
                        end
                )
        );
        Vec3 nearest = ObsidianCrackTracker.nearestPointOnSegment2D(
                new Vec3(5.0D, 9.0D, 2.0D),
                start,
                end
        );
        assertEquals(3.0D, nearest.x);
        assertEquals(0.0D, nearest.z);
    }

    @Test
    void pendingCrackIsBoundToItsCastDimension() {
        assertTrue(ObsidianCrackTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(ObsidianCrackTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }
}

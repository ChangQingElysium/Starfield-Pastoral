package com.stardew.craft.combat.skill;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FemurSlamTrackerTest {
    @Test
    void authoredArcControlAndKnockbackValuesRemainStable() {
        assertEquals(20, FemurSlamTracker.CHARGE_TICKS);
        assertEquals(5.0D, FemurSlamTracker.RANGE);
        assertEquals(0.5D, FemurSlamTracker.MIN_DOT);
        assertEquals(40, FemurSlamTracker.SLOW_TICKS);
        assertEquals(0, FemurSlamTracker.SLOW_AMPLIFIER);
        assertEquals(4, FemurSlamTracker.STAGGER_TICKS);
        assertEquals(0, FemurSlamTracker.STAGGER_AMPLIFIER);
        assertEquals(0.7F, FemurSlamTracker.KNOCKBACK_MULTI);
        assertEquals(1.1F, FemurSlamTracker.KNOCKBACK_SINGLE);
        assertEquals(28, FemurSlamTracker.QUAKE_TREMOR_MAX);
        assertEquals(
                5,
                FemurSlamTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    @Test
    void chargeReleaseAndFireBoundaryPreserveOriginalTiming() {
        assertEquals(
                FemurSlamTracker.TickDecision.WAIT,
                FemurSlamTracker.tickDecision(
                        120L,
                        119L,
                        true,
                        true,
                        true
                )
        );
        assertEquals(
                FemurSlamTracker.TickDecision.CANCEL,
                FemurSlamTracker.tickDecision(
                        120L,
                        119L,
                        true,
                        true,
                        false
                )
        );
        assertEquals(
                FemurSlamTracker.TickDecision.FIRE,
                FemurSlamTracker.tickDecision(
                        120L,
                        120L,
                        true,
                        true,
                        false
                )
        );
    }

    @Test
    void deathAndDimensionChangeCancelBeforeTheSlam() {
        assertEquals(
                FemurSlamTracker.TickDecision.CANCEL,
                FemurSlamTracker.tickDecision(
                        120L,
                        110L,
                        false,
                        true,
                        true
                )
        );
        assertEquals(
                FemurSlamTracker.TickDecision.CANCEL,
                FemurSlamTracker.tickDecision(
                        120L,
                        110L,
                        true,
                        false,
                        true
                )
        );
        assertTrue(FemurSlamTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(FemurSlamTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void hitUsesOriginalMinorDamageContext() {
        SkillContext hit = FemurSlamTracker.createHitContext(
                "femur_slam",
                1.20F
        );

        assertEquals("femur_slam", hit.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, hit.getTier());
        assertEquals(1.20F, hit.getDamageMultiplier());
        assertFalse(hit.isGuaranteedCrit());
        assertFalse(hit.isIgnoreDefense());
    }

    @Test
    void logoutCleanupIsIdempotentWithoutOnlineCaster() {
        assertDoesNotThrow(() ->
                FemurSlamTracker.removePlayer(UUID.randomUUID())
        );
    }
}

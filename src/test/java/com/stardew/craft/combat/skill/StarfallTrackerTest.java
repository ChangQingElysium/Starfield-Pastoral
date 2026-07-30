package com.stardew.craft.combat.skill;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarfallTrackerTest {
    @Test
    void authoredStarfallCadenceAndHitCountRemainStable() {
        assertEquals(10, StarfallTracker.STRIKE_INTERVAL_TICKS);
        assertEquals(3, StarfallTracker.DEFAULT_STRIKES);
        assertEquals(3, StarfallTracker.MAX_EXTRA_HITS);
        assertEquals(4.0D, StarfallTracker.DEFAULT_RADIUS);
        assertEquals(
                0.70F,
                StarfallTracker.DEFAULT_DAMAGE_MULTIPLIER
        );
        assertEquals(5, StarfallTracker.HIT_CONTEXT_LIFETIME_TICKS);

        assertFalse(StarfallTracker.shouldStrike(109L, 110L));
        assertTrue(StarfallTracker.shouldStrike(110L, 110L));
        assertEquals(120L, StarfallTracker.nextStrikeTick(110L));
        assertEquals(1, StarfallTracker.hitsPerTarget(0));
        assertEquals(4, StarfallTracker.hitsPerTarget(3));
    }

    @Test
    void eachDelayedHitUsesTheOriginalMajorDamageContext() {
        SkillContext strike = StarfallTracker.createStrikeContext(
                "galaxy_judgement",
                0.70F
        );

        assertEquals("galaxy_judgement", strike.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, strike.getTier());
        assertEquals(0.70F, strike.getDamageMultiplier());
        assertFalse(strike.isGuaranteedCrit());
        assertFalse(strike.isIgnoreDefense());
    }

    @Test
    void delayedStateRequiresTheCasterAndStartingDimension() {
        assertTrue(StarfallTracker.isValidContext(true, true));
        assertFalse(StarfallTracker.isValidContext(false, true));
        assertFalse(StarfallTracker.isValidContext(true, false));
        assertTrue(StarfallTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(StarfallTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void logoutCleanupIsIdempotentWithoutAnOnlineCaster() {
        assertDoesNotThrow(() ->
                StarfallTracker.removePlayer(UUID.randomUUID())
        );
    }
}

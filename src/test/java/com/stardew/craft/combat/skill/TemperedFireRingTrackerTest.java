package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemperedFireRingTrackerTest {
    @Test
    void fireRingExpandsFromMinimumRadiusToAuthoredMaximum() {
        assertEquals(
                0.25F,
                TemperedFireRingTracker.radiusAt(0L, 10, 2.5F)
        );
        assertEquals(
                1.25F,
                TemperedFireRingTracker.radiusAt(5L, 10, 2.5F)
        );
        assertEquals(
                2.5F,
                TemperedFireRingTracker.radiusAt(10L, 10, 2.5F)
        );
        assertEquals(0.6F, TemperedFireRingTracker.DAMAGE_MULTIPLIER);
        assertEquals(5, TemperedFireRingTracker.HIT_CONTEXT_LIFETIME_TICKS);

        SkillContext damageContext =
                TemperedFireRingTracker.createDamageContext(
                        TemperedFireRingTracker.DAMAGE_MULTIPLIER
                );
        assertEquals("tempered_billet", damageContext.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, damageContext.getTier());
        assertEquals(0.6F, damageContext.getDamageMultiplier());
        assertTrue(TemperedFireRingTracker.isWithinRadius(1.0, 1.0F));
        assertFalse(TemperedFireRingTracker.isWithinRadius(1.01, 1.0F));
    }

    @Test
    void delayedStateIsInclusiveButCannotCrossDimensions() {
        assertTrue(TemperedFireRingTracker.isCastActive(165L, 165L, true));
        assertFalse(TemperedFireRingTracker.isCastActive(165L, 166L, true));
        assertFalse(TemperedFireRingTracker.isCastActive(165L, 120L, false));
        assertTrue(TemperedFireRingTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(TemperedFireRingTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }
}

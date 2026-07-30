package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplarVowTrackerTest {
    @Test
    void counterAndExpirySlashKeepTheirDistinctDamageContracts() {
        SkillContext counter = TemplarVowTracker.createStrikeContext(
                TemplarVowTracker.COUNTER_DAMAGE_MULTIPLIER
        );
        SkillContext expiry = TemplarVowTracker.createStrikeContext(
                TemplarVowTracker.EXPIRE_SLASH_DAMAGE_MULTIPLIER
        );

        assertEquals(SkillContext.SkillTier.MINOR, counter.getTier());
        assertEquals(1.10F, counter.getDamageMultiplier());
        assertEquals(0.80F, expiry.getDamageMultiplier());
        assertEquals(5, TemplarVowTracker.HIT_CONTEXT_LIFETIME_TICKS);
    }

    @Test
    void authoredWindowIncludesItsEndTick() {
        assertTrue(TemplarVowTracker.isWithinActiveWindow(140L, 140L));
        assertFalse(TemplarVowTracker.isWithinActiveWindow(141L, 140L));
    }
}

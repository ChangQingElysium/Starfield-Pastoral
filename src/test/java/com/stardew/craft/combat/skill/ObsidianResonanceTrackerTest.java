package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObsidianResonanceTrackerTest {
    @Test
    void bonusHitMirrorsTheOpeningHitsCritResult() {
        SkillContext normal = ObsidianResonanceTracker.createBonusContext(false);
        SkillContext critical = ObsidianResonanceTracker.createBonusContext(true);

        assertEquals(SkillContext.SkillTier.MINOR, normal.getTier());
        assertEquals(0.70F, normal.getDamageMultiplier());
        assertFalse(normal.isGuaranteedCrit());
        assertTrue(critical.isGuaranteedCrit());
        assertEquals(5, ObsidianResonanceTracker.HIT_CONTEXT_LIFETIME_TICKS);
    }
}

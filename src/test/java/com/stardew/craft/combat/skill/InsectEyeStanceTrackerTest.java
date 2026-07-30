package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsectEyeStanceTrackerTest {
    @Test
    void firstAndLaterHitsPreserveTheAuthoredStanceContexts() {
        SkillContext first = InsectEyeStanceTracker.createSkillContext(
                "insect_eye_stance",
                true
        );
        SkillContext later = InsectEyeStanceTracker.createSkillContext(
                "insect_eye_stance",
                false
        );

        assertEquals(SkillContext.SkillTier.MINOR, first.getTier());
        assertEquals(1.05F, first.getDamageMultiplier());
        assertTrue(first.isGuaranteedCrit());
        assertEquals(1.05F, later.getDamageMultiplier());
        assertFalse(later.isGuaranteedCrit());
    }

    @Test
    void activeWindowIsInclusiveButCannotCrossDimensions() {
        assertTrue(InsectEyeStanceTracker.shouldRemainActive(130L, 130L, true));
        assertFalse(InsectEyeStanceTracker.shouldRemainActive(130L, 131L, true));
        assertFalse(InsectEyeStanceTracker.shouldRemainActive(130L, 120L, false));
    }
}

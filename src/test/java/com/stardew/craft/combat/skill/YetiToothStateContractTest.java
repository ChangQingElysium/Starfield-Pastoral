package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YetiToothStateContractTest {
    @Test
    void markAndFreezeExpireAtTheirAuthoredExclusiveEndTick() {
        assertTrue(YetiToothMarkTracker.isWithinMarkWindow(59L, 60L));
        assertFalse(YetiToothMarkTracker.isWithinMarkWindow(60L, 60L));
        assertTrue(YetiFreezeTracker.isWithinFreezeWindow(39L, 40L));
        assertFalse(YetiFreezeTracker.isWithinFreezeWindow(40L, 40L));
    }

    @Test
    void theApplyingHitCannotImmediatelyConsumeItsOwnMark() {
        assertFalse(YetiToothMarkTracker.isEligibleFollowupSkill(
                YetiToothMarkTracker.SKILL_ID
        ));
        assertTrue(YetiToothMarkTracker.isEligibleFollowupSkill("normal"));
        assertTrue(YetiToothMarkTracker.isEligibleFollowupSkill(null));
    }

}

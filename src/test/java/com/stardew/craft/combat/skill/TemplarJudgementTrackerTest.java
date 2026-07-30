package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplarJudgementTrackerTest {
    @Test
    void authoredWindowUsesAnExclusiveEndTick() {
        assertTrue(TemplarJudgementTracker.isWithinActiveWindow(139L, 140L));
        assertFalse(TemplarJudgementTracker.isWithinActiveWindow(140L, 140L));
    }

    @Test
    void authoredTransferCapAndSettlementRemainStable() {
        assertEquals(0.35F, TemplarJudgementTracker.SHARE_RATIO);
        assertEquals(
                0.25F,
                TemplarJudgementTracker.MAX_HEALTH_DAMAGE_CAP_RATIO
        );
        assertEquals(
                1.6F,
                TemplarJudgementTracker.SETTLEMENT_DAMAGE_MULTIPLIER
        );
        assertEquals(5, TemplarJudgementTracker.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(
                35.0F,
                TemplarJudgementTracker.cappedSharedDamage(100.0F, 200.0F)
        );
        assertEquals(
                50.0F,
                TemplarJudgementTracker.cappedSharedDamage(200.0F, 200.0F)
        );
    }
}

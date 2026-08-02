package com.stardew.craft.combat.skill.handler;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonBreathJudgementExecutionStateTest {
    @Test
    void refundCountsEachPositiveAppliedTargetOnceAndCapsAtFive() {
        DragonBreathJudgementExecutionState state =
                new DragonBreathJudgementExecutionState();
        UUID first = UUID.randomUUID();

        assertTrue(state.recordAppliedTarget(first));
        assertFalse(state.recordAppliedTarget(first));
        for (int index = 0; index < 6; index++) {
            assertTrue(state.recordAppliedTarget(UUID.randomUUID()));
        }

        assertEquals(5, state.settleRefund());
        assertEquals(0, state.settleRefund());
        assertFalse(state.recordAppliedTarget(UUID.randomUUID()));
    }

    @Test
    void aCastWithNoPositiveAppliedDamageRefundsNothing() {
        DragonBreathJudgementExecutionState state =
                new DragonBreathJudgementExecutionState();

        assertFalse(state.recordAppliedTarget(null));
        assertEquals(0, state.settleRefund());
    }
}

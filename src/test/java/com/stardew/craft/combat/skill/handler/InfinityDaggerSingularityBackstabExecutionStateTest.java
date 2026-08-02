package com.stardew.craft.combat.skill.handler;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfinityDaggerSingularityBackstabExecutionStateTest {
    @Test
    void controlRequiresAnExactAppliedHitOnTheOwnedTarget() {
        UUID targetId = UUID.randomUUID();
        InfinityDaggerSingularityBackstabExecutionState state =
                new InfinityDaggerSingularityBackstabExecutionState(targetId);

        assertFalse(state.recordAppliedHit(UUID.randomUUID()));
        assertFalse(state.settleControl());
    }

    @Test
    void eitherAuthoredHitCanArmOneControlSettlement() {
        UUID targetId = UUID.randomUUID();
        InfinityDaggerSingularityBackstabExecutionState state =
                new InfinityDaggerSingularityBackstabExecutionState(targetId);

        assertTrue(state.recordAppliedHit(targetId));
        assertFalse(state.recordAppliedHit(targetId));
        assertTrue(state.settleControl());
        assertFalse(state.settleControl());
    }

    @Test
    void settlingWithoutDamagePermanentlyClosesTheCast() {
        UUID targetId = UUID.randomUUID();
        InfinityDaggerSingularityBackstabExecutionState state =
                new InfinityDaggerSingularityBackstabExecutionState(targetId);

        assertFalse(state.settleControl());
        assertFalse(state.recordAppliedHit(targetId));
    }
}

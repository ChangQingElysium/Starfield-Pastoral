package com.stardew.craft.combat.skill.handler;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesperatePlunderExecutionStateTest {
    @Test
    void exactAppliedKillSettlesAsThisCastsKill() {
        UUID targetId = UUID.randomUUID();
        DesperatePlunderExecutionState state =
                new DesperatePlunderExecutionState(targetId);

        assertTrue(state.recordAppliedHit(targetId, true));
        assertTrue(state.settleKilledByThisHit());
        assertFalse(state.settleKilledByThisHit());
    }

    @Test
    void positiveNonKillSettlesToFuryOutcome() {
        UUID targetId = UUID.randomUUID();
        DesperatePlunderExecutionState state =
                new DesperatePlunderExecutionState(targetId);

        assertTrue(state.recordAppliedHit(targetId, false));
        assertFalse(state.settleKilledByThisHit());
    }

    @Test
    void rejectedZeroOrMissingHitCannotClaimKill() {
        DesperatePlunderExecutionState missingTarget =
                new DesperatePlunderExecutionState(null);
        assertFalse(missingTarget.recordAppliedHit(UUID.randomUUID(), true));
        assertFalse(missingTarget.settleKilledByThisHit());

        UUID targetId = UUID.randomUUID();
        DesperatePlunderExecutionState noAppliedHit =
                new DesperatePlunderExecutionState(targetId);
        assertFalse(noAppliedHit.settleKilledByThisHit());
        assertFalse(noAppliedHit.recordAppliedHit(targetId, true));
    }

    @Test
    void unrelatedTargetCannotClaimKill() {
        UUID targetId = UUID.randomUUID();
        DesperatePlunderExecutionState state =
                new DesperatePlunderExecutionState(targetId);

        assertFalse(state.recordAppliedHit(UUID.randomUUID(), true));
        assertFalse(state.settleKilledByThisHit());
    }
}

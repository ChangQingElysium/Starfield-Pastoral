package com.stardew.craft.combat.skill.handler;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartrailRiftExecutionStateTest {
    @Test
    void firstEligibleAppliedTargetClaimsRewardsExactlyOnce() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        StartrailRiftExecutionState state =
                new StartrailRiftExecutionState(List.of(first, second));

        assertTrue(state.claimAppliedHitRewards(first));
        assertFalse(state.claimAppliedHitRewards(first));
        assertFalse(state.claimAppliedHitRewards(second));
    }

    @Test
    void ineligibleOrMissingTargetDoesNotConsumeTheClaim() {
        UUID eligible = UUID.randomUUID();
        StartrailRiftExecutionState state =
                new StartrailRiftExecutionState(List.of(eligible));

        assertFalse(state.claimAppliedHitRewards(UUID.randomUUID()));
        assertFalse(state.claimAppliedHitRewards(null));
        assertTrue(state.claimAppliedHitRewards(eligible));
    }

    @Test
    void candidateTargetsAloneNeverClaimRewards() {
        StartrailRiftExecutionState state =
                new StartrailRiftExecutionState(List.of(UUID.randomUUID()));

        assertFalse(state.claimAppliedHitRewards(UUID.randomUUID()));
    }
}

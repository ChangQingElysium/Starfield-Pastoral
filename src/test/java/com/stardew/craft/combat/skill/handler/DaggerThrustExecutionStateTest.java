package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaggerThrustExecutionStateTest {
    @Test
    void galaxyComboPreservesAuthoredStrikeContract() {
        SkillContext context =
                GalaxyDaggerThrustExecutionState.createStrikeContext(
                        "galaxy_dagger_starstab",
                        0.5F
                );

        assertEquals("galaxy_dagger_starstab", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(0.5F, context.getDamageMultiplier());
        assertTrue(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());
        assertEquals(3, GalaxyDaggerStarstabSkillHandler.STRIKE_COUNT);
        assertEquals(
                2,
                GalaxyDaggerStarstabSkillHandler.STRIKE_INTERVAL_TICKS
        );
        assertEquals(
                60,
                GalaxyDaggerStarstabSkillHandler.MARK_DURATION_TICKS
        );
        assertEquals(
                4,
                GalaxyDaggerStarstabSkillHandler.STRIKE_ANIMATION_TICKS
        );
        assertEquals(
                5,
                GalaxyDaggerStarstabSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
                3.5D,
                GalaxyDaggerStarstabSkillHandler.RETARGET_RANGE
        );
        assertEquals(
                102L,
                GalaxyDaggerThrustExecutionState.nextStrikeTick(100L)
        );
    }

    @Test
    void infinityComboPreservesAuthoredStrikeContract() {
        SkillContext context =
                InfinityDaggerThrustExecutionState.createStrikeContext(
                        "infinity_dagger_singularity_stab",
                        0.22F
                );

        assertEquals(
                "infinity_dagger_singularity_stab",
                context.getSkillId()
        );
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(0.22F, context.getDamageMultiplier());
        assertTrue(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());
        assertEquals(
                4,
                InfinityDaggerSingularityStabSkillHandler.STRIKE_COUNT
        );
        assertEquals(
                2,
                InfinityDaggerSingularityStabSkillHandler
                        .STRIKE_INTERVAL_TICKS
        );
        assertEquals(
                60,
                InfinityDaggerSingularityStabSkillHandler
                        .MARK_DURATION_TICKS
        );
        assertEquals(
                4,
                InfinityDaggerSingularityStabSkillHandler
                        .STRIKE_ANIMATION_TICKS
        );
        assertEquals(
                5,
                InfinityDaggerSingularityStabSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
                3.5D,
                InfinityDaggerSingularityStabSkillHandler.RETARGET_RANGE
        );
        assertEquals(
                202L,
                InfinityDaggerThrustExecutionState.nextStrikeTick(200L)
        );
    }

    @Test
    void galaxyFinalMarkConsumesOnlyTheLiveArmedTarget() {
        UUID targetId = UUID.randomUUID();
        GalaxyDaggerThrustExecutionState state =
                new GalaxyDaggerThrustExecutionState(0L, targetId);

        state.beginStrike(targetId, false);
        assertFalse(state.consumeFinalStrikeCandidate(targetId, true));

        state.beginStrike(targetId, true);
        assertFalse(state.consumeFinalStrikeCandidate(
                UUID.randomUUID(),
                true
        ));
        assertTrue(state.consumeFinalStrikeCandidate(targetId, true));
        assertFalse(state.consumeFinalStrikeCandidate(targetId, true));

        state.beginStrike(targetId, true);
        assertFalse(state.consumeFinalStrikeCandidate(targetId, false));
        state.clearFinalStrikeCandidate();
        assertFalse(state.consumeFinalStrikeCandidate(targetId, true));
    }

    @Test
    void infinityFinalMarkConsumesOnlyTheLiveArmedTarget() {
        UUID targetId = UUID.randomUUID();
        InfinityDaggerThrustExecutionState state =
                new InfinityDaggerThrustExecutionState(0L, targetId);

        state.beginStrike(targetId, false);
        assertFalse(state.consumeFinalStrikeCandidate(targetId, true));

        state.beginStrike(targetId, true);
        assertFalse(state.consumeFinalStrikeCandidate(
                UUID.randomUUID(),
                true
        ));
        assertTrue(state.consumeFinalStrikeCandidate(targetId, true));
        assertFalse(state.consumeFinalStrikeCandidate(targetId, true));

        state.beginStrike(targetId, true);
        assertFalse(state.consumeFinalStrikeCandidate(targetId, false));
        state.clearFinalStrikeCandidate();
        assertFalse(state.consumeFinalStrikeCandidate(targetId, true));
    }

    @Test
    void onlyLivePickableNonCasterTargetsRemainLocked() {
        assertTrue(GalaxyDaggerThrustExecutionState.canReuseStoredTarget(
                false,
                true,
                true
        ));
        assertFalse(GalaxyDaggerThrustExecutionState.canReuseStoredTarget(
                true,
                true,
                true
        ));
        assertFalse(GalaxyDaggerThrustExecutionState.canReuseStoredTarget(
                false,
                false,
                true
        ));
        assertFalse(GalaxyDaggerThrustExecutionState.canReuseStoredTarget(
                false,
                true,
                false
        ));

        assertTrue(InfinityDaggerThrustExecutionState.canReuseStoredTarget(
                false,
                true,
                true
        ));
        assertFalse(InfinityDaggerThrustExecutionState.canReuseStoredTarget(
                true,
                true,
                true
        ));
        assertFalse(InfinityDaggerThrustExecutionState.canReuseStoredTarget(
                false,
                false,
                true
        ));
        assertFalse(InfinityDaggerThrustExecutionState.canReuseStoredTarget(
                false,
                true,
                false
        ));
    }
}

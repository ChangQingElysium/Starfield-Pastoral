package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.RiftPathDamageTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingularityEvolveExecutionStateTest {
    @Test
    void positiveAppliedHitRewardCanBeClaimedOnlyOnce() {
        SingularityEvolveRewardState rewardState =
                new SingularityEvolveRewardState();

        assertTrue(rewardState.claim());
        assertFalse(rewardState.claim());
    }

    @Test
    void runtimeStatePreservesAuthoredTimingAndDamageContexts() {
        assertTrue(
                SingularityEvolveExecutionState.isWithinPullWindow(
                        119L,
                        120L
                )
        );
        assertFalse(
                SingularityEvolveExecutionState.isWithinPullWindow(
                        120L,
                        120L
                )
        );
        assertTrue(
                SingularityEvolveExecutionState.shouldProcessTick(
                        119L,
                        118L
                )
        );
        assertFalse(
                SingularityEvolveExecutionState.shouldProcessTick(
                        119L,
                        119L
                )
        );
        assertTrue(
                SingularityEvolveExecutionState.isValidContext(
                        true,
                        true
                )
        );
        assertFalse(
                SingularityEvolveExecutionState.isValidContext(
                        true,
                        false
                )
        );

        SkillContext context = SingularityEvolveExecutionState
                .createDamageContext("singularity_evolve", 1.6F);
        assertEquals("singularity_evolve", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.6F, context.getDamageMultiplier());
        assertFalse(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());
    }

    @Test
    void runtimeConstantsRemainAuthored() {
        assertEquals(20, SingularityEvolveSkillHandler.ACTIVE_DURATION_TICKS);
        assertEquals(4.0D, SingularityEvolveSkillHandler.EFFECT_RADIUS);
        assertEquals(
                1.6F,
                SingularityEvolveSkillHandler.EXPLOSION_DAMAGE_MULTIPLIER
        );
        assertEquals(
                1.2F,
                SingularityEvolveSkillHandler.SLASH_DAMAGE_MULTIPLIER
        );
        assertEquals(5.0D, SingularityEvolveSkillHandler.DASH_DISTANCE);
        assertEquals(5, SingularityEvolveSkillHandler.DASH_DURATION_TICKS);
        assertEquals(0.15D, SingularityEvolveSkillHandler.PULL_STRENGTH);
        assertEquals(
                0.9D,
                SingularityEvolveSkillHandler.SLASH_PATH_HALF_WIDTH
        );
        assertEquals(3.0F, SingularityEvolveSkillHandler.RIFT_LENGTH);
        assertEquals(40, SingularityEvolveSkillHandler.RIFT_DURATION_TICKS);
        assertEquals(
                5,
                SingularityEvolveSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    @Test
    void detachedDashAndRiftHaveExactPublicOwnershipHandles()
            throws ReflectiveOperationException {
        Method start = RiftPathDamageTracker.class.getDeclaredMethod(
                "startExact",
                ServerPlayer.class,
                Vec3.class,
                float.class,
                float.class,
                int.class,
                String.class,
                WeaponDamageSnapshot.class
        );
        Method cancel = RiftPathDamageTracker.class.getDeclaredMethod(
                "cancel",
                ServerPlayer.class,
                RiftPathDamageTracker.Handle.class
        );
        assertTrue(Modifier.isPublic(start.getModifiers()));
        assertTrue(Modifier.isStatic(start.getModifiers()));
        assertEquals(RiftPathDamageTracker.Handle.class, start.getReturnType());
        assertTrue(Modifier.isPublic(cancel.getModifiers()));
        assertTrue(Modifier.isStatic(cancel.getModifiers()));
        assertEquals(boolean.class, cancel.getReturnType());
    }

    @Test
    void handlerOwnsBodyWhileDetachedResourcesKeepExactLifetimes()
            throws IOException {
        String handler = normalizedSource(
                sourceRoot().resolve(
                        "handler/SingularityEvolveSkillHandler.java"
                )
        );
        String state = normalizedSource(
                sourceRoot().resolve(
                        "handler/SingularityEvolveExecutionState.java"
                )
        );

        assertTrue(handler.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState( "
                        + "SingularityEvolveExecutionState.class )"
        ));
        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(state.contains(
                "private DashMovementTracker.Handle movementHandle;"
        ));
        assertTrue(state.contains(
                "private RiftPathDamageTracker.Handle riftHandle;"
        ));
        assertTrue(state.contains(
                "movementHandle = dashForward(player, nowTick);"
        ));
        assertTrue(state.contains(
                "riftHandle = RiftPathDamageTracker.startExact("
        ));
        assertTrue(state.contains(
                "RiftPathDamageTracker.cancel(player, riftHandle)"
        ));
        assertTrue(state.contains(
                "DashMovementTracker.cancel(player, movementHandle)"
        ));
        assertTrue(state.contains(
                "detachedResourcesReleased = true;"
        ));
        assertTrue(state.contains(
                "if (!detachedResourcesReleased) { "
                        + "cancelOwnedDetachedResources(player, null); }"
        ));
        assertTrue(handler.contains("instance.registerCommittedEffect("));
        assertFalse(state.contains("registerBeginFailureCleanup("));
    }

    private static String normalizedSource(Path path) throws IOException {
        return Files.readString(path).replaceAll("\\s+", " ");
    }

    private static Path sourceRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException(
                "Cannot locate combat skill sources from "
                        + Path.of("").toAbsolutePath()
        );
    }
}

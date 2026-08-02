package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonBreathThrustLifecycleContractTest {
    @Test
    void beginPreservesTheAuthoredSnapshotAndPresentationOrder()
            throws IOException {
        String handler = source(
                "combat/skill/handler/DragonBreathThrustSkillHandler.java"
        );
        String begin = method(handler, "public void begin(");

        assertOrdered(
                begin,
                "findTargetsAlongPath(",
                "instance.setTargetEntityIds(",
                "WeaponSkillRuntime.commitCooldown(",
                "instance.registerCommittedEffect(",
                "executionState.start(",
                "attackTarget(context, target)",
                "WeaponSkillAnimationDispatcher.sendSkillAnim("
        );
        assertTrue(begin.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertFalse(begin.contains("WeaponSkillAnimationLock"));
        assertFalse(begin.contains("target.addEffect("));
        assertFalse(begin.contains("YetiFreezeTracker"));
    }

    @Test
    void runtimeOwnsTheThrustWindowAndSharedDashOwnsOnlyMovement()
            throws IOException {
        String handler = source(
                "combat/skill/handler/DragonBreathThrustSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/DragonBreathThrustExecutionState.java"
        );
        String resource = source(
                "combat/skill/DragonBreathTracker.java"
        );

        assertFalse(handler.contains("DragonBreathTracker"));
        assertTrue(handler.contains("implements RuntimeWeaponSkillHandler"));
        assertFalse(handler.contains(
                "PostServerRuntimeWeaponSkillHandler"
        ));
        assertFalse(handler.contains("postServerTick("));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(state.contains("DashMovementTracker.Handle"));
        assertTrue(state.contains("DashMovementTracker.cancel(player, movement)"));
        assertFalse(state.contains("static final Map"));
        assertFalse(resource.contains("ACTIVE_THRUSTS"));
        assertFalse(resource.contains("ThrustState"));
    }

    private static void assertOrdered(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = source.indexOf(token);
            assertTrue(current > previous, () -> "Out of order or missing: " + token);
            previous = current;
        }
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        assertTrue(openingBrace > start, "Missing method body " + signature);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("Unclosed method " + signature);
    }

    private static String source(String relativeFile) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativeFile);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}

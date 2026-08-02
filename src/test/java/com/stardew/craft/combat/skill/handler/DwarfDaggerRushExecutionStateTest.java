package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwarfDaggerRushExecutionStateTest {
    @Test
    void activeWindowKeepsTheAuthoredExclusiveEndTick() {
        long endTick = 200L;

        assertTrue(DwarfDaggerRushExecutionState.isWithinActiveWindow(
                100L,
                endTick
        ));
        assertTrue(DwarfDaggerRushExecutionState.isWithinActiveWindow(
                199L,
                endTick
        ));
        assertFalse(DwarfDaggerRushExecutionState.isWithinActiveWindow(
                200L,
                endTick
        ));
    }

    @Test
    void runtimeOwnsTheWindowAndThrustUsesTheExactFacade()
            throws IOException {
        String handler = handlerSource(
                "DwarfDaggerRushSkillHandler.java"
        );
        String state = handlerSource(
                "DwarfDaggerRushExecutionState.java"
        );
        String thrust = handlerSource(
                "DwarfDaggerThrustExecutionState.java"
        );
        String cleanup = source("combat/CombatTrackerCleanup.java");

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));
        assertFalse(handler.contains("DwarfDaggerRushTracker"));
        assertFalse(thrust.contains("DwarfDaggerRushTracker"));
        assertFalse(cleanup.contains("DwarfDaggerRushTracker"));
        assertTrue(handler.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "instance.executionState(DwarfDaggerRushExecutionState.class)"
        ));
        assertTrue(thrust.contains(
                "DwarfDaggerRushSkillHandler.isActive("
        ));
    }

    @Test
    void activeFacadePinsCasterSkillAndConcreteState()
            throws IOException {
        String handler = handlerSource(
                "DwarfDaggerRushSkillHandler.java"
        );

        assertEquals(
                1,
                occurrences(
                        handler,
                        "WeaponSkillRuntime.activeExecutionState("
                )
        );
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.DWARF_DAGGER_RUSH"
        ));
        assertTrue(handler.contains(
                "DwarfDaggerRushExecutionState.class"
        ));
        assertTrue(handler.contains("player.getUUID()"));
    }

    @Test
    void authoredBeginAndPresentationOrderRemainUnchanged()
            throws IOException {
        String handler = handlerSource(
                "DwarfDaggerRushSkillHandler.java"
        );
        String state = handlerSource(
                "DwarfDaggerRushExecutionState.java"
        );

        int initialize = handler.indexOf(
                "instance.initializeExecutionState(executionState)"
        );
        int cooldown = handler.indexOf(
                "WeaponSkillRuntime.commitCooldown(",
                initialize
        );
        int start = handler.indexOf(
                "executionState.start(",
                cooldown
        );
        int speed = handler.indexOf(
                "context.player().addEffect(",
                start
        );
        int lock = handler.indexOf(
                "WeaponSkillAnimationLock.setLock(",
                speed
        );
        int animation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                lock
        );
        assertTrue(initialize >= 0);
        assertTrue(cooldown > initialize);
        assertTrue(start > cooldown);
        assertTrue(speed > start);
        assertTrue(lock > speed);
        assertTrue(animation > lock);

        String startMethod = method(state, "void start(");
        assertTrue(startMethod.contains(
                "new DwarfDaggerRushPayload(true, durationTicks)"
        ));
        String settle = method(
                state,
                "private void settle(ServerPlayer player)"
        );
        assertTrue(settle.contains(
                "new DwarfDaggerRushPayload(false, 0)"
        ));
        assertFalse(settle.contains("removeEffect("));
        assertFalse(state.contains("MobEffects.MOVEMENT_SPEED"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            return "";
        }
        int openingBrace = source.indexOf('{', start);
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
        return "";
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }

    private static String handlerSource(String fileName)
            throws IOException {
        return Files.readString(javaRoot().resolve(
                "combat/skill/handler/" + fileName
        ));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(javaRoot().resolve(relative));
    }

    private static Path javaRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate Java source root");
    }
}

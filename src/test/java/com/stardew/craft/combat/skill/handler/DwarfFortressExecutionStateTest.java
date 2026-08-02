package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwarfFortressExecutionStateTest {
    @Test
    void activeWindowIncludesTheAuthoredEndTick() {
        long endTick = 180L;

        assertTrue(DwarfFortressExecutionState.isWithinActiveWindow(
                100L,
                endTick
        ));
        assertTrue(DwarfFortressExecutionState.isWithinActiveWindow(
                180L,
                endTick
        ));
        assertFalse(DwarfFortressExecutionState.isWithinActiveWindow(
                181L,
                endTick
        ));
    }

    @Test
    void reactiveShockLimitAndSameTickGateRemainAuthored() {
        assertTrue(DwarfFortressExecutionState.canTriggerReactiveShock(
                0,
                100L,
                101L
        ));
        assertFalse(DwarfFortressExecutionState.canTriggerReactiveShock(
                0,
                100L,
                100L
        ));
        assertFalse(DwarfFortressExecutionState.canTriggerReactiveShock(
                4,
                100L,
                101L
        ));
        assertFalse(DwarfFortressExecutionState.canTriggerReactiveShock(
                0,
                0L,
                0L
        ));
    }

    @Test
    void echoAndKnockbackThresholdsRemainAuthored() {
        assertFalse(DwarfFortressExecutionState.shouldTriggerEcho(3));
        assertTrue(DwarfFortressExecutionState.shouldTriggerEcho(4));
        assertEquals(
                1.0D,
                DwarfFortressExecutionState.knockbackResistanceBonus()
        );
        assertEquals(
                "dwarf_fortress",
                DwarfFortressExecutionState.createShockContext(1.2F)
                        .getSkillId()
        );
        assertEquals(
                1.2F,
                DwarfFortressExecutionState.createShockContext(1.2F)
                        .getDamageMultiplier()
        );
        assertEquals(
                SkillContext.SkillTier.MAJOR,
                DwarfFortressExecutionState.createShockContext(1.2F)
                        .getTier()
        );
    }

    @Test
    void runtimeStateOwnsEveryExecutionLocalResource() throws IOException {
        String handler = handlerSource(
                "DwarfFortressSkillHandler.java"
        );
        String state = handlerSource(
                "DwarfFortressExecutionState.java"
        );
        String playerEvents = source(
                "player/PlayerDataEventHandler.java"
        );

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(state.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));
        assertTrue(playerEvents.contains(
                "DwarfFortressSkillHandler.onDamageTaken("
        ));
        assertTrue(handler.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "instance.executionState(DwarfFortressExecutionState.class)"
        ));
    }

    @Test
    void externalHitUsesExactTypedStateAndReleaseSnapshot()
            throws IOException {
        String handler = handlerSource(
                "DwarfFortressSkillHandler.java"
        );
        String state = handlerSource(
                "DwarfFortressExecutionState.java"
        );

        assertEquals(
                1,
                occurrences(
                        handler,
                        "WeaponSkillRuntime.activeExecutionState("
                )
        );
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.DWARF_FORTRESS"
        ));
        assertTrue(handler.contains(
                "DwarfFortressExecutionState.class"
        ));
        assertTrue(state.contains(
                "createShockContext(damageMultiplier),\n"
                        + "                    weaponSnapshot,"
        ));
    }

    @Test
    void authoredStartDamageAndCleanupOrderArePreserved()
            throws IOException {
        String handler = handlerSource(
                "DwarfFortressSkillHandler.java"
        );
        String state = handlerSource(
                "DwarfFortressExecutionState.java"
        );

        int initialize = handler.indexOf(
                "instance.initializeExecutionState(executionState)"
        );
        int start = handler.indexOf(
                "executionState.start(",
                initialize
        );
        int cooldown = handler.indexOf(
                "WeaponSkillRuntime.commitCooldown(",
                initialize
        );
        int committed = handler.indexOf(
                "instance.registerCommittedEffect(",
                cooldown
        );
        int activePayload = handler.indexOf(
                "new DwarfFortressPayload(",
                cooldown
        );
        assertTrue(initialize >= 0);
        assertTrue(cooldown > initialize);
        assertTrue(committed > cooldown);
        assertTrue(start > committed);
        assertTrue(activePayload > start);

        String startMethod = method(state, "void start(");
        int guard = startMethod.indexOf("applyKnockbackGuard(player)");
        int shelter = startMethod.indexOf("player.addEffect(", guard);
        int initialShock = startMethod.indexOf(
                "triggerShockwave(",
                shelter
        );
        assertTrue(guard >= 0);
        assertTrue(shelter > guard);
        assertTrue(initialShock > shelter);

        String advance = method(
                state,
                "SkillTickResult advance("
        );
        int activeWindow = advance.indexOf(
                "isWithinActiveWindow(context.nowTick(), endTick)"
        );
        int echoGate = advance.indexOf(
                "shouldTriggerEcho(shocks)",
                activeWindow
        );
        int cleanup = advance.indexOf(
                "removeKnockbackGuard(player)",
                echoGate
        );
        assertTrue(activeWindow >= 0);
        assertTrue(echoGate > activeWindow);
        assertTrue(cleanup > echoGate);

        String cancel = method(state, "void cancel(ServerPlayer player)");
        assertTrue(cancel.contains("removeKnockbackGuard(player)"));
        assertFalse(cancel.contains("triggerShockwave("));
        assertFalse(cancel.contains("removeEffect("));
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

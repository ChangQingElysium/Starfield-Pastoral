package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkSwordBloodDebtExecutionStateTest {
    @Test
    void activeWindowKeepsTheAuthoredInclusiveDuration() {
        DarkSwordBloodDebtExecutionState state =
                new DarkSwordBloodDebtExecutionState(20L, 100);

        assertTrue(state.isActive(120L));
        assertEquals(
                SkillTickResult.CONTINUE,
                state.advance(120L)
        );
        assertEquals(
                SkillTickResult.COMPLETE,
                state.advance(121L)
        );
        assertFalse(state.isActive(121L));
    }

    @Test
    void cancellationEndsTheWindowWithoutChangingItsBoundary() {
        DarkSwordBloodDebtExecutionState state =
                new DarkSwordBloodDebtExecutionState(20L, 100);

        state.cancel();

        assertFalse(state.isActive(20L));
        assertEquals(
                SkillTickResult.COMPLETE,
                state.advance(20L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new DarkSwordBloodDebtExecutionState(20L, 0)
        );
    }

    @Test
    void handlerOwnsStateAndFacadeUsesTheExactRuntimeExecution()
            throws IOException {
        String handler = source(
                "combat/skill/handler/DarkSwordBloodDebtSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/DarkSwordBloodDebtExecutionState.java"
        );

        assertTrue(handler.contains("instance.initializeExecutionState("));
        assertTrue(handler.contains("instance.requireExecutionState("));
        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_DEBT"
        ));
        assertTrue(handler.contains(
                "DarkSwordBloodDebtExecutionState.class"
        ));
        assertFalse(handler.contains("DarkSwordBloodDebtTracker"));
        assertTrue(state.contains("implements SkillInstance.ExecutionState"));
        assertFalse(state.contains("static final Map"));
    }

    @Test
    void activationAndFinishKeepTheirOriginalSideEffectOrder()
            throws IOException {
        String handler = source(
                "combat/skill/handler/DarkSwordBloodDebtSkillHandler.java"
        );
        String begin = method(handler, "public void begin(");
        String finish = method(handler, "public void finish(");

        int health = begin.indexOf(
                "WeaponSkillRuntime.spendHealthDuringBegin("
        );
        int state = begin.indexOf("instance.initializeExecutionState(");
        int castEffect = begin.indexOf(
                "DarkSwordEffects.playBloodDebtCast("
        );
        int committed = begin.indexOf(
                "instance.registerCommittedEffect("
        );
        int activePayload = begin.indexOf(
                "new DarkSwordBloodDebtPayload("
        );
        int damage = begin.indexOf("WeaponSkillDamage.apply(");
        int animation = begin.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim("
        );
        int lock = begin.indexOf("WeaponSkillAnimationLock.setLock(");

        assertTrue(health >= 0);
        assertTrue(state > health);
        assertTrue(committed > state);
        assertTrue(castEffect > committed);
        assertTrue(activePayload > castEffect);
        assertTrue(damage > activePayload);
        assertTrue(animation > damage);
        assertTrue(lock > animation);

        int stateRead = finish.indexOf("instance.executionState(");
        int cancel = finish.indexOf(
                "DarkSwordBloodDebtExecutionState::cancel"
        );
        int cooldown = finish.indexOf(
                "WeaponSkillRuntime.commitCooldown("
        );
        int inactivePayload = finish.indexOf(
                "new DarkSwordBloodDebtPayload(false, 0)"
        );
        assertTrue(stateRead >= 0);
        assertTrue(cancel > stateRead);
        assertTrue(cooldown > cancel);
        assertTrue(inactivePayload > cooldown);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
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
        throw new AssertionError("Unclosed method " + signature);
    }

    private static String source(String relativePath) throws IOException {
        Path relative = Path.of("src", "main", "java", "com", "stardew", "craft")
                .resolve(relativePath);
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

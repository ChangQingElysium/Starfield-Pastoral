package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsectEyeStanceExecutionStateTest {
    @Test
    void firstAndLaterContextsPreserveTheAuthoredReward() {
        SkillContext first =
                InsectEyeStanceExecutionState.createSkillContext(
                        "insect_eye_stance",
                        true
                );
        SkillContext later =
                InsectEyeStanceExecutionState.createSkillContext(
                        "insect_eye_stance",
                        false
                );

        assertEquals(SkillContext.SkillTier.MINOR, first.getTier());
        assertEquals(1.05F, first.getDamageMultiplier());
        assertTrue(first.isGuaranteedCrit());
        assertEquals(1.05F, later.getDamageMultiplier());
        assertFalse(later.isGuaranteedCrit());
    }

    @Test
    void activeWindowIsInclusiveButCannotCrossDimensions() {
        assertTrue(InsectEyeStanceExecutionState.shouldRemainActive(
                130L,
                130L,
                true
        ));
        assertFalse(InsectEyeStanceExecutionState.shouldRemainActive(
                130L,
                131L,
                true
        ));
        assertFalse(InsectEyeStanceExecutionState.shouldRemainActive(
                130L,
                120L,
                false
        ));
    }

    @Test
    void runtimeStateExclusivelyOwnsTheDeferredTransaction()
            throws IOException {
        String handler = handlerSource("InsectEyeStanceSkillHandler.java");
        String state = handlerSource("InsectEyeStanceExecutionState.java");
        String events = source("combat/WeaponCombatEvents.java")
                + source("combat/WeaponHitPreparation.java");

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(state.contains(
                "private final DeferredSkillCooldown cooldown;"
        ));
        assertTrue(state.contains("private boolean firstHitPending = true;"));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));
        assertFalse(handler.contains("InsectEyeStanceTracker"));
        assertFalse(events.contains("InsectEyeStanceTracker"));
        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.INSECT_EYE_STANCE"
        ));
        assertTrue(handler.contains("instance.registerCommittedEffect("));
        assertFalse(handler.contains(
                "WeaponSkillRuntime.abandonDeferredCooldown(cooldown)"
        ));
        assertTrue(events.contains(
                "InsectEyeStanceSkillHandler.reserveAttack("
        ));
        assertTrue(handler.contains("public record AttackReservation("));
        assertTrue(handler.contains(
                "context.player().level().getGameTime()"
        ));
    }

    @Test
    void beginReservationAndSettlementOrderRemainAuthored()
            throws IOException {
        String handler = handlerSource("InsectEyeStanceSkillHandler.java");
        String state = handlerSource("InsectEyeStanceExecutionState.java");

        int defer = handler.indexOf("WeaponSkillRuntime.deferCooldown(");
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(executionState)",
                defer
        );
        int payload = handler.indexOf(
                "executionState.start(",
                initialize
        );
        int animation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                payload
        );
        assertTrue(defer >= 0);
        assertTrue(initialize > defer);
        assertTrue(payload > initialize);
        assertTrue(animation > payload);

        String consume = method(
                state,
                "SkillContext consumeAttack(ServerPlayer player, long nowTick)"
        );
        int reserveFromConsume = consume.indexOf(
                "reserveAttack(player, nowTick)"
        );
        int commitFromConsume = consume.indexOf(
                "reservation.commit().run()",
                reserveFromConsume
        );
        int returnContext = consume.indexOf(
                "return reservation.skillContext();",
                commitFromConsume
        );
        assertTrue(reserveFromConsume >= 0);
        assertTrue(commitFromConsume > reserveFromConsume);
        assertTrue(returnContext > commitFromConsume);

        String reserve = method(
                state,
                "InsectEyeStanceSkillHandler.AttackReservation reserveAttack("
        );
        int reservationId = reserve.indexOf(
                "long reservationId = ++nextReservationId;"
        );
        int firstToken = reserve.indexOf(
                "boolean guaranteedCrit = firstHitPending\n"
                        + "                && firstHitReservation == null;",
                reservationId
        );
        int holdToken = reserve.indexOf(
                "firstHitReservation = reservationId;",
                firstToken
        );
        int reservation = reserve.indexOf(
                "new InsectEyeStanceSkillHandler.AttackReservation(",
                holdToken
        );
        assertTrue(reservationId >= 0);
        assertTrue(firstToken > reservationId);
        assertTrue(holdToken > firstToken);
        assertTrue(reservation > holdToken);

        String commit = method(state, "private void commitReservation(");
        assertTrue(commit.contains("firstHitPending = false;"));
        assertTrue(commit.contains("firstHitReservation = null;"));

        String release = method(state, "private void releaseReservation(");
        assertTrue(release.contains("firstHitReservation = null;"));
        assertFalse(release.contains("firstHitPending = false;"));

        String settle = method(
                state,
                "private void settle(ServerPlayer player, long nowTick)"
        );
        int guard = settle.indexOf("if (settled)");
        int settled = settle.indexOf("settled = true;", guard);
        int cooldown = settle.indexOf(
                "WeaponSkillRuntime.commitDeferredCooldown(",
                settled
        );
        int stopPayload = settle.indexOf(
                "new InsectEyeStancePayload(false, 0)",
                cooldown
        );
        assertTrue(guard >= 0);
        assertTrue(settled > guard);
        assertTrue(cooldown > settled);
        assertTrue(stopPayload > cooldown);
        assertFalse(settle.contains("abandonDeferredCooldown"));
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

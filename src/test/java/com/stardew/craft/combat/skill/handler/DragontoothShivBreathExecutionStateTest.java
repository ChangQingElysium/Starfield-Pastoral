package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragontoothShivBreathExecutionStateTest {
    @Test
    void authoredOneHundredTwentyTickWindowRemainsInclusive() {
        DragontoothShivBreathExecutionState state =
                new DragontoothShivBreathExecutionState(
                        Level.OVERWORLD,
                        100L,
                        DragontoothShivBreathSkillHandler
                                .ACTIVE_DURATION_TICKS
                );

        assertTrue(state.isActive(220L, Level.OVERWORLD, true));
        assertFalse(state.isActive(221L, Level.OVERWORLD, true));
        assertFalse(state.isActive(200L, Level.NETHER, true));
        assertFalse(state.isActive(200L, Level.OVERWORLD, false));
        assertTrue(
                DragontoothShivBreathExecutionState
                        .isWithinActiveWindow(220L, 220L)
        );
        assertFalse(
                DragontoothShivBreathExecutionState
                        .isWithinActiveWindow(221L, 220L)
        );
    }

    @Test
    void handlerUsesAnExactRuntimeTypedStateFacade() throws IOException {
        String handler = source(
                "DragontoothShivBreathSkillHandler.java"
        );
        String state = source(
                "DragontoothShivBreathExecutionState.java"
        );

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));
        assertTrue(handler.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.DRAGONTOOTH_SHIV_BREATH"
        ));
        assertTrue(handler.contains(
                "DragontoothShivBreathExecutionState.class"
        ));
        assertFalse(handler.contains("DragontoothShivBreathTracker"));
    }

    @Test
    void beginAndFinishPreservePayloadBuffAndAnimationOrder()
            throws IOException {
        String handler = source(
                "DragontoothShivBreathSkillHandler.java"
        );
        String state = source(
                "DragontoothShivBreathExecutionState.java"
        );

        int energy = handler.indexOf(
                "WeaponSkillRuntime.consumeEnergyDuringBegin("
        );
        int cooldown = handler.indexOf(
                "WeaponSkillRuntime.commitCooldown(",
                energy
        );
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(executionState)",
                cooldown
        );
        int payload = handler.indexOf(
                "executionState.start(",
                initialize
        );
        int speed = handler.indexOf("ModMobEffects.SPEED", payload);
        int resistance = handler.indexOf(
                "MobEffects.DAMAGE_RESISTANCE",
                speed
        );
        int lock = handler.indexOf(
                "WeaponSkillAnimationLock.setLock(",
                resistance
        );
        int animation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                lock
        );
        assertTrue(energy >= 0);
        assertTrue(cooldown > energy);
        assertTrue(initialize > cooldown);
        assertTrue(payload > initialize);
        assertTrue(speed > payload);
        assertTrue(resistance > speed);
        assertTrue(lock > resistance);
        assertTrue(animation > lock);

        assertTrue(state.contains(
                "new DragontoothShivBreathPayload(true, durationTicks)"
        ));
        assertTrue(state.contains(
                "new DragontoothShivBreathPayload(false, 0)"
        ));
        String cancel = method(state, "void cancel(ServerPlayer player)");
        int guard = cancel.indexOf("if (settled)");
        int settle = cancel.indexOf("settled = true;", guard);
        int stopPayload = cancel.indexOf(
                "new DragontoothShivBreathPayload(false, 0)",
                settle
        );
        assertTrue(guard >= 0);
        assertTrue(settle > guard);
        assertTrue(stopPayload > settle);
        assertFalse(handler.contains("removeEffect("));
        assertTrue(handler.contains(
                ".ifPresent(state -> state.cancel(context.player()))"
        ));
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

    private static String source(String fileName) throws IOException {
        return Files.readString(handlerRoot().resolve(fileName));
    }

    private static Path handlerRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "handler"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate handler source root");
    }
}

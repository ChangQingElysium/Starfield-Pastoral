package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IridiumNeedleFrenzyExecutionStateTest {
    @Test
    void authoredWindowIncludesItsOneHundredTwentiethTick() {
        IridiumNeedleFrenzyExecutionState state = state(100L);

        assertTrue(state.isActive(220L, Level.OVERWORLD));
        assertEquals(
                SkillTickResult.CONTINUE,
                state.advance(220L, Level.OVERWORLD)
        );
        assertFalse(state.isActive(221L, Level.OVERWORLD));
        assertEquals(
                SkillTickResult.COMPLETE,
                state.advance(221L, Level.OVERWORLD)
        );
        assertFalse(state.isActive(221L, Level.OVERWORLD));
    }

    @Test
    void dimensionChangeAndCancellationCannotLeaveFrenzyReadable() {
        IridiumNeedleFrenzyExecutionState changedDimension = state(100L);
        assertFalse(changedDimension.isActive(150L, Level.NETHER));
        assertEquals(
                SkillTickResult.CANCEL,
                changedDimension.advance(150L, Level.NETHER)
        );
        assertFalse(changedDimension.isActive(150L, Level.OVERWORLD));

        IridiumNeedleFrenzyExecutionState cancelled = state(100L);
        cancelled.cancel();
        assertFalse(cancelled.isActive(150L, Level.OVERWORLD));
        assertEquals(
                SkillTickResult.COMPLETE,
                cancelled.advance(150L, Level.OVERWORLD)
        );
    }

    @Test
    void runtimeInstanceOwnsStateAndHandlerExposesTypedFacade()
            throws IOException {
        Path root = handlerRoot();
        String handler = Files.readString(root.resolve(
                "IridiumNeedleFrenzySkillHandler.java"
        ));
        String state = Files.readString(root.resolve(
                "IridiumNeedleFrenzyExecutionState.java"
        ));

        assertTrue(handler.contains(
                "instance.initializeExecutionState("
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.IRIDIUM_NEEDLE_FRENZY"
        ));
        assertFalse(handler.contains("IridiumNeedleFrenzyTracker"));
        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));

        int energy = handler.indexOf(
                "WeaponSkillRuntime.consumeEnergyDuringBegin("
        );
        int cooldown = handler.indexOf(
                "WeaponSkillRuntime.commitCooldown(",
                energy
        );
        int stateRegistration = handler.indexOf(
                "instance.initializeExecutionState(",
                cooldown
        );
        int startPayload = handler.indexOf(
                "new IridiumNeedleFrenzyPayload(true",
                stateRegistration
        );
        int speed = handler.indexOf(
                "context.player().addEffect(",
                startPayload
        );
        assertTrue(energy >= 0);
        assertTrue(cooldown > energy);
        assertTrue(stateRegistration > cooldown);
        assertTrue(startPayload > stateRegistration);
        assertTrue(speed > startPayload);
    }

    private static IridiumNeedleFrenzyExecutionState state(
            long nowTick
    ) {
        return new IridiumNeedleFrenzyExecutionState(
                Level.OVERWORLD,
                nowTick,
                IridiumNeedleFrenzySkillHandler.DURATION_TICKS
        );
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

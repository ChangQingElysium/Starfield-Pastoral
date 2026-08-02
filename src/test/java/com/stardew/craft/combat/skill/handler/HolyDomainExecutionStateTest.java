package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolyDomainExecutionStateTest {
    @Test
    void durationEndsAtTheExclusiveEndTick() {
        long endTick = 180L;

        assertFalse(HolyDomainExecutionState.isExpired(100L, endTick));
        assertFalse(HolyDomainExecutionState.isExpired(179L, endTick));
        assertTrue(HolyDomainExecutionState.isExpired(180L, endTick));
    }

    @Test
    void pulseBecomesDueAtItsScheduledTick() {
        assertFalse(HolyDomainExecutionState.shouldPulse(119L, 120L));
        assertTrue(HolyDomainExecutionState.shouldPulse(120L, 120L));
        assertTrue(HolyDomainExecutionState.shouldPulse(121L, 120L));
    }

    @Test
    void runtimeStateOwnsPulseScheduleSnapshotAndFinish()
            throws IOException {
        String handler = source(
                "combat/skill/handler/HolyDomainSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/HolyDomainExecutionState.java"
        );

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertFalse(state.contains("static final Map"));
        assertTrue(handler.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "instance.executionState(HolyDomainExecutionState.class)"
        ));
        assertFalse(handler.contains("HolyBladeSanctuaryTracker"));
        assertTrue(state.contains(
                "executionContext.weaponSnapshot()"
        ));
        assertTrue(state.contains(
                "nextPulseTick += HolyDomainSkillHandler."
                        + "PULSE_INTERVAL_TICKS"
        ));
        assertTrue(state.contains(
                "WeaponSkillDamage.apply("
        ));
        assertTrue(state.contains(
                "HolyBladeEffects.playHeal("
        ));
    }

    @Test
    void expiryIsCheckedBeforeThePulseAtTheEndTick()
            throws IOException {
        String state = source(
                "combat/skill/handler/HolyDomainExecutionState.java"
        );

        int expiry = state.indexOf(
                "isExpired(context.nowTick(), endTick)"
        );
        int pulse = state.indexOf(
                "shouldPulse(context.nowTick(), nextPulseTick)",
                expiry
        );
        assertTrue(expiry >= 0);
        assertTrue(pulse > expiry);
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

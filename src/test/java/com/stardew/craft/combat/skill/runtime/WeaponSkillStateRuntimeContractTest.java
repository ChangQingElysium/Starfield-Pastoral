package com.stardew.craft.combat.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillStateRuntimeContractTest {
    private static final List<String> OWNED_TICKS = List.of(
            "BrokenTridentCatchTracker.tick(player, nowTick)",
            "CrystalDaggerLayerTracker.tick(player, nowTick)",
            "InsectDashChainState.tick(player, nowTick)",
            "ObsidianResonanceTracker.tick(player, nowTick)",
            "SilverSaberSkillHelper.tickPersistedFoldback(player, nowTick)",
            "TemperedFireRingTracker.tick(player, nowTick)",
            "SteelFalchionDotTracker.tickDetachedEffects(player, nowTick)",
            "RiftPathDamageTracker.tick(player, nowTick)",
            "WindSpireTracker.tick(player, nowTick)"
    );

    @Test
    void playerEventKnowsOnlyTheTopLevelWeaponRuntime()
            throws IOException {
        String playerEvents = source("player/PlayerDataEventHandler.java");

        assertTrue(playerEvents.contains(
                "WeaponSkillRuntime.tickPlayer(player, gameTime)"
        ));
        for (String tick : OWNED_TICKS) {
            String tracker = tick.substring(0, tick.indexOf('.'));
            assertFalse(playerEvents.contains(tracker + ".tick("));
        }
        assertFalse(playerEvents.contains(
                "SteelFalchionDotTracker.tickDetachedEffects("
        ));
    }

    @Test
    void weaponCombatEventsDoNotOwnPersistedStateTicking()
            throws IOException {
        String combatEvents = source("combat/WeaponCombatEvents.java");

        assertFalse(combatEvents.contains("PlayerTickEvent"));
        assertFalse(combatEvents.contains("handleTimeout("));
        assertFalse(combatEvents.contains("WeaponRegistry.get(weaponId)"));
    }

    @Test
    void statusObjectsDoNotSubscribeTheirOwnPlayerTicks()
            throws IOException {
        String insectDash = source(
                "combat/skill/InsectDashChainState.java"
        );
        String windSpire = source(
                "combat/skill/WindSpireTracker.java"
        );

        assertFalse(insectDash.contains("PlayerTickEvent"));
        assertFalse(windSpire.contains("PlayerTickEvent"));
    }

    @Test
    void stateRuntimeOwnsStatusesPassivesAndDetachedEffects()
            throws IOException {
        String runtime = source(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );
        String stateRuntime = source(
                "combat/skill/runtime/WeaponSkillStateRuntime.java"
        );

        assertTrue(runtime.contains(
                "WeaponSkillStateRuntime.tickPlayer(player, nowTick)"
        ));
        for (String tick : OWNED_TICKS) {
            assertTrue(stateRuntime.contains(tick), tick);
        }
    }

    @Test
    void steelExecutionAndResidualDotHaveDifferentTickEntrypoints()
            throws IOException {
        String dots = source(
                "combat/skill/handler/SteelFalchionDotTracker.java"
        );
        String line = source(
                "combat/skill/handler/SteelFalchionLineSkillHandler.java"
        );
        String trace = source(
                "combat/skill/handler/SteelFalchionTraceSkillHandler.java"
        );

        assertTrue(dots.contains("void tickDetachedEffects("));
        assertTrue(dots.contains("lastTick"));
        assertFalse(dots.contains("tickExecutionState"));
        assertTrue(line.contains(
                "SteelFalchionLineExecutionState.class"
        ));
        assertTrue(trace.contains(
                "SteelFalchionTraceExecutionState.class"
        ));
        assertFalse(line.contains("SteelFalchionDotTracker.tickDetachedEffects"));
        assertFalse(trace.contains("SteelFalchionDotTracker.tickDetachedEffects"));
    }

    private static String source(String relativeSource) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativeSource);
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

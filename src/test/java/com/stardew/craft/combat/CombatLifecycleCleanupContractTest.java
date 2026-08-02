package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatLifecycleCleanupContractTest {
    @Test
    void runtimeTerminationIsIsolatedAndCannotMaskBeginFailure()
            throws IOException {
        String runtime = normalizedSource(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );

        assertTrue(runtime.contains(
                "private static RuntimeException endExecution("
        ));
        assertTrue(runtime.contains(
                "RuntimeException cleanupFailure = endExecution("
        ));
        assertTrue(runtime.contains(
                "exception.addSuppressed(cleanupFailure);"
        ));
        assertTrue(runtime.contains(
                "} finally { instance.clearExecutionState(); synchronized "
                        + "(WeaponSkillRuntime.class) { ACTIVE.remove("
                        + "instance.instanceId());"
        ));
        assertTrue(runtime.contains(
                "for (ActiveExecution execution : executions) "
                        + "{ logTerminationFailure("
        ));
        assertTrue(occurrences(
                runtime,
                "!player.isAlive() || player.isRemoved()"
        ) >= 2);
    }

    @Test
    void deathCloneRespawnAndLogoutUseConcretePlayerCleanup()
            throws IOException {
        String events = normalizedSource(
                "player/PlayerDataEventHandler.java"
        );

        assertTrue(events.contains(
                "void onPlayerDeath("
        ));
        assertTrue(events.contains(
                "void onPlayerClone(PlayerEvent.Clone event)"
        ));
        assertTrue(events.contains(
                "void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)"
        ));
        assertTrue(events.contains(
                "CombatTrackerCleanup.onPlayerUnavailable( player )"
        ));
        assertTrue(occurrences(
                events,
                "cleanupTransientCombat(player);"
        ) >= 5);
    }

    @Test
    void concreteCleanupOwnsRuntimeAndSilverFoldback()
            throws IOException {
        String cleanup = normalizedSource(
                "combat/CombatTrackerCleanup.java"
        );

        assertTrue(cleanup.contains(
                "void onPlayerUnavailable(ServerPlayer player)"
        ));
        assertTrue(cleanup.contains(
                "WeaponSkillRuntime.removePlayer(playerId)"
        ));
        assertTrue(cleanup.contains(
                "WeaponSkillContextStore.clear(player)"
        ));
        assertTrue(cleanup.contains(
                "WeaponSkillAnimationLock.clear(player)"
        ));
        assertTrue(cleanup.contains(
                "DamageNumberContextStore.clear(player)"
        ));
        assertTrue(cleanup.contains(
                "AuthoredDirectDamageContextStore.clear(player)"
        ));
        assertTrue(cleanup.contains(
                "YobaProtectionState.clear(player)"
        ));
        assertTrue(cleanup.contains(
                "SilverSaberSkillHelper.cancelFoldback("
        ));
        assertTrue(cleanup.contains(
                "SilverSaberFoldbackState.clear(player)"
        ));
        assertTrue(cleanup.contains(
                "DragonBreathTracker.clear(player)"
        ));
        assertTrue(cleanup.contains(
                "DashMovementTracker.clear(player)"
        ));
        assertTrue(cleanup.contains(
                "WeaponSkillMovementArbiter.removePlayer(playerId)"
        ));
        assertTrue(cleanup.contains(
                "for (Runnable cleanupStep : cleanupSteps)"
        ));
        assertTrue(cleanup.contains(
                "catch (RuntimeException exception)"
        ));
        assertTrue(!cleanup.contains("MeowmereShotTracker"));
        assertTrue(!cleanup.contains("MeowmereSymphonyTracker"));
        assertTrue(!cleanup.contains("DwarfDaggerThrustTracker"));
        assertTrue(!cleanup.contains("DwarfDaggerRushTracker"));
        assertTrue(!cleanup.contains("CarvingKnifeThrustTracker"));
        assertTrue(!cleanup.contains("IridiumNeedleThrustTracker"));
        assertTrue(!cleanup.contains("BrokenTridentThrustTracker"));
        assertTrue(!cleanup.contains("GalaxyDaggerThrustTracker"));
        assertTrue(!cleanup.contains("InfinityDaggerThrustTracker"));
        assertTrue(!cleanup.contains("ClaymoreFoldbackTracker"));
        assertTrue(!cleanup.contains("LightCounterParryState"));
        assertTrue(!cleanup.contains("InsectEyeStanceTracker"));
        assertTrue(!cleanup.contains("ElfBladeTracker"));
        assertTrue(!cleanup.contains("OssifiedExecutionTracker"));
        assertTrue(cleanup.contains(
                "CombatDamageHistory.remove(playerId)"
        ));
        assertTrue(cleanup.contains(
                "StardewWeaponAttackRecovery.clear(playerId)"
        ));
        assertTrue(cleanup.contains(
                "OrdinaryWeaponAttackFrameStore.clear(playerId)"
        ));
        assertTrue(cleanup.contains(
                "CrossDimensionNativeAttackHandler.clear(playerId)"
        ));
        assertTrue(!cleanup.contains("WeaponCombatEvents.removePlayer("));
        assertTrue(!cleanup.contains("onPlayerLogout(UUID"));
    }

    @Test
    void dragonBreathResourceClearsAndResynchronizesWithConcretePlayer()
            throws IOException {
        String tracker = normalizedSource(
                "combat/skill/DragonBreathTracker.java"
        );
        String events = normalizedSource(
                "player/PlayerDataEventHandler.java"
        );

        assertTrue(tracker.contains(
                "void clear(ServerPlayer player)"
        ));
        assertTrue(tracker.contains("setStacks(player, 0);"));
        assertTrue(tracker.contains(
                "void sync(ServerPlayer player)"
        ));
        assertTrue(events.contains(
                "DragonBreathTracker.sync(player);"
        ));
    }

    @Test
    void persistedSkillStatesDoNotDuplicateLogoutOwnership()
            throws IOException {
        String insectDash = normalizedSource(
                "combat/skill/InsectDashChainState.java"
        );
        String silverFoldback = normalizedSource(
                "combat/skill/SilverSaberFoldbackState.java"
        );

        assertTrue(!insectDash.contains("PlayerLoggedOutEvent"));
        assertTrue(!silverFoldback.contains("PlayerLoggedOutEvent"));
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

    private static String normalizedSource(String relativeFile)
            throws IOException {
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
                return Files.readString(candidate)
                        .replaceAll("\\s+", " ");
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}

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
                "} finally { synchronized (WeaponSkillRuntime.class) "
                        + "{ ACTIVE.remove(instance.instanceId());"
        ));
        assertTrue(runtime.contains(
                "for (ActiveExecution execution : executions) "
                        + "{ logTerminationFailure("
        ));
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
                "WeaponSkillRuntime.removePlayer(playerId); "
                        + "SilverSaberSkillHelper.cancelFoldback("
        ));
        assertTrue(cleanup.contains(
                "SilverSaberFoldbackState.clear(player);"
        ));
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

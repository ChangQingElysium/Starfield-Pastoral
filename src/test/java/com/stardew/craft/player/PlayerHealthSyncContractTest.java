package com.stardew.craft.player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHealthSyncContractTest {
    @Test
    void healthOnlyApiUsesTheSlimVitalsPacket() throws IOException {
        String source = Files.readString(findSource("PlayerStardewDataAPI.java"));
        String method = method(source, "public static void setHealth(");

        assertTrue(method.contains("data.setHealth(health);"));
        assertTrue(method.contains(
                "PlayerDataEventHandler.syncPlayerVitals(player, data);"
        ));
        assertFalse(method.contains(
                "PlayerDataEventHandler.syncPlayerData(player, data);"
        ));
    }

    @Test
    void acceptedCombatHitUsesSlimVitalsAndNeverFullPlayerSync() throws IOException {
        String source = Files.readString(findSource("PlayerDataEventHandler.java"));
        String method = method(source, "public static void onPlayerHurt(");

        assertTrue(method.contains("syncPlayerVitals(player, data);"));
        assertFalse(method.contains("syncPlayerData(player, data);"));
    }

    @Test
    void playerTickConsumesFullAndVitalsNetworkDirtySeparately() throws IOException {
        String source = Files.readString(findSource("PlayerDataEventHandler.java"));
        String method = method(source, "public static void onPlayerTick(");

        assertTrue(method.contains("changed || data.isFullSyncDirty()"));
        assertTrue(method.contains("else if (data.isVitalsSyncDirty())"));
        assertTrue(method.contains("syncPlayerVitals(player, data);"));
        assertFalse(method.contains("data.markClean();"));
    }

    private static Path findSource(String fileName) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "player",
                fileName
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        assertTrue(openingBrace > start, "Missing method body " + signature);

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
}

package com.stardew.craft.block.mine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerVitalsHotPathContractTest {
    @Test
    void toxicSporeDamageUsesTheUnifiedDamageLifecycle() throws IOException {
        String source = Files.readString(findSource("ToxicSporeBlock.java"));

        assertTrue(source.contains("DimensionDamageMapper"));
        assertTrue(source.contains(".toMinecraftHealthDamage("));
        assertTrue(source.contains("HitCooldownDamageSource.bypassVanillaCooldown("));
        assertTrue(source.contains("player.hurt("));
        assertFalse(source.contains("data.setHealth("));
        assertFalse(source.contains("syncPlayerVitals("));
        assertFalse(source.contains("StardewDamageHooks.onHealthDepleted("));
        assertTrue(occurrences(source, "MobEffects.POISON") == 2);
    }

    @Test
    void mineShaftFallDamageUsesSlimVitalsSync() throws IOException {
        String source = Files.readString(findSource("MineLadderBlock.java"));

        assertTrue(source.contains("PlayerDataEventHandler.syncPlayerVitals(serverPlayer, sdData);"));
        assertFalse(source.contains("PlayerDataEventHandler.syncPlayerData(serverPlayer, sdData);"));
    }

    private static Path findSource(String fileName) throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "block", "mine", fileName
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

    private static int occurrences(String source, String needle) {
        int count = 0;
        int cursor = 0;
        while ((cursor = source.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }
}

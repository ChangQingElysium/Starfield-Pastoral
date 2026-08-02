package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewWeaponSpeedSourceContractTest {
    @Test
    void projectionRemainsAnchoredToBundledMeleeWeaponSource()
            throws IOException {
        String original = source(Path.of(
                "源文件", "StardewValley.Tools", "MeleeWeapon.cs"
        ));
        String projection = source(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "combat", "StardewWeaponSpeedRules.java"
        ));

        assertTrue(original.contains(
                "public const int millisecondsPerSpeedPoint = 40;"
        ));
        assertTrue(original.contains(
                "public const int defaultSpeed = 400;"
        ));
        assertTrue(original.contains(
                "public const int baseClubSpeed = -8;"
        ));
        assertTrue(original.contains(
                "swipeSpeed = (float)(400 - speed.Value * 40)"
        ));
        assertTrue(original.contains(
                "swipeSpeed *= 1f - who.buffs.WeaponSpeedMultiplier;"
        ));

        assertTrue(projection.contains("BASE_SWIPE_MILLISECONDS = 400.0D"));
        assertTrue(projection.contains("MILLISECONDS_PER_RAW_SPEED = 40.0D"));
        assertTrue(projection.contains("CLUB_BASE_RAW_SPEED = -8.0D"));
        assertTrue(projection.contains("case SWORD -> 1.3D * 6.0D / 8.0D"));
        assertTrue(projection.contains("case DAGGER -> 2.0D / 4.0D"));
        assertTrue(projection.contains("case CLUB -> 1.3D * 6.0D / 5.0D"));
    }

    private static String source(Path relative) throws IOException {
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

package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssifiedMarkCooldownContractTest {
    @Test
    void shortenedCooldownFreezesItsAdjustedEndAtCastTime()
            throws IOException {
        String tracker = source("OssifiedMarkTracker.java");

        assertTrue(tracker.contains(
                "TAG_UNTRIGGERED_COOLDOWN_END"
        ));
        assertTrue(tracker.contains(
                "nowTick + WeaponSkillCooldowns.adjustedDuration("
        ));
        int expire = tracker.indexOf(
                "private static void handleExpire("
        );
        int clear = tracker.indexOf(
                "private static void clearMark(",
                expire
        );
        assertTrue(expire >= 0 && clear > expire);
        String expireMethod = tracker.substring(expire, clear);
        assertTrue(expireMethod.contains(
                "WeaponSkillCooldowns.setCooldownUntil("
        ));
        assertFalse(expireMethod.contains(
                "WeaponSkillCooldowns.setCooldown("
        ));
    }

    @Test
    void absoluteCooldownSetterDoesNotReapplyEquipmentModifiers()
            throws IOException {
        String cooldowns = source("WeaponSkillCooldowns.java");
        int method = cooldowns.indexOf(
                "public static void setCooldownUntil("
        );
        int modifiers = cooldowns.indexOf(
                "static int adjustedDuration(",
                method
        );
        assertTrue(method >= 0 && modifiers > method);
        String absoluteSetter = cooldowns.substring(method, modifiers);
        assertFalse(absoluteSetter.contains("adjustedDuration("));
        assertTrue(absoluteSetter.contains(
                "normalizedEndTick - nowTick"
        ));
    }

    private static String source(String fileName) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                fileName
        );
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

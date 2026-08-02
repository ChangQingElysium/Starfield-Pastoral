package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillCooldownContractTest {
    @Test
    void relativeCooldownAdjustsOnceBeforeWritingAnAbsoluteEnd()
            throws IOException {
        String source = readSkillSource("WeaponSkillCooldowns.java");
        String relativeSetter = method(
                source,
                "public static void setCooldown(\n"
                        + "            Player player,\n"
                        + "            ItemStack releaseWeapon,"
        );
        String absoluteSetter = method(
                source,
                "public static void setCooldownUntil("
        );
        String adjustment = method(
                source,
                "static int adjustedDuration(\n"
                        + "            Player player,\n"
                        + "            ItemStack releaseWeapon,"
        );

        assertTrue(relativeSetter.contains(
                "int appliedDuration = adjustedDuration(\n"
                        + "                player,\n"
                        + "                releaseWeapon,\n"
                        + "                durationTicks\n"
                        + "        );"
        ));
        assertTrue(relativeSetter.contains("nowTick + appliedDuration"));
        assertTrue(relativeSetter.contains("setCooldownUntil("));
        assertFalse(absoluteSetter.contains("adjustedDuration("));
        assertEquals(1, occurrences(adjustment, "StardewEnchantments.ARTFUL"));
        assertEquals(1, occurrences(adjustment, "ProfessionType.ACROBAT"));
    }

    @Test
    void ossifiedMarkFreezesAdjustedEndAndPreservesLegacyTags()
            throws IOException {
        String source = readSkillSource("OssifiedMarkTracker.java");
        String apply = method(source, "public static void apply(")
                .replaceAll("\\s+", " ");
        String expire = method(source, "private static void handleExpire(")
                .replaceAll("\\s+", " ");

        assertTrue(apply.contains(
                "TAG_UNTRIGGERED_COOLDOWN_END, "
                        + "nowTick + WeaponSkillCooldowns.adjustedDuration("
        ));
        assertTrue(expire.contains(
                "? tag.getLong(TAG_UNTRIGGERED_COOLDOWN_END) "
                        + ": nowTick + WeaponSkillCooldowns.adjustedDuration("
        ));
        assertTrue(expire.contains(
                "untriggeredCooldownRemaining( "
                        + "tag.getLong(TAG_START_TICK), nowTick"
        ));
        assertTrue(expire.contains("WeaponSkillCooldowns.setCooldownUntil("));
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

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static String readSkillSource(String fileName)
            throws IOException {
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
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}

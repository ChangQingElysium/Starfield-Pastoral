package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossDimensionSkillPostHitContractTest {
    @Test
    void nonStardewTargetsStillReachWeaponSkillPostHitRules()
            throws IOException {
        String method = postHitMethodSource();

        assertFalse(
                method.contains(
                        "if (!DimensionDamageMapper"
                                + ".isInStardewDimension(target)) return;"
                )
        );
        assertTrue(method.contains(
                "boolean inStardewDimension ="
        ));
        assertTrue(method.contains(
                "if (inStardewDimension) {"
        ));
        assertTrue(method.indexOf(
                "LavaKatanaMarkTracker.apply("
        ) > method.indexOf(
                "boolean inStardewDimension ="
        ));
        assertTrue(method.indexOf(
                "TideMarkTracker.createBonusContext()"
        ) > method.indexOf(
                "boolean inStardewDimension ="
        ));
    }

    @Test
    void postHitWeaponRulesUseTheDamageSnapshotInsteadOfCurrentHand()
            throws IOException {
        String method = postHitMethodSource();
        int heatRule = method.indexOf(
                "// === 熔岩武士刀：命中叠加"
        );
        int nextRule = method.indexOf(
                "boolean isGalaxyBonus",
                heatRule
        );

        assertTrue(heatRule >= 0 && nextRule > heatRule);
        String heatBlock = method.substring(heatRule, nextRule);
        assertTrue(heatBlock.contains(
                "ItemStack weapon = damageWeapon;"
        ));
        assertFalse(heatBlock.contains("getMainHandItem()"));
    }

    private static String postHitMethodSource() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "WeaponCombatEvents.java"
        );
        Path source = locate(relative);
        String contents = Files.readString(source);
        int start = contents.indexOf(
                "public static void onLivingDamagePost("
        );
        int end = contents.indexOf(
                "private static int getCombatExperienceOnKill(",
                start
        );
        assertTrue(start >= 0 && end > start);
        return contents.substring(start, end);
    }

    private static Path locate(Path relative) throws IOException {
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
}

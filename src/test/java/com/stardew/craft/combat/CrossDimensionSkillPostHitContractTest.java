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
        String coordinator = readCombatSource(
                "WeaponAppliedHitCoordinator.java"
        );
        String skills = readCombatSource(
                "BuiltinSkillAppliedHitRules.java"
        );
        String passives = readCombatSource(
                "BuiltinWeaponPassiveAppliedHitRules.java"
        );

        assertFalse(coordinator.contains(
                        "if (!DimensionDamageMapper"
                                + ".isInStardewDimension(target)) return;"
                ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyLavaBrand(hit);"
        ));
        assertTrue(coordinator.contains(
                "BuiltinWeaponPassiveAppliedHitRules.applyTideMark(hit);"
        ));
        assertTrue(skills.contains("LavaKatanaMarkTracker.apply("));
        int lavaBrand = skills.indexOf("static void applyLavaBrand(");
        assertTrue(lavaBrand >= 0);
        assertFalse(skills.substring(lavaBrand).contains(
                "hit.inStardewDimension()"
        ));
        assertTrue(passives.contains("TideMarkTracker.createBonusContext()"));
        assertFalse(passives.contains("inStardewDimension()"));
    }

    @Test
    void postHitWeaponRulesUseTheDamageSnapshotInsteadOfCurrentHand()
            throws IOException {
        String passives = readCombatSource(
                "BuiltinWeaponPassiveAppliedHitRules.java"
        );
        assertTrue(passives.contains("hit.weapon().getItem()"));
        assertTrue(passives.contains("hit.weaponSnapshot().orElseThrow()"));
        assertFalse(passives.contains("getMainHandItem()"));
    }

    private static String readCombatSource(String fileName) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                fileName
        );
        Path source = locate(relative);
        return Files.readString(source);
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

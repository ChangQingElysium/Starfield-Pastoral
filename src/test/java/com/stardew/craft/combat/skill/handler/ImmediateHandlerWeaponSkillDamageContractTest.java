package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmediateHandlerWeaponSkillDamageContractTest {
    private static final List<String> SAFE_BATCH_A = List.of(
            "BoneFractureSkillHandler.java",
            "BurglarShankSkillHandler.java",
            "ClaymoreFoldbackSkillHandler.java",
            "CrystalDaggerLayerSkillHandler.java",
            "DarkSwordBloodDebtSkillHandler.java",
            "DragonBreathJudgementSkillHandler.java",
            "DragonBreathThrustSkillHandler.java",
            "DragontoothShivStabSkillHandler.java",
            "DwarfRuneGuardSkillHandler.java",
            "GalaxyDaggerStarleapSkillHandler.java",
            "GalaxyJudgementSkillHandler.java"
    );

    @Test
    void safeBatchAUsesTheCentralizedEntryAndReleaseSnapshot()
            throws IOException {
        for (String handler : SAFE_BATCH_A) {
            String source = readHandler(handler);
            assertTrue(
                    source.contains("WeaponSkillDamage.apply("),
                    handler + " must use the centralized damage entry"
            );
            assertTrue(
                    source.contains("context.weaponSnapshot()"),
                    handler + " must bind the release snapshot explicitly"
            );
            assertFalse(
                    source.contains(
                            "WeaponSkillContextStore.setPending("
                    ),
                    handler + " must not bind pending context locally"
            );
            assertFalse(
                    source.contains("context.player().attack("),
                    handler + " must not retain Player.attack"
            );
        }
    }

    private static String readHandler(String fileName) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "handler",
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

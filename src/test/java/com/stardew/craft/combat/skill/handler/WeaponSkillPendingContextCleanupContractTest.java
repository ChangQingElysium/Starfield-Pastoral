package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillPendingContextCleanupContractTest {
    private static final List<String> CENTRALIZED_BATCH_B_HANDLERS =
            List.of(
                    "HolySmiteSkillHandler.java",
                    "InsectDashSkillHandler.java",
                    "IronDirkThrustSkillHandler.java",
                    "LavaKatanaBrandSkillHandler.java",
                    "StartrailRiftSkillHandler.java",
                    "TemperedQuenchSkillHandler.java",
                    "TetanusStrikeSkillHandler.java",
                    "TideReelSkillHandler.java",
                    "TreeBlessingSkillHandler.java",
                    "WindSpireThrustSkillHandler.java",
                    "YetiToothMarkSkillHandler.java"
            );

    private static final List<String> HANDLERS = List.of(
            "BoneFractureSkillHandler.java",
            "BurglarShankSkillHandler.java",
            "CrescentSlashSkillHandler.java",
            "CrystalDaggerLayerSkillHandler.java",
            "DarkSwordBloodDebtSkillHandler.java",
            "DesperatePlunderSkillHandler.java",
            "ForestBlessingSkillHandler.java",
            "HolySmiteSkillHandler.java",
            "InsectDashSkillHandler.java",
            "IronDirkThrustSkillHandler.java",
            "ShadowDaggerExecuteSkillHandler.java",
            "TemperedQuenchSkillHandler.java",
            "TetanusStrikeSkillHandler.java",
            "TideReelSkillHandler.java",
            "TreeBlessingSkillHandler.java",
            "WickedKrisNestBurstSkillHandler.java",
            "WickedKrisVenomRippleSkillHandler.java",
            "WindSpireThrustSkillHandler.java",
            "YetiToothMarkSkillHandler.java"
    );

    @Test
    void everyMigratedHandlerDelegatesPendingCleanup()
            throws IOException {
        for (String handler : HANDLERS) {
            String source = readHandler(handler);
            assertTrue(
                    source.contains("WeaponSkillDamage.apply("),
                    handler + " must use centralized skill damage"
            );
            assertTrue(
                    source.contains("context.weaponSnapshot()"),
                    handler + " must bind the release snapshot explicitly"
            );
            assertFalse(
                    source.contains(
                            "WeaponSkillContextStore.setPending("
                    ),
                    handler + " must delegate pending cleanup"
            );
            assertFalse(
                    source.contains("context.player().attack("),
                    handler + " must not retain Player.attack"
            );
        }
    }

    @Test
    void shadowExecuteBonusBindsTheReleaseWeaponSnapshot()
            throws IOException {
        String source = readHandler(
                "ShadowDaggerExecuteSkillHandler.java"
        ).replaceAll("\\s+", " ");

        assertTrue(source.contains(
                "createExecuteBonusContext(), "
                        + "context.weaponSnapshot(), "
                        + "context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS"
        ));
    }

    @Test
    void centralizedBatchBUsesExplicitReleaseSnapshots()
            throws IOException {
        for (String handler : CENTRALIZED_BATCH_B_HANDLERS) {
            String source = readHandler(handler);

            assertTrue(
                    source.contains("WeaponSkillDamage.apply("),
                    handler + " must use centralized skill damage"
            );
            assertTrue(
                    source.contains("context.weaponSnapshot()"),
                    handler + " must bind its release weapon"
            );
            assertFalse(
                    source.contains("WeaponSkillContextStore.setPending("),
                    handler + " must not own pending context"
            );
            assertFalse(
                    source.contains("context.player().attack("),
                    handler + " must not call Player.attack directly"
            );
        }
    }

    @Test
    void lavaBrandKeepsPreparedReleaseAtomicAroundDamage()
            throws IOException {
        String source = readHandler(
                "LavaKatanaBrandSkillHandler.java"
        );
        int prepare = source.indexOf(
                "LavaKatanaMarkTracker.prepareRelease("
        );
        int tryBlock = source.indexOf("try {", prepare);
        int damage = source.indexOf("WeaponSkillDamage.apply(", tryBlock);
        int finallyBlock = source.indexOf("} finally {", damage);
        int discard = source.indexOf(
                "LavaKatanaMarkTracker.discardPreparedRelease(",
                finallyBlock
        );

        assertTrue(
                prepare >= 0
                        && tryBlock > prepare
                        && damage > tryBlock
                        && finallyBlock > damage
                        && discard > finallyBlock
        );
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

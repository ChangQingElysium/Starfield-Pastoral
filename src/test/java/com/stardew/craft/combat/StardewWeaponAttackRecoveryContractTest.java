package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewWeaponAttackRecoveryContractTest {
    @Test
    void serverGateRejectsOnlyUnrecoveredOrdinaryAttacks()
            throws IOException {
        String events = source("WeaponCombatEvents.java");

        int stayStrike = events.indexOf(
                "SilverSaberSkillHelper.tryHandleStayStrike("
        );
        int recovery = events.indexOf(
                "!StardewWeaponAttackRecovery.tryAcquire("
        );
        int pendingSkill = events.lastIndexOf(
                "!WeaponSkillContextStore.hasPending(player, nowTick)",
                recovery
        );
        int attackHandlerEnd = events.indexOf(
                "public static void onLeftClickBlock(",
                recovery
        );

        assertTrue(events.contains(
                "@SubscribeEvent(priority = EventPriority.LOWEST)"
        ));
        assertTrue(stayStrike >= 0 && stayStrike < recovery);
        assertTrue(stayStrike < pendingSkill && pendingSkill < recovery);
        assertTrue(recovery < attackHandlerEnd);
        assertTrue(events.substring(recovery, attackHandlerEnd).contains(
                "event.setCanceled(true)"
        ));
        assertTrue(!events.contains("AttackTargetTracker"));
    }

    @Test
    void primaryHurtOwnsAnExactSynchronousOrdinaryAttackFrame()
            throws IOException {
        String mixin = mainSource("mixin/PlayerSweepAttackMixin.java");

        int redirect = mixin.indexOf(
                "private boolean stardewcraft$authorizedPrimaryHurt("
        );
        int bind = mixin.indexOf(
                "OrdinaryWeaponAttackFrameStore.bind(",
                redirect
        );
        int hurt = mixin.indexOf("return target.hurt(", bind);
        int finallyBlock = mixin.indexOf("} finally {", hurt);
        int discard = mixin.indexOf(
                "OrdinaryWeaponAttackFrameStore.discard(",
                finallyBlock
        );

        assertTrue(redirect >= 0);
        assertTrue(bind > redirect);
        assertTrue(hurt > bind);
        assertTrue(finallyBlock > hurt);
        assertTrue(discard > finallyBlock);
        assertTrue(mixin.contains("WeaponDamageSnapshot.capture("));
    }

    @Test
    void timingDoesNotReadMinecraftAttackStrength() throws IOException {
        String events = source("WeaponCombatEvents.java");
        String recovery = source("StardewWeaponAttackRecovery.java");
        String timingBoundary = events + recovery;

        assertTrue(!timingBoundary.contains("getAttackStrengthScale"));
        assertTrue(!timingBoundary.contains("attackStrength"));
        assertTrue(recovery.contains(
                "stats.getWeaponType()"
        ));
        assertTrue(recovery.contains(
                "stats.getRawSpeed()"
        ));
        assertTrue(recovery.contains(
                "getWeaponSpeedMultiplier()"
        ));
    }

    private static String source(String fileName) throws IOException {
        return mainSource("combat/" + fileName);
    }

    private static String mainSource(String relative) throws IOException {
        Path project = Path.of(System.getProperty(
                "stardewcraft.projectDir",
                "."
        ));
        return Files.readString(project.resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                relative
        )));
    }
}

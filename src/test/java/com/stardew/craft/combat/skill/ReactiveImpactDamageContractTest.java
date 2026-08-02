package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveImpactDamageContractTest {
    @Test
    void silverFoldbackRechecksPermissionWithoutChangingStrikeOrder()
            throws IOException {
        String source = normalizedSource(
                "combat/skill/SilverSaberSkillHelper.java"
        );
        int snapshotRead = source.indexOf(
                "SilverSaberFoldbackState.getWeaponSnapshot(player)"
        );
        int stayStateExit = source.indexOf(
                "exitFoldbackState(player)",
                snapshotRead
        );
        int stayStrike = source.indexOf(
                "attackWithSkillContext(",
                stayStateExit
        );
        int stayCooldown = source.indexOf(
                "sendCooldownAnimation(",
                stayStrike
        );
        int returnMethod = source.indexOf(
                "public static void executeReturnStrike( Player player, "
                        + "LivingEntity target, Vec3 origin, String weaponId, "
                        + "WeaponSkillData skill, long nowTick, "
                        + "TeleportFunction teleportFunc, "
                        + "WeaponDamageSnapshot weaponSnapshot"
        );
        int returnStateExit = source.indexOf(
                "exitFoldbackState(player)",
                returnMethod
        );
        int returnStrike = source.indexOf(
                "attackWithSkillContext(",
                returnStateExit
        );
        int returnTeleport = source.indexOf(
                "teleportFunc.teleport(player, origin)",
                returnStrike
        );
        int cooldown = source.indexOf(
                "sendCooldownAnimation(",
                returnTeleport
        );

        assertTrue(
                snapshotRead >= 0
                        && stayStateExit > snapshotRead
                        && stayStrike > stayStateExit
                        && stayCooldown > stayStrike
                        && returnMethod >= 0
                        && returnStateExit > returnMethod
                        && returnStrike > returnStateExit
                        && returnTeleport > returnStrike
                        && cooldown > returnTeleport
        );
        assertCompatibilityAndExplicitSnapshotPaths(source);
        assertImpactGatedOnly(source);
    }

    @Test
    void lightCounterConsumesStateThenRetaliatesAgainstTheAttacker()
            throws IOException {
        String source = normalizedSource(
                "combat/skill/LightCounterParryHandler.java"
        );
        int stateConsume = source.indexOf(
                "LightCounterSkillHandler.consumeParry("
        );
        int snapshotRead = source.indexOf(
                "activation.weaponSnapshot()",
                stateConsume
        );
        int mitigation = source.indexOf(
                "event.setAmount(event.getAmount() * 0.4f)",
                snapshotRead
        );
        int attacker = source.indexOf(
                "src instanceof LivingEntity attacker",
                mitigation
        );
        int counter = source.indexOf(
                "WeaponSkillDamage.apply(",
                attacker
        );
        int animation = source.indexOf(
                "WeaponSkillAnimationDispatcher.sendCounterAnim(",
                counter
        );

        assertTrue(
                stateConsume >= 0
                        && snapshotRead > stateConsume
                        && mitigation > snapshotRead
                        && attacker > mitigation
                        && counter > attacker
                        && animation > counter
        );
        assertTrue(source.contains(
                "player, attacker, context,"
        ));
        assertCompatibilityAndExplicitSnapshotPaths(source);
        assertImpactGatedOnly(source);
    }

    @Test
    void templarVowThreadsOneReleaseSnapshotThroughBothOutcomes()
            throws IOException {
        String runtime = normalizedSource(
                "combat/skill/handler/TemplarVowSkillHandler.java"
        );
        String handler = normalizedSource(
                "combat/skill/TemplarVowHandler.java"
        );
        String state = normalizedSource(
                "combat/skill/handler/TemplarVowExecutionState.java"
        );

        assertTrue(runtime.contains(
                "context.weaponSnapshot(), cooldown"
        ));
        assertTrue(state.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertTrue(handler.contains(
                "WeaponDamageSnapshot weaponSnapshot = "
                        + "activation.weaponSnapshot();"
        ));
        assertTrue(state.contains(
                "applyExpirySlash(context)"
        ));

        int negateDamage = handler.indexOf("event.setAmount(0.0f)");
        int swing = handler.indexOf(
                "player.swing(InteractionHand.MAIN_HAND, true)",
                negateDamage
        );
        int counter = handler.indexOf(
                "WeaponSkillDamage.apply(",
                swing
        );
        int endVow = handler.indexOf(
                "TemplarVowSkillHandler.finishCounter(player, nowTick)",
                counter
        );
        assertTrue(
                negateDamage >= 0
                        && swing > negateDamage
                        && counter > swing
                        && endVow > counter
        );

        int shelter = state.indexOf("player.addEffect(");
        int expiryTarget = state.indexOf(
                "findTargetEntity( player, "
                        + "TemplarVowSkillHandler.COUNTER_TARGET_RANGE )",
                shelter
        );
        int expirySlash = state.indexOf(
                "WeaponSkillDamage.apply(",
                expiryTarget
        );
        assertTrue(
                shelter >= 0
                        && expiryTarget > shelter
                        && expirySlash > expiryTarget
        );
        assertCompatibilityAndExplicitSnapshotPaths(handler);
        assertCompatibilityAndExplicitSnapshotPaths(state);
        assertImpactGatedOnly(handler);
        assertImpactGatedOnly(state);
    }

    private static void assertCompatibilityAndExplicitSnapshotPaths(
            String source
    ) {
        assertTrue(source.contains("if (weaponSnapshot == null)"));
        assertTrue(source.contains(
                "WeaponSkillDamage.apply("
        ));
        assertTrue(source.contains("weaponSnapshot,"));
    }

    private static void assertImpactGatedOnly(String source) {
        assertTrue(source.contains(
                "WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT"
        ));
        assertFalse(source.contains(
                "WeaponSkillContextStore.setPending("
        ));
        assertFalse(source.contains("player.attack("));
        assertFalse(source.contains(
                "if (WeaponSkillDamage.apply("
        ));
        assertFalse(source.contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }

    private static String normalizedSource(String relativeFile)
            throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativeFile);
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

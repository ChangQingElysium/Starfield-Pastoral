package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoredMultiHitDamageContractTest {
    @Test
    void infinityBackstabKeepsItsSharedHelperAndSecondHitGuards()
            throws IOException {
        String source = normalizedHandler(
                "InfinityDaggerSingularityBackstabSkillHandler.java"
        );
        int safeTeleport = source.indexOf(
                "teleportPlayer(context.player(), plan.destination());"
        );
        int firstHit = source.indexOf(
                "attack( context, target, createHitContext("
        );
        int continuationGuard = source.indexOf(
                "if (!canContinueCast(",
                firstHit
        );
        int targetAliveGuard = source.indexOf(
                "if (shouldStrikeSecond(target.isAlive()))",
                continuationGuard
        );
        int secondHit = source.indexOf(
                "attack( context, target, createHitContext(",
                firstHit + 1
        );
        int bypass = source.indexOf(
                ".BYPASS_FOR_AUTHORED_SEQUENCE",
                secondHit
        );
        int controlGate = source.indexOf(
                "if (executionState.settleControl())",
                secondHit
        );
        int control = source.indexOf(
                "YetiFreezeTracker.applyWithEquipmentProtection(",
                controlGate
        );

        assertTrue(
                safeTeleport >= 0
                        && firstHit > safeTeleport
                        && continuationGuard > firstHit
                        && targetAliveGuard > continuationGuard
                        && secondHit > targetAliveGuard
                        && bypass > secondHit
                        && controlGate > bypass
                        && control > controlGate
        );
        assertEquals(2, occurrences(
                source,
                "attack( context, target, createHitContext("
        ));
        assertEquals(1, occurrences(
                source,
                "WeaponSkillDamage.apply("
        ));
        assertTrue(source.contains(
                "WeaponSkillDamage.apply( context.player(), target, "
                        + "hitContext, context.weaponSnapshot(), "
        ));
        assertTrue(source.contains(".RESPECT_VANILLA"));
        assertFalse(source.contains("target.invulnerableTime = 0;"));
        assertFalse(source.contains("target.hurtTime = 0;"));
        assertCentralizedOnly(source);
    }

    @Test
    void shadowExecuteKeepsTheLockedTwoHitSequence()
            throws IOException {
        String source = normalizedHandler(
                "ShadowDaggerExecuteSkillHandler.java"
        );
        int executeLock = source.indexOf(
                "boolean execute = target.getHealth()"
        );
        int firstHit = source.indexOf(
                "WeaponSkillDamage.apply(",
                executeLock
        );
        int animation = source.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                firstHit
        );
        int appliedRoot = source.indexOf(
                "public static boolean onAppliedRootHit(",
                animation
        );
        int secondHit = source.indexOf(
                "WeaponSkillDamage.apply(",
                appliedRoot
        );
        int bypass = source.indexOf(
                ".BYPASS_FOR_AUTHORED_SEQUENCE",
                secondHit
        );

        assertTrue(
                executeLock >= 0
                        && firstHit > executeLock
                        && animation > firstHit
                        && appliedRoot > animation
                        && secondHit > appliedRoot
                        && bypass > secondHit
        );
        assertEquals(2, occurrences(
                source,
                "WeaponSkillDamage.apply("
        ));
        assertTrue(source.contains("context.weaponSnapshot()"));
        assertTrue(source.contains(
                "createExecuteBonusContext(), "
                        + "weaponSnapshot, "
        ));
        assertFalse(source.contains("target.isAlive()"));
        assertFalse(source.contains("target.invulnerableTime = 0;"));
        assertFalse(source.contains("target.hurtTime = 0;"));
        assertCentralizedOnly(source);
    }

    private static void assertCentralizedOnly(String source) {
        assertFalse(source.contains(
                "WeaponSkillContextStore.setPending("
        ));
        assertFalse(source.contains("context.player().attack("));
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

    private static String normalizedHandler(String fileName)
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
                "handler",
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

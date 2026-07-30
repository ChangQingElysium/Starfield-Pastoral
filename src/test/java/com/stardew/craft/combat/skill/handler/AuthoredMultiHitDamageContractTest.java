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
        int clearInvulnerability = source.indexOf(
                "target.invulnerableTime = 0;",
                targetAliveGuard
        );
        int clearHurtTime = source.indexOf(
                "target.hurtTime = 0;",
                clearInvulnerability
        );
        int secondHit = source.indexOf(
                "attack( context, target, createHitContext(",
                firstHit + 1
        );

        assertTrue(
                safeTeleport >= 0
                        && firstHit > safeTeleport
                        && continuationGuard > firstHit
                        && targetAliveGuard > continuationGuard
                        && clearInvulnerability > targetAliveGuard
                        && clearHurtTime > clearInvulnerability
                        && secondHit > clearHurtTime
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
        int executeBranch = source.indexOf("if (execute) {", firstHit);
        int clearInvulnerability = source.indexOf(
                "target.invulnerableTime = 0;",
                executeBranch
        );
        int clearHurtTime = source.indexOf(
                "target.hurtTime = 0;",
                clearInvulnerability
        );
        int secondHit = source.indexOf(
                "WeaponSkillDamage.apply(",
                firstHit + 1
        );
        int animation = source.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                secondHit
        );

        assertTrue(
                executeLock >= 0
                        && firstHit > executeLock
                        && executeBranch > firstHit
                        && clearInvulnerability > executeBranch
                        && clearHurtTime > clearInvulnerability
                        && secondHit > clearHurtTime
                        && animation > secondHit
        );
        assertEquals(2, occurrences(
                source,
                "WeaponSkillDamage.apply("
        ));
        assertEquals(2, occurrences(
                source,
                "context.weaponSnapshot()"
        ));
        assertTrue(source.contains(
                "createExecuteBonusContext(), "
                        + "context.weaponSnapshot(), "
        ));
        assertFalse(source.contains("target.isAlive()"));
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

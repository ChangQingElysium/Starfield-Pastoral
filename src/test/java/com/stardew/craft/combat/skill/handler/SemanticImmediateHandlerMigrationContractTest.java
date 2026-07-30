package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticImmediateHandlerMigrationContractTest {
    @Test
    void desperatePlunderBranchesOnPostAttemptTargetState()
            throws IOException {
        String source = readHandler(
                "DesperatePlunderSkillHandler.java"
        );
        String begin = method(source, "public void begin(");

        int healthCost = begin.indexOf(
                "setHealth(healthAfterCost("
        );
        int damageAttempt = begin.indexOf(
                "WeaponSkillDamage.apply("
        );
        int deathCheck = begin.indexOf(
                "if (target.isDeadOrDying()"
        );
        int healing = begin.indexOf(
                "healAfterKill(context);",
                deathCheck
        );
        int fury = begin.indexOf(
                "grantFury(context);",
                deathCheck
        );

        assertTrue(healthCost >= 0 && damageAttempt > healthCost);
        assertTrue(deathCheck > damageAttempt);
        assertTrue(healing > deathCheck && fury > deathCheck);
        assertTrue(begin.contains("context.weaponSnapshot()"));
        assertFalse(begin.contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
        assertFalse(begin.contains(
                "if (WeaponSkillDamage.apply("
        ));
        assertCentralizedOnly(source);
    }

    @Test
    void nestBurstPoisonsAfterEveryDamageAttempt()
            throws IOException {
        String source = readHandler(
                "WickedKrisNestBurstSkillHandler.java"
        );
        String begin = method(source, "public void begin(");

        int damageAttempt = begin.indexOf(
                "WeaponSkillDamage.apply("
        );
        int poison = begin.indexOf(
                "WickedKrisPoisonTracker.applyPoison("
        );
        assertTrue(damageAttempt >= 0 && poison > damageAttempt);
        assertFalse(begin.substring(damageAttempt, poison).contains(
                "if ("
        ));
        assertTrue(begin.substring(damageAttempt, poison).contains(
                "context.weaponSnapshot()"
        ));
        assertCentralizedOnly(source);
    }

    @Test
    void venomRippleClearsIFramesThenAttemptsAndPoisonsEveryTarget()
            throws IOException {
        String source = readHandler(
                "WickedKrisVenomRippleSkillHandler.java"
        );
        String begin = method(source, "public void begin(");

        int loop = begin.indexOf(
                "for (LivingEntity target : targets)"
        );
        int invulnerable = begin.indexOf(
                "target.invulnerableTime = 0;",
                loop
        );
        int hurtTime = begin.indexOf(
                "target.hurtTime = 0;",
                invulnerable
        );
        int damageAttempt = begin.indexOf(
                "WeaponSkillDamage.apply(",
                hurtTime
        );
        int poison = begin.indexOf(
                "WickedKrisPoisonTracker.applyPoison(",
                damageAttempt
        );

        assertTrue(loop >= 0 && invulnerable > loop);
        assertTrue(hurtTime > invulnerable);
        assertTrue(damageAttempt > hurtTime && poison > damageAttempt);
        assertFalse(begin.substring(damageAttempt, poison).contains(
                "if ("
        ));
        assertTrue(begin.substring(damageAttempt, poison).contains(
                "context.weaponSnapshot()"
        ));
        assertCentralizedOnly(source);
    }

    private static void assertCentralizedOnly(String source) {
        assertFalse(source.contains(
                "WeaponSkillContextStore.setPending("
        ));
        assertFalse(source.contains("context.player().attack("));
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

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
                "WeaponSkillRuntime.spendHealthDuringBegin("
        );
        int committed = begin.indexOf(
                "instance.registerCommittedEffect(",
                healthCost
        );
        int damageAttempt = begin.indexOf(
                "WeaponSkillDamage.apply("
        );
        int settlement = begin.indexOf(
                "executionState.settleKilledByThisHit()"
        );
        int healing = begin.indexOf(
                "healAfterKill(context);",
                settlement
        );
        int fury = begin.indexOf(
                "grantFury(context);",
                settlement
        );

        assertTrue(healthCost >= 0 && committed > healthCost);
        assertTrue(damageAttempt > committed);
        assertTrue(settlement > damageAttempt);
        assertTrue(healing > settlement && fury > settlement);
        assertFalse(begin.contains("target.isDeadOrDying()"));
        assertFalse(begin.contains("target.getHealth()"));
        assertTrue(begin.contains("context.weaponSnapshot()"));
        assertFalse(begin.contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
        assertFalse(begin.contains(
                "if (WeaponSkillDamage.apply("
        ));
        assertFalse(begin.contains(
                "context.player().setHealth("
        ));
        assertTrue(source.contains(
                "CombatHealing.heal(context.player(), KILL_HEALING);"
        ));
        assertCentralizedOnly(source);
    }

    @Test
    void nestBurstPoisonsOnlyFromPositiveAppliedPost()
            throws IOException {
        String source = readHandler(
                "WickedKrisNestBurstSkillHandler.java"
        );
        String rules = readMain(
                "combat/BuiltinSkillAppliedHitRules.java"
        );
        String coordinator = readMain(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        String begin = method(source, "public void begin(");
        String applied = method(
                rules,
                "static void applyWickedNestBurst("
        );

        int damageAttempt = begin.indexOf(
                "WeaponSkillDamage.apply("
        );
        assertTrue(damageAttempt >= 0);
        assertTrue(begin.contains("context.weaponSnapshot()"));
        assertTrue(begin.contains(
                "WeaponSkillDamage.AttackGatePolicy"
        ));
        assertTrue(begin.contains(".RESPECT_AT_IMPACT"));
        assertFalse(begin.contains(
                "WickedKrisPoisonTracker.applyPoison("
        ));

        assertTrue(applied.contains(
                "\"wicked_kris_nest_burst\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "WickedKrisPoisonTracker.applyPoison("
        ));
        assertTrue(applied.contains(
                "hit.weaponSnapshot().orElseThrow()"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyWickedNestBurst(hit)"
        ));
        assertCentralizedOnly(source);
    }

    @Test
    void venomRipplePoisonsPerPositiveHitAndRewardsTheCastOnce()
            throws IOException {
        String source = readHandler(
                "WickedKrisVenomRippleSkillHandler.java"
        );
        String rules = readMain(
                "combat/BuiltinSkillAppliedHitRules.java"
        );
        String coordinator = readMain(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        String begin = method(source, "public void begin(");
        String applied = method(
                rules,
                "static void applyWickedVenomRipple("
        );

        int loop = begin.indexOf(
                "for (LivingEntity target : targets)"
        );
        int damageAttempt = begin.indexOf(
                "WeaponSkillDamage.apply(",
                loop
        );
        int bypass = begin.indexOf(
                ".BYPASS_FOR_AUTHORED_SEQUENCE",
                damageAttempt
        );

        assertTrue(loop >= 0 && damageAttempt > loop);
        assertTrue(bypass > damageAttempt);
        assertFalse(begin.contains("target.invulnerableTime = 0;"));
        assertFalse(begin.contains("target.hurtTime = 0;"));
        assertTrue(begin.contains("context.weaponSnapshot()"));
        assertTrue(begin.contains(
                "instance.initializeExecutionState(new State())"
        ));
        assertFalse(begin.contains(
                "WickedKrisPoisonTracker.applyPoison("
        ));
        assertFalse(begin.contains("player.addEffect("));

        assertTrue(applied.contains(
                "\"wicked_kris_venom_ripple\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "WickedKrisPoisonTracker.applyPoison("
        ));
        assertTrue(applied.contains(
                "hit.weaponSnapshot().orElseThrow()"
        ));
        assertTrue(applied.contains(
                "WickedKrisVenomRippleSkillHandler.recordAppliedHit(player)"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyWickedVenomRipple(hit)"
        ));

        String reward = method(
                source,
                "public static boolean recordAppliedHit("
        );
        assertTrue(reward.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(reward.contains(
                "BuiltinWeaponSkillHandlers.WICKED_KRIS_VENOM_RIPPLE"
        ));
        assertTrue(source.contains("private boolean speedGranted;"));
        assertTrue(source.contains("if (speedGranted)"));
        assertTrue(source.contains("speedGranted = true;"));
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
        return readMain(Path.of(
                "combat", "skill", "handler", fileName
        ).toString());
    }

    private static String readMain(String relativeFile) throws IOException {
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
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}

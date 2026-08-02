package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateTargetRewardAppliedPostContractTest {
    @Test
    void dragonJudgementRefundsOnlyUniquePositiveAppliedTargets()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        String handler = source(
                "combat/skill/handler/DragonBreathJudgementSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/DragonBreathJudgementExecutionState.java"
        );

        String applied = method(
                rules,
                "static void recordDragonBreathJudgement("
        );
        assertTrue(applied.contains(
                "\"dragon_breath_judgement\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "DragonBreathJudgementSkillHandler.recordAppliedHit("
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules."
                        + "recordDragonBreathJudgement(hit)"
        ));

        String begin = method(handler, "public void begin(");
        assertTrue(begin.contains(
                "new DragonBreathJudgementExecutionState()"
        ));
        assertTrue(begin.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertTrue(begin.contains("executionState.settleRefund()"));
        assertFalse(begin.contains("refundForTargetCount(targets.size())"));
        assertTrue(handler.contains(
                "WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT"
        ));
        assertTrue(handler.contains(
                "WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA"
        ));
        assertOrdered(
                begin,
                "for (LivingEntity target : targets)",
                "attackTarget(context, target, criticalChanceBonus)",
                "executionState.settleRefund()",
                "DragonBreathTracker.addStacks("
        );

        String facade = method(
                handler,
                "public static boolean recordAppliedHit("
        );
        assertTrue(facade.contains(
                "BuiltinWeaponSkillHandlers.DRAGON_BREATH_JUDGEMENT"
        ));
        assertTrue(facade.contains(
                "DragonBreathJudgementExecutionState.class"
        ));
        assertTrue(state.contains("Set<UUID> appliedTargets"));
        assertTrue(state.contains("appliedTargets.add(targetId)"));
        assertTrue(state.contains("appliedTargets.size()"));
    }

    @Test
    void singularityRewardsOnlyItsFirstPositiveAppliedHit()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        String handler = source(
                "combat/skill/handler/SingularityEvolveSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/SingularityEvolveExecutionState.java"
        );

        String applied = method(
                rules,
                "static void settleSingularityEvolveRewards("
        );
        assertTrue(applied.contains(
                "\"singularity_evolve\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "SingularityEvolveSkillHandler."
                        + "settleAppliedHitRewards(player)"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules."
                        + "settleSingularityEvolveRewards(hit)"
        ));

        String begin = method(handler, "public void begin(");
        assertTrue(begin.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertFalse(begin.contains("rewardTargets"));
        assertFalse(handler.contains("findRewardTargets("));
        assertFalse(handler.contains("grantsHitRewards("));

        String facade = method(
                handler,
                "public static boolean settleAppliedHitRewards("
        );
        assertTrue(facade.contains(
                "BuiltinWeaponSkillHandlers.SINGULARITY_EVOLVE"
        ));
        assertTrue(facade.contains("SingularityEvolveExecutionState.class"));
        assertTrue(facade.contains("HIT_SINGULARITY_RESTORE"));
        assertTrue(facade.contains("HIT_ENERGY_RESTORE"));
        assertTrue(facade.contains("HIT_HEALTH_RESTORE"));
        assertTrue(state.contains("SingularityEvolveRewardState"));
        assertTrue(state.contains("return rewardState.claim()"));
        assertTrue(state.contains("if (claimed)"));
        assertTrue(state.contains(
                "WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE"
        ));
        assertTrue(state.contains(
                "WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT"
        ));
        assertTrue(state.contains(
                ".BYPASS_FOR_AUTHORED_SEQUENCE"
        ));
        assertTrue(state.contains("\"singularity_rift_path\""));
    }

    @Test
    void startrailRiftRewardsOnlyItsFirstOwnedPositiveAppliedHit()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        String handler = source(
                "combat/skill/handler/StartrailRiftSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/StartrailRiftExecutionState.java"
        );

        String applied = method(
                rules,
                "static void settleStartrailRiftRewards("
        );
        assertTrue(applied.contains(
                "\"startrail_rift\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "StartrailRiftSkillHandler.settleAppliedHitRewards("
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules."
                        + "settleStartrailRiftRewards(hit)"
        ));

        String begin = method(handler, "public void begin(");
        assertTrue(begin.contains(
                "new StartrailRiftExecutionState("
        ));
        assertTrue(begin.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertFalse(begin.contains("if (!targets.isEmpty())"));
        assertFalse(begin.contains("HIT_STARTRAIL_RESTORE"));
        assertFalse(begin.contains("HIT_ENERGY_RESTORE"));
        assertFalse(begin.contains("HIT_HEALTH_RESTORE"));
        assertTrue(handler.contains(
                "WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT"
        ));
        assertTrue(handler.contains(
                "WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA"
        ));

        String facade = method(
                handler,
                "public static boolean settleAppliedHitRewards("
        );
        assertTrue(facade.contains(
                "BuiltinWeaponSkillHandlers.STARTRAIL_RIFT"
        ));
        assertTrue(facade.contains("StartrailRiftExecutionState.class"));
        assertTrue(facade.contains("HIT_STARTRAIL_RESTORE"));
        assertTrue(facade.contains("HIT_ENERGY_RESTORE"));
        assertTrue(facade.contains("HIT_HEALTH_RESTORE"));
        assertTrue(state.contains("Set<UUID> eligibleTargetIds"));
        assertTrue(state.contains("boolean rewardsClaimed"));
        assertTrue(state.contains(
                "eligibleTargetIds.contains(targetId)"
        ));
    }

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + needle);
            previous = current;
        }
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        assertTrue(openingBrace > start, "Missing body " + signature);

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

    private static String source(String relativeFile) throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft"
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

package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponPassivePhaseOwnershipContractTest {
    @Test
    void iridiumCommitsOnlyFromTheExactAppliedHitFrame()
            throws IOException {
        String appliedRules = source(
                "BuiltinWeaponPassiveAppliedHitRules.java"
        );
        String evaluatedCoordinator = source(
                "WeaponEvaluatedHitCoordinator.java"
        );
        String appliedCoordinator = source(
                "WeaponAppliedHitCoordinator.java"
        );

        assertFalse(evaluatedCoordinator.contains("applyIridiumNeedle(hit)"));
        assertTrue(appliedCoordinator.contains(
                "BuiltinWeaponPassiveAppliedHitRules"
                        + ".applyIridiumNeedle(hit)"
        ));

        String method = method(
                appliedRules,
                "static void applyIridiumNeedle(ResolvedWeaponHit hit)"
        );
        assertTrue(method.contains("hit.dealtPositiveDamage()"));
        assertTrue(method.contains("hit.weaponIdentity().builtIn()"));
        assertTrue(method.contains("hit.weaponIdentity().logicId()"));
        assertTrue(method.contains("hit.damageOutcome().isCrit()"));
        assertTrue(method.contains("hit.gameTick()"));
        assertTrue(method.contains("IridiumNeedleCritTracker.recordHit("));
        assertFalse(method.contains("hit.displayCritical()"));
        assertFalse(method.contains("getMainHandItem()"));
    }

    @Test
    void everyPersistentResourceNowCommitsFromAppliedPost()
            throws IOException {
        String appliedRules = source(
                "BuiltinWeaponPassiveAppliedHitRules.java"
        );

        String applied = method(
                appliedRules,
                "static void addAppliedWeaponResources(ResolvedWeaponHit hit)"
        );
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains("hit.weaponIdentity().builtIn()"));
        assertTrue(applied.contains("hit.weaponIdentity().logicId()"));
        assertTrue(applied.contains(
                "hit.authoredSkillContext().getSkillId()"
        ));
        assertTrue(applied.contains("\"dragontooth_cutlass\""));
        assertTrue(applied.contains("DragonBreathTracker.addStacks("));
        assertTrue(applied.contains("\"galaxy_sword\""));
        assertTrue(applied.contains("StartrailTracker.addStacks("));
        assertTrue(applied.contains("hit.damageOutcome().isCrit()"));
        assertFalse(applied.contains("hit.displayCritical()"));
        assertFalse(applied.contains("SingularityTracker"));
        assertFalse(applied.contains("CrystalDaggerLayerTracker"));
        assertFalse(applied.contains("getMainHandItem()"));

        String evaluated = source("WeaponEvaluatedHitCoordinator.java");
        for (String forbidden : new String[]{
                "ObsidianResonanceTracker",
                "CrystalDaggerLayerTracker",
                "SingularityTracker",
                "WeaponSkillDamage",
                "BuiltinWeaponPassive"
        }) {
            assertFalse(evaluated.contains(forbidden), forbidden);
        }
    }

    @Test
    void synchronousChildrenRunAfterParentRewardsInStableOrder()
            throws IOException {
        String applied = source("WeaponAppliedHitCoordinator.java");
        assertIncreasing(
                applied,
                "BuiltinSkillAppliedHitRules.applyDarkSwordLifeSteal(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyIridiumNeedle(hit)",
                "BuiltinWeaponPassiveAppliedHitRules"
                        + ".addAppliedWeaponResources(hit)",
                "CommonWeaponAppliedHitRules.applyKillRewards(hit)",
                "BuiltinWeaponPassiveAppliedHitRules"
                        + ".consumeObsidianResonance(hit)",
                "BuiltinWeaponPassiveAppliedHitRules"
                        + ".triggerCrystalBurst(hit)",
                "triggerEvolvedSingularityFollowup(hit)",
                "BuiltinWeaponPassiveAppliedHitRules"
                        + ".applyOssifiedCriticalMark(hit)"
        );

        String evaluated = source("WeaponEvaluatedHitCoordinator.java");
        assertTrue(evaluated.contains(
                "BuiltinSkillEvaluatedHitRules.emitSteelSpineStrike(hit)"
        ));
        assertFalse(evaluated.contains(
                "BuiltinWeaponPassiveEvaluatedHitRules"
        ));
    }

    @Test
    void obsidianConsumesFatalChargeButStrikesOnlyALivingTarget()
            throws IOException {
        String rules = source("BuiltinWeaponPassiveAppliedHitRules.java");
        String method = method(
                rules,
                "static void consumeObsidianResonance(ResolvedWeaponHit hit)"
        );

        assertTrue(method.contains("hit.dealtPositiveDamage()"));
        assertTrue(method.contains("hit.weaponIdentity().builtIn()"));
        assertTrue(method.contains("hit.weaponIdentity().logicId()"));
        assertTrue(method.contains(
                "hit.authoredSkillContext().getSkillId()"
        ));
        assertIncreasing(
                method,
                "ObsidianResonanceTracker.consumeCharge(",
                "hit.target().isAlive()",
                "applyChildDamage(",
                "ObsidianResonanceTracker.createBonusContext("
        );
        assertFalse(method.contains(
                "LegacyWeaponHitPresentation.emitObsidianResonance("
        ));
        String presentation = method(
                rules,
                "static void emitObsidianResonancePresentation("
                        + "ResolvedWeaponHit hit)"
        );
        assertIncreasing(
                presentation,
                "\"obsidian_resonance\".equals(hit.skillId())",
                "hit.dealtPositiveDamage()",
                "LegacyWeaponHitPresentation.emitObsidianResonance("
        );
        assertTrue(rules.contains(
                "WeaponDamageSnapshot snapshot = "
                        + "hit.weaponSnapshot().orElseThrow()"
        ));
        assertFalse(method.contains("getMainHandItem()"));
    }

    @Test
    void crystalConsumesFatalReadinessBeforeGatingItsChildAndPayload()
            throws IOException {
        String rules = source("BuiltinWeaponPassiveAppliedHitRules.java");
        String method = method(
                rules,
                "static void triggerCrystalBurst(ResolvedWeaponHit hit)"
        );

        assertTrue(method.contains("hit.dealtPositiveDamage()"));
        assertTrue(method.contains("hit.weaponIdentity().builtIn()"));
        assertTrue(method.contains("\"crystal_dagger\""));
        assertTrue(method.contains(
                "hit.authoredSkillContext().getSkillId()"
        ));
        assertIncreasing(
                method,
                "CrystalDaggerLayerTracker.addStack(",
                "CrystalDaggerLayerTracker.shouldBurst(",
                "CrystalDaggerLayerTracker.consumeBurst(",
                "hit.target().isAlive()",
                "applyChildDamage(",
                ".skillId(\"crystal_dagger_burst\")"
        );
        assertFalse(method.contains("new CrystalDaggerBurstPayload()"));
        String presentation = method(
                rules,
                "static void emitCrystalDaggerBurstPresentation("
                        + "ResolvedWeaponHit hit)"
        );
        assertIncreasing(
                presentation,
                "\"crystal_dagger_burst\".equals(hit.skillId())",
                "hit.dealtPositiveDamage()",
                "hit.attacker() instanceof ServerPlayer player",
                "new CrystalDaggerBurstPayload()"
        );
        assertTrue(rules.contains(
                "WeaponDamageSnapshot snapshot = "
                        + "hit.weaponSnapshot().orElseThrow()"
        ));
        assertTrue(rules.contains("private static void applyChildDamage("));
        assertTrue(rules.contains("WeaponSkillDamage.apply("));
        assertFalse(rules.contains("return WeaponSkillDamage.apply("));
        assertFalse(method.contains("getMainHandItem()"));
    }

    @Test
    void infinityAlwaysAddsBeforeNormalEvolvedLivingFollowupGate()
            throws IOException {
        String rules = source("BuiltinWeaponPassiveAppliedHitRules.java");
        String method = method(
                rules,
                "static void triggerEvolvedSingularityFollowup("
                        + "ResolvedWeaponHit hit)"
        );

        assertTrue(method.contains("hit.dealtPositiveDamage()"));
        assertTrue(method.contains("hit.weaponIdentity().builtIn()"));
        assertTrue(method.contains("\"infinity_blade\""));
        assertTrue(method.contains("hit.damageOutcome().isCrit()"));
        assertIncreasing(
                method,
                "SingularityTracker.addStacks(",
                "hit.authoredSkillContext().getSkillId()",
                "SingularityTracker.isEvolved(",
                "hit.target().isAlive()",
                "applyChildDamage(",
                ".skillId(\"singularity_followup\")"
        );
        assertTrue(rules.contains(
                "WeaponDamageSnapshot snapshot = "
                        + "hit.weaponSnapshot().orElseThrow()"
        ));
        assertFalse(method.contains("getMainHandItem()"));
    }

    @Test
    void ossifiedMarkConsumesGameplayCriticalNotPresentationFlag()
            throws IOException {
        String rules = source("BuiltinWeaponPassiveAppliedHitRules.java");
        String method = method(
                rules,
                "static void applyOssifiedCriticalMark("
                        + "ResolvedWeaponHit hit)"
        );

        assertTrue(method.contains("hit.damageOutcome().isCrit()"));
        assertFalse(method.contains("hit.displayCritical()"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int next = source.indexOf("\n    static void ", start + signature.length());
        int end = next >= 0 ? next : source.lastIndexOf('}');
        assertTrue(end > start, signature);
        return source.substring(start, end);
    }

    private static void assertIncreasing(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = source.indexOf(token, previous + 1);
            assertTrue(current > previous, token);
            previous = current;
        }
    }

    private static String source(String fileName) throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "combat", fileName
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

package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponEvaluatedHitCoordinatorContractTest {
    @Test
    void incomingEvaluatesAndPreCommitsTheExactStagedResult()
            throws IOException {
        String events = source("WeaponCombatEvents.java");
        String incoming = method(
                events,
                "public static void onLivingIncomingDamage(",
                "public static void onLivingIncomingDamageFinal("
        );
        String resolver = method(
                events,
                "private static IncomingWeaponResolution evaluateWeaponHit(",
                "public static void onLivingHurt(LivingDamageEvent.Pre event)"
        );
        String pre = method(
                events,
                "public static void onLivingHurt(LivingDamageEvent.Pre event)",
                "public static CustomHealthWeaponResolution "
                        + "evaluateCustomHealthWeaponHit("
        );

        assertIncreasing(incoming, List.of(
                "IncomingWeaponResolution resolution = evaluateWeaponHit(",
                "event.setAmount(resolution.authoritativeDamage())",
                "WeaponIncomingHitStore.bind(",
                "event.getContainer(),"
        ));
        assertIncreasing(resolver, List.of(
                "WeaponHitPreparation preparation =",
                "WeaponDamageAssemblyRules.apply(",
                "DamagePipeline.evaluate(damageRequest.build())",
                "EvaluatedWeaponHit hit = new EvaluatedWeaponHit("
        ));
        assertIncreasing(pre, List.of(
                "hit = WeaponIncomingHitStore.consume(",
                "event.getContainer(),",
                "hit.preparationReservation().commit();",
                "WeaponEvaluatedHitCoordinator.apply(hit);"
        ));
        assertFalse(pre.contains(
                "DamagePipeline.evaluate(damageRequest.build())"
        ));
        assertFalse(pre.contains("new EvaluatedWeaponHit("));
        assertFalse(incoming.contains(
                "WeaponEvaluatedHitCoordinator.apply("
        ));
        assertFalse(incoming.contains("DamageNumberContextStore.bind("));
        assertFalse(pre.contains("DamageNumberContextStore.bind("));
        assertFalse(pre.contains("WeaponSkillDamage.apply("));
        assertFalse(resolver.contains("event.getOriginalDamage()"));
        assertFalse(resolver.contains("nativeProtectionRatio("));

        assertTrue(events.contains(
                "@SubscribeEvent(priority = EventPriority.HIGHEST)"
        ));
        assertTrue(events.contains(
                "@SubscribeEvent(priority = EventPriority.LOWEST)"
        ));

        String evaluated = source("EvaluatedWeaponHit.java");
        assertTrue(evaluated.contains(
                "WeaponHitPreparation.Reservation preparationReservation"
        ));
        assertTrue(evaluated.contains(
                "WeaponDamageSnapshot weaponSnapshot"
        ));
        assertTrue(evaluated.contains("DamageOutcome outcome"));
        assertTrue(evaluated.contains("boolean sweepTarget"));
        assertTrue(evaluated.contains("boolean inStardewDimension"));

        String store = source("WeaponIncomingHitStore.java");
        assertTrue(store.contains(
                "IdentityHashMap<DamageContainer, BoundHit> ACTIVE"
        ));
        assertTrue(store.contains("ACTIVE.remove(container)"));
    }

    @Test
    void evaluatedRuleOrderRemainsExplicitIncludingNestedDamageBoundary()
            throws IOException {
        String coordinator = source("WeaponEvaluatedHitCoordinator.java");
        assertIncreasing(coordinator, List.of(
                "BuiltinSkillEvaluatedHitRules.emitSteelSpineStrike(hit)",
                "CommonWeaponEvaluatedHitRules.bindAppliedHitFrame(hit)"
        ));
        assertFalse(coordinator.contains(
                "BuiltinWeaponPassiveEvaluatedHitRules"
        ));
        assertFalse(coordinator.contains("WeaponSkillDamage.apply("));
        assertFalse(coordinator.contains("\"holy_smite\""));
        assertFalse(coordinator.contains("getWeaponId()"));

        String evaluatedSkills = source(
                "BuiltinSkillEvaluatedHitRules.java"
        );
        assertFalse(evaluatedSkills.contains("applyHolySmite("));
        assertFalse(evaluatedSkills.contains("armTemperedQuench("));
        assertFalse(evaluatedSkills.contains("applyDarkSwordLifeSteal("));
        assertFalse(evaluatedSkills.contains("fireElfLeaf("));

        String appliedCoordinator = source(
                "WeaponAppliedHitCoordinator.java"
        );
        assertIncreasing(appliedCoordinator, List.of(
                "BuiltinSkillAppliedHitRules.applyDarkSwordLifeSteal(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyIridiumNeedle(hit)",
                "BuiltinSkillAppliedHitRules.fireElfLeaf(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.addAppliedWeaponResources(hit)",
                "CommonWeaponAppliedHitRules.applyKnockback(hit)",
                "CommonWeaponAppliedHitRules.notifyTrinkets(hit)",
                "CommonWeaponAppliedHitRules.applyKillRewards(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.consumeObsidianResonance(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.triggerCrystalBurst(hit)",
                "triggerEvolvedSingularityFollowup(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyGalaxyMark(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyInfinityMark(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyTideMark(hit)"
        ));

        String appliedPassives = source(
                "BuiltinWeaponPassiveAppliedHitRules.java"
        );
        String iridium = methodBody(
                appliedPassives,
                "static void applyIridiumNeedle("
        );
        String resources = methodBody(
                appliedPassives,
                "static void addAppliedWeaponResources("
        );
        assertTrue(iridium.contains("hit.damageOutcome().isCrit()"));
        assertFalse(iridium.contains("hit.displayCritical()"));
        assertTrue(resources.contains("hit.damageOutcome().isCrit()"));
        assertFalse(resources.contains("hit.displayCritical()"));

        String appliedSkills = source("BuiltinSkillAppliedHitRules.java");
        String elfLeaf = methodBody(
                appliedSkills,
                "static void fireElfLeaf("
        );
        assertTrue(elfLeaf.contains("hit.dealtPositiveDamage()"));
        assertTrue(elfLeaf.contains("hit.target().isAlive()"));
        assertTrue(elfLeaf.contains(
                "hit.authoredSkillContext().getSkillId()"
        ));
        assertTrue(elfLeaf.contains("\"elf_blade_leaf\""));
        assertTrue(elfLeaf.contains(
                "hit.attacker() instanceof ServerPlayer player"
        ));
        assertTrue(elfLeaf.contains(
                "ElfBladeLeafSkillHandler.fireLeafAtTarget("
        ));
        assertTrue(elfLeaf.contains("hit.target()"));
        assertTrue(elfLeaf.contains("hit.gameTick()"));
    }

    @Test
    void authoredHealingAndQuenchCommitOnlyFromExactPositivePostFrame()
            throws IOException {
        String skills = source("BuiltinSkillAppliedHitRules.java");
        String coordinator = source("WeaponAppliedHitCoordinator.java");

        String holy = methodBody(skills, "static void applyHolySmite(");
        assertTrue(holy.contains("hit.dealtPositiveDamage()"));
        assertTrue(holy.contains("\"holy_smite\""));
        assertTrue(holy.contains(
                "hit.authoredSkillContext().getSkillId()"
        ));
        assertTrue(holy.contains("HolyBladeEffects.playSmiteHit("));
        assertTrue(holy.contains(
                "HolyBladeEffects.playHeal(player, "
                        + "HolySmiteSkillHandler.HEAL_AMOUNT)"
        ));
        assertTrue(holy.contains("HolyBladeDodgeTracker.start("));
        assertTrue(holy.contains("hit.gameTick()"));
        assertTrue(holy.contains(
                "HolySmiteSkillHandler.DODGE_DURATION_TICKS"
        ));
        assertTrue(holy.contains("HolySmiteSkillHandler.DODGE_CHANCE"));

        String quench = methodBody(skills, "static void armTemperedQuench(");
        assertTrue(quench.contains("hit.dealtPositiveDamage()"));
        assertTrue(quench.contains("\"tempered_quench\""));
        assertTrue(quench.contains(
                "hit.authoredSkillContext().getSkillId()"
        ));
        assertTrue(quench.contains("hit.target()"));
        assertTrue(quench.contains("hit.gameTick()"));
        assertTrue(quench.contains(
                "TemperedQuenchSkillHandler.BLAST_DELAY_TICKS"
        ));
        assertTrue(quench.contains(
                "hit.weaponSnapshot().orElseThrow()"
        ));

        String lifesteal = methodBody(
                skills,
                "static void applyDarkSwordLifeSteal("
        );
        assertTrue(lifesteal.contains("hit.dealtPositiveDamage()"));
        assertTrue(lifesteal.contains(
                "DarkSwordBloodDebtSkillHandler.getLifestealRatio("
        ));
        assertTrue(lifesteal.contains(
                "DarkSwordBloodMoonSkillHandler.getLifestealRatio("
        ));
        assertTrue(lifesteal.contains("hit.gameTick()"));
        assertTrue(lifesteal.contains(
                "Math.round(hit.appliedDamage() * ratio)"
        ));
        assertFalse(lifesteal.contains("hit.finalDamage()"));
        assertTrue(lifesteal.contains("float actualHeal = CombatHealing.heal("));
        assertTrue(lifesteal.contains(
                "DarkSwordBloodMoonSkillHandler.recordLifeSteal("
        ));
        assertTrue(lifesteal.contains("actualHeal"));
        assertTrue(lifesteal.contains("DarkSwordEffects.playLifeSteal(player)"));

        int positiveGate = coordinator.indexOf(
                "if (!hit.dealtPositiveDamage())"
        );
        int holyCall = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.applyHolySmite(hit)"
        );
        int quenchCall = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.armTemperedQuench(hit)"
        );
        int lifestealCall = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.applyDarkSwordLifeSteal(hit)"
        );
        assertTrue(positiveGate >= 0 && holyCall > positiveGate);
        assertTrue(quenchCall > holyCall);
        assertTrue(lifestealCall > quenchCall);
    }

    @Test
    void trinketsObserveActualAppliedHealthLossInPost() throws IOException {
        String evaluated = source("CommonWeaponEvaluatedHitRules.java");
        String applied = source("CommonWeaponAppliedHitRules.java");
        String coordinator = source("WeaponAppliedHitCoordinator.java");

        assertFalse(evaluated.contains("onDamageMonster("));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains("Math.round(hit.appliedDamage())"));
        assertTrue(applied.contains("hit.damageOutcome().isCrit()"));
        assertFalse(applied.contains("hit.displayCritical()"));
        assertTrue(coordinator.contains(
                "CommonWeaponAppliedHitRules.notifyTrinkets(hit)"
        ));
    }

    @Test
    void exactPostFrameIsBoundBeforeAnyGameplayChildRunsInAppliedPost()
            throws IOException {
        String coordinator = source("WeaponEvaluatedHitCoordinator.java");
        int bind = coordinator.indexOf(
                "CommonWeaponEvaluatedHitRules.bindAppliedHitFrame(hit)"
        );
        int steel = coordinator.indexOf(
                "BuiltinSkillEvaluatedHitRules.emitSteelSpineStrike(hit)"
        );
        assertTrue(steel >= 0 && bind > steel);
        assertFalse(coordinator.contains("triggerCrystalBurst(hit)"));
        assertFalse(coordinator.contains(
                "triggerEvolvedSingularityFollowup(hit)"
        ));

        String common = source("CommonWeaponEvaluatedHitRules.java");
        for (String frozenField : List.of(
                "hit.weaponSnapshot()",
                "hit.weaponIdentity()",
                "hit.skillContext()",
                "hit.outcome()",
                "hit.primaryTarget()",
                "hit.sweepTarget()",
                "hit.inStardewDimension()",
                "hit.target().isAlive()"
        )) {
            assertTrue(common.contains(frozenField), frozenField);
        }
        assertFalse(common.contains("getMainHandItem()"));
    }

    private static void assertIncreasing(
            String source,
            List<String> tokens
    ) {
        int cursor = -1;
        for (String token : tokens) {
            int next = source.indexOf(token, cursor + 1);
            assertTrue(next > cursor, token);
            cursor = next;
        }
    }

    private static String method(
            String source,
            String startToken,
            String endToken
    ) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int open = source.indexOf('{', start);
        assertTrue(open > start, signature);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(start, index + 1);
            }
        }
        throw new AssertionError("Unclosed method: " + signature);
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

package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillAppliedEffectBoundaryContractTest {
    @Test
    void tetanusAndBoneEffectsBelongOnlyToPositiveAppliedHits()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String tetanusHandler = source(
                "combat/skill/handler/TetanusStrikeSkillHandler.java"
        );
        String boneHandler = source(
                "combat/skill/handler/BoneFractureSkillHandler.java"
        );

        String tetanus = method(rules, "static void applyTetanusStrike(");
        assertTrue(tetanus.contains("\"tetanus_strike\".equals(hit.skillId())"));
        assertTrue(tetanus.contains("hit.dealtPositiveDamage()"));
        assertTrue(tetanus.contains("ModMobEffects.VULNERABLE"));
        assertFalse(tetanusHandler.contains("ModMobEffects.VULNERABLE"));

        String bone = method(rules, "static void applyBoneFracture(");
        assertTrue(bone.contains("\"bone_fracture\".equals(hit.skillId())"));
        assertTrue(bone.contains("hit.dealtPositiveDamage()"));
        assertTrue(bone.contains("EquipmentNegativeStatusProtection.decide("));
        assertTrue(bone.contains("BoneFractureTracker.apply("));
        assertFalse(boneHandler.contains("BoneFractureTracker.apply("));

        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyTetanusStrike(hit)"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyBoneFracture(hit)"
        ));
    }

    @Test
    void femurControlSettlesFromUniqueExactPositiveAppliedTargets()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String state = source(
                "combat/skill/handler/FemurSlamExecutionState.java"
        );

        String femur = method(rules, "static void applyFemurSlam(");
        assertTrue(femur.contains("\"femur_slam\".equals(hit.skillId())"));
        assertTrue(femur.contains("hit.dealtPositiveDamage()"));
        assertTrue(femur.contains(
                "FemurSlamSkillHandler.recordAppliedHit("
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyFemurSlam(hit)"
        ));

        String record = method(
                state,
                "boolean recordAppliedHit(LivingEntity target)"
        );
        assertTrue(record.contains(
                "eligibleHitTargets.contains(target.getUUID())"
        ));
        assertTrue(record.contains("appliedTargets.putIfAbsent("));
        String settle = method(
                state,
                "private void settleAppliedControls(ServerPlayer player)"
        );
        assertTrue(settle.contains("appliedTargets.size() == 1"));
        assertTrue(settle.contains("appliedTargets.values()"));
        String control = method(state, "private static void applyControl(");
        assertTrue(control.contains(
                "EquipmentNegativeStatusProtection.decide("
        ));
        assertTrue(control.contains(
                "applyKnockback(player, target, knockback)"
        ));
    }

    @Test
    void runeGuardSettlesHitAndMissExactlyOnce() throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String handler = source(
                "combat/skill/handler/DwarfRuneGuardSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/DwarfRuneGuardExecutionState.java"
        );

        String applied = method(rules, "static void applyDwarfRuneGuard(");
        assertTrue(applied.contains(
                "\"dwarf_rune_guard\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "DwarfRuneGuardSkillHandler.onAppliedHit("
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyDwarfRuneGuard(hit)"
        ));

        assertTrue(handler.contains(
                "instance.initializeExecutionState("
        ));
        assertTrue(handler.contains(
                "WeaponSkillDamage.AttackGatePolicy"
        ));
        assertTrue(handler.contains(".RESPECT_AT_IMPACT"));
        assertTrue(handler.contains("state.settleMiss(context.player())"));
        assertFalse(handler.contains("energyRestoreForTarget("));
        assertFalse(handler.contains("MobEffects.MOVEMENT_SLOWDOWN"));

        assertTrue(state.contains("private boolean outcomeSettled;"));
        assertTrue(state.contains("targetId.equals(target.getUUID())"));
        assertTrue(state.contains("HIT_ENERGY_RESTORE"));
        assertTrue(state.contains("MISS_ENERGY_RESTORE"));
        assertTrue(state.contains("MobEffects.MOVEMENT_SLOWDOWN"));
    }

    @Test
    void forestBlessingUpgradesOnlyFromPositiveAppliedDamage()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String handler = source(
                "combat/skill/handler/ForestBlessingSkillHandler.java"
        );

        String applied = method(rules, "static void applyForestBlessing(");
        assertTrue(applied.contains(
                "\"forest_blessing\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "ForestBlessingSkillHandler.recordAppliedHit(player)"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyForestBlessing(hit)"
        ));

        String activation = method(
                handler,
                "private static void activateBlessing("
        );
        assertTrue(activation.contains(
                "state.healAmount = HEAL_WITHOUT_TARGET"
        ));
        assertFalse(activation.contains(
                "target == null ? HEAL_WITHOUT_TARGET : HEAL_WITH_TARGET"
        ));

        String record = method(
                handler,
                "public static boolean recordAppliedHit(ServerPlayer player)"
        );
        assertTrue(record.contains(
                "BuiltinWeaponSkillHandlers.FOREST_BLESSING"
        ));
        assertTrue(handler.contains("healAmount = HEAL_WITH_TARGET"));
    }

    @Test
    void windSpireGaleStartsOnlyFromPositiveAppliedDamage()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String handler = source(
                "combat/skill/handler/WindSpireThrustSkillHandler.java"
        );

        String applied = method(rules, "static void applyWindSpire(");
        assertTrue(applied.contains(
                "\"wind_spire_thrust\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "WindSpireThrustSkillHandler.grantGale("
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyWindSpire(hit)"
        ));

        String begin = method(handler, "public void begin(");
        assertTrue(begin.contains(".RESPECT_AT_IMPACT"));
        assertFalse(begin.contains("WindSpireTracker.start("));
        assertFalse(begin.contains("new WindSpirePayload("));

        String grant = method(
                handler,
                "public static void grantGale(ServerPlayer player, long nowTick)"
        );
        assertTrue(grant.contains("WindSpireTracker.start("));
        assertTrue(grant.contains("new WindSpirePayload("));
    }

    @Test
    void shadowExecuteBonusCannotOutliveARejectedRootSlash()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String handler = source(
                "combat/skill/handler/ShadowDaggerExecuteSkillHandler.java"
        );

        String begin = method(handler, "public void begin(");
        assertTrue(begin.contains("new State(target.getUUID(), execute)"));
        assertTrue(begin.contains(".RESPECT_AT_IMPACT"));
        assertFalse(begin.contains("createExecuteBonusContext()"));

        String applied = method(rules, "static void applyShadowExecute(");
        assertTrue(applied.contains(
                "\"shadow_dagger_execute\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "ShadowDaggerExecuteSkillHandler.onAppliedRootHit("
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyShadowExecute(hit)"
        ));

        String child = method(handler, "public static boolean onAppliedRootHit(");
        assertTrue(child.contains("state.consumeBonus(target.getUUID())"));
        assertTrue(child.contains("createExecuteBonusContext()"));
        assertTrue(child.contains(".SKILL_DAMAGE"));
        assertTrue(handler.contains("private boolean bonusConsumed;"));
    }

    @Test
    void quenchBlastAppliesVulnerableOnlyAfterPositiveDamage()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String state = source(
                "combat/skill/handler/TemperedQuenchExecutionState.java"
        );

        String applied = method(
                rules,
                "static void applyTemperedQuenchBlast("
        );
        assertTrue(applied.contains(
                "\"tempered_quench_blast\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains("ModMobEffects.VULNERABLE"));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyTemperedQuenchBlast(hit)"
        ));
        assertFalse(state.contains("ModMobEffects.VULNERABLE"));
    }

    @Test
    void dragonThrustCompositeControlUsesOneAppliedHitDecision()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String handler = source(
                "combat/skill/handler/DragonBreathThrustSkillHandler.java"
        );

        String applied = method(rules, "static void applyDragonBreathThrust(");
        assertTrue(applied.contains(
                "\"dragon_breath_thrust\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "EquipmentNegativeStatusProtection.decide("
        ));
        assertTrue(applied.contains(
                "EquipmentMobEffectHandler.addPreAdjustedEffect("
        ));
        assertTrue(applied.contains("YetiFreezeTracker.applyPreAdjusted("));
        assertTrue(applied.contains("adjustRelatedDurationTicks("));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyDragonBreathThrust(hit)"
        ));
        assertFalse(handler.contains("ModMobEffects.VULNERABLE"));
        assertFalse(handler.contains(
                "YetiFreezeTracker.applyWithEquipmentProtection("
        ));
    }

    @Test
    void delayedAndMultiStrikeRewardsUseExactAppliedHits()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String claymore = source(
                "combat/skill/handler/ClaymoreFoldbackExecutionState.java"
        );
        String fish = source(
                "combat/skill/handler/FishcatchThrustExecutionState.java"
        );
        String dwarf = source(
                "combat/skill/handler/DwarfDaggerThrustExecutionState.java"
        );

        String claymoreRule = method(
                rules,
                "static void applyClaymoreFoldbackReturn("
        );
        assertTrue(claymoreRule.contains(
                "\"claymore_foldback_return\".equals(hit.skillId())"
        ));
        assertTrue(claymoreRule.contains("hit.dealtPositiveDamage()"));
        assertTrue(claymoreRule.contains("MobEffects.DIG_SLOWDOWN"));
        assertTrue(claymore.contains(
                ".skillId(\"claymore_foldback_return\")"
        ));
        assertFalse(claymore.contains("target.addEffect("));

        String fishRule = method(rules, "static void applyFishcatchThrust(");
        assertTrue(fishRule.contains(
                "\"fishcatch_thrust\".equals(hit.skillId())"
        ));
        assertTrue(fishRule.contains("hit.dealtPositiveDamage()"));
        assertTrue(fishRule.contains("FishcatchThrustSkillHandler.onAppliedHit("));
        assertTrue(fish.contains("beginStrike(target.getUUID(), fishCatchActive)"));
        assertTrue(fish.contains("boolean recordAppliedHit("));
        assertFalse(fish.contains("boolean hit = WeaponSkillDamage.apply("));

        String dwarfRule = method(rules, "static void applyDwarfDaggerThrust(");
        assertTrue(dwarfRule.contains(
                "\"dwarf_dagger_thrust\".equals(hit.skillId())"
        ));
        assertTrue(dwarfRule.contains("hit.dealtPositiveDamage()"));
        assertTrue(dwarfRule.contains(
                "DwarfDaggerThrustSkillHandler.onAppliedHit("
        ));
        String dwarfApply = method(dwarf, "private void applyHits(");
        assertFalse(dwarfApply.contains("target.addEffect("));
        assertFalse(dwarfApply.contains("restoreEnergy("));
        assertTrue(dwarf.contains("boolean recordAppliedHit("));

        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyClaymoreFoldbackReturn(hit)"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyFishcatchThrust(hit)"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyDwarfDaggerThrust(hit)"
        ));
    }

    @Test
    void carvingBonusIsArmedOnlyByAPositiveAppliedCritical()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String state = source(
                "combat/skill/handler/CarvingThrustExecutionState.java"
        );

        String applied = method(rules, "static void applyCarvingThrust(");
        assertTrue(applied.contains(
                "\"carving_thrust\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains(
                "\"carving_thrust_bonus\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "baseStrike && hit.damageOutcome().isCrit()"
        ));
        assertTrue(applied.contains(
                "CarvingThrustSkillHandler.recordCriticalHit("
        ));
        assertTrue(state.contains("? \"carving_thrust_bonus\""));
        assertTrue(state.contains(".skillId(skillId)"));
        assertTrue(state.contains("boolean recordCriticalHit("));
        assertFalse(state.contains("WeaponSkillDamage.applyWithResult("));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyCarvingThrust(hit)"
        ));
    }

    @Test
    void daggerFinalMarksRequireTheirExactPositiveAppliedFinalStrike()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String galaxyState = source(
                "combat/skill/handler/GalaxyDaggerThrustExecutionState.java"
        );
        String infinityState = source(
                "combat/skill/handler/InfinityDaggerThrustExecutionState.java"
        );

        String galaxyRule = method(
                rules,
                "static void applyGalaxyDaggerFinalMark("
        );
        assertTrue(galaxyRule.contains(
                "\"galaxy_dagger_starstab\".equals(hit.skillId())"
        ));
        assertTrue(galaxyRule.contains("hit.dealtPositiveDamage()"));
        assertTrue(galaxyRule.contains(
                "GalaxyDaggerStarstabSkillHandler.applyFinalStrikeMark("
        ));

        String infinityRule = method(
                rules,
                "static void applyInfinityDaggerFinalMark("
        );
        assertTrue(infinityRule.contains(
                "\"infinity_dagger_singularity_stab\".equals(hit.skillId())"
        ));
        assertTrue(infinityRule.contains("hit.dealtPositiveDamage()"));
        assertTrue(infinityRule.contains(
                "InfinityDaggerSingularityStabSkillHandler.applyFinalStrikeMark("
        ));

        for (String state : new String[]{galaxyState, infinityState}) {
            assertTrue(state.contains("finalStrikeCandidateId"));
            assertTrue(state.contains("consumeFinalStrikeCandidate("));
            assertTrue(state.contains("clearFinalStrikeCandidate();"));
            assertFalse(state.contains(
                    "boolean hit = WeaponSkillDamage.apply("
            ));
            assertFalse(state.contains("MarkTracker.apply("));
        }

        int galaxyConsume = coordinator.indexOf(
                "BuiltinWeaponPassiveAppliedHitRules.applyGalaxyMark(hit)"
        );
        int infinityConsume = coordinator.indexOf(
                "BuiltinWeaponPassiveAppliedHitRules.applyInfinityMark(hit)"
        );
        int galaxyApply = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.applyGalaxyDaggerFinalMark(hit)"
        );
        int infinityApply = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.applyInfinityDaggerFinalMark(hit)"
        );
        assertTrue(galaxyApply > galaxyConsume);
        assertTrue(galaxyApply > infinityConsume);
        assertTrue(infinityApply > galaxyApply);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int openingBrace = source.indexOf('{', start);
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

    private static String source(String relative) throws IOException {
        Path root = javaRoot();
        return Files.readString(root.resolve(relative));
    }

    private static Path javaRoot() throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate main Java source root");
    }
}

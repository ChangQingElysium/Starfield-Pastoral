package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillDamageContractTest {
    private static final List<String> COMPATIBILITY_MIGRATIONS = List.of(
            "combat/skill/handler/FishcatchThrustExecutionState.java",
            "combat/skill/handler/CarvingThrustExecutionState.java",
            "combat/skill/handler/DwarfDaggerThrustExecutionState.java",
            "combat/skill/handler/IridiumNeedleThrustExecutionState.java",
            "combat/skill/handler/GalaxyDaggerThrustExecutionState.java",
            "combat/skill/handler/InfinityDaggerThrustExecutionState.java",
            "combat/skill/handler/ClaymoreFoldbackExecutionState.java",
            "combat/skill/handler/ObsidianCrackExecutionState.java",
            "combat/skill/handler/DarkSwordBloodMoonExecutionState.java",
            "combat/skill/handler/DwarfFortressExecutionState.java",
            "combat/skill/handler/HolyDomainExecutionState.java",
            "combat/skill/LavaKatanaMarkTracker.java",
            "combat/skill/handler/LavaKatanaReverbExecutionState.java",
            "combat/skill/handler/OssifiedExecutionState.java",
            "combat/skill/handler/SteelFalchionDotTracker.java",
            "combat/skill/handler/SteelFalchionTraceExecutionState.java",
            "combat/skill/TemperedFireRingTracker.java",
            "combat/skill/handler/TemplarJudgementExecutionState.java",
            "combat/skill/WickedKrisPoisonTracker.java",
            "entity/effect/IceSpineEffectEntity.java",
            "entity/projectile/ElfBladeLeafEntity.java",
            "entity/projectile/TemperedBilletProjectileEntity.java",
            "entity/projectile/TideAnchorProjectileEntity.java"
    );

    @Test
    void explicitEntryBindsBeforeTheHitAndAlwaysCleansUp()
            throws IOException {
        String source = normalizedSource(
                "combat/skill/WeaponSkillDamage.java"
        );

        assertEquals(6, occurrences(
                source,
                "public static void apply("
        ));
        assertFalse(source.contains("public static boolean apply("));
        assertTrue(source.contains(
                "Objects.requireNonNull(weaponSnapshot, \"weaponSnapshot\")"
        ));
        assertTrue(source.contains(
                "WeaponCombatIdentity.isWeapon("
                        + "weaponSnapshot.weapon()"
        ));
        assertTrue(source.contains(
                "attacker instanceof ServerPlayer serverPlayer"
        ));
        assertTrue(source.contains(
                "target.level() != serverPlayer.level()"
        ));

        int binding = source.indexOf(
                "WeaponSkillContextStore.setPending("
        );
        int attackGate = source.indexOf(
                "CommonHooks.onPlayerAttackTarget("
        );
        int guardedTry = source.lastIndexOf("try {", attackGate);
        int gateRejected = source.indexOf(
                "return;",
                attackGate
        );
        int hit = source.indexOf(
                "target.hurt("
        );
        int cleanup = source.indexOf(
                "} finally {",
                hit
        );
        assertTrue(binding >= 0 && guardedTry > binding);
        assertTrue(attackGate > guardedTry && gateRejected > attackGate);
        assertTrue(hit > gateRejected);
        assertTrue(cleanup > hit);
        assertTrue(source.indexOf(
                "clearUnconsumedContext(serverPlayer, nowTick)",
                cleanup
        ) > cleanup);
        assertFalse(source.contains(
                "PIPELINE_SENTINEL_DAMAGE"
        ));
        assertTrue(source.contains(
                "pipelineInputDamage(resolvedSnapshot, skillContext)"
        ));
        assertTrue(source.contains(
                "WeaponSkillRuntime.releaseWeaponSnapshot("
        ));
        assertTrue(source.contains(
                "stats.getAverageDamage()"
        ));
        assertTrue(source.contains(
                "serverPlayer.damageSources().playerAttack(serverPlayer)"
        ));
        assertEquals(1, occurrences(
                source,
                "CommonHooks.onPlayerAttackTarget("
        ));
        assertEquals(2, occurrences(
                source,
                "AttackGatePolicy.SKILL_DAMAGE"
        ));
        assertTrue(source.contains(
                "attackGatePolicy == AttackGatePolicy.RESPECT_AT_IMPACT"
        ));
        assertFalse(source.contains("DamagePipeline.evaluate("));
    }

    @Test
    void delayedPlayerAttackHitsRecheckPermissionAtImpact()
            throws IOException {
        for (String relative : List.of(
                "combat/skill/handler/CrescentSlashSkillHandler.java",
                "combat/skill/handler/ForestBlessingSkillHandler.java"
        )) {
            String handler = normalizedSource(relative);
            assertTrue(
                    handler.contains("WeaponSkillDamage.apply("),
                    relative
            );
            assertTrue(
                    handler.contains("context.weaponSnapshot()"),
                    relative
            );
            assertTrue(
                    handler.contains(
                            "WeaponSkillDamage.AttackGatePolicy."
                                    + "RESPECT_AT_IMPACT"
                    ),
                    relative
            );
            assertTrue(
                    handler.contains(
                            "context.nowTick() "
                                    + "+ HIT_CONTEXT_LIFETIME_TICKS"
                    ),
                    relative
            );
            assertFalse(
                    handler.contains("context.player().attack("),
                    relative
            );
            assertFalse(
                    handler.contains("WeaponSkillContextStore.setPending("),
                    relative
            );
        }
    }

    @Test
    void arithmeticAndCombatSemanticsRemainInTheSharedEvaluator()
            throws IOException {
        String entry = normalizedSource(
                "combat/skill/WeaponSkillDamage.java"
        );
        String events = normalizedSource(
                "combat/WeaponCombatEvents.java"
        );
        String assembly = normalizedSource(
                "combat/WeaponDamageAssemblyRules.java"
        );
        String calculator = normalizedSource(
                "combat/DamageCalculator.java"
        );

        int consume = events.indexOf(
                "WeaponSkillContextStore.consumePending("
        );
        int missingSnapshotGate = events.indexOf(
                "if (releaseWeapon == null) return null;",
                consume
        );
        int frozenWeapon = events.indexOf(
                "ItemStack weapon = releaseWeapon.weapon();",
                missingSnapshotGate
        );
        int prepare = events.indexOf(
                "WeaponHitPreparation.prepare(",
                frozenWeapon
        );
        int request = events.indexOf(
                ".createPlayerDamageRequest(",
                prepare
        );
        int assemble = events.indexOf(
                "WeaponDamageAssemblyRules.apply(",
                request
        );
        int evaluate = events.indexOf(
                "DamagePipeline.evaluate(damageRequest.build())",
                assemble
        );
        int freezeOutcome = events.indexOf(
                "new IncomingWeaponResolution(hit, "
                        + "outcome.getFinalDamage())",
                evaluate
        );
        int nativeEntry = events.indexOf(
                "public static void onLivingIncomingDamage("
                        + "LivingIncomingDamageEvent event)"
        );
        int nativeEvaluate = events.indexOf(
                "IncomingWeaponResolution resolution = evaluateWeaponHit(",
                nativeEntry
        );
        int nativeAmount = events.indexOf(
                "event.setAmount(resolution.authoritativeDamage())",
                nativeEvaluate
        );
        int customEntry = events.indexOf(
                "public static CustomHealthWeaponResolution "
                        + "evaluateCustomHealthWeaponHit("
        );
        int customEvaluate = events.indexOf(
                "IncomingWeaponResolution resolution = evaluateWeaponHit(",
                customEntry
        );

        assertTrue(entry.contains(
                "WeaponSkillContextStore.setPending("
        ));
        assertTrue(consume >= 0);
        assertTrue(missingSnapshotGate > consume);
        assertTrue(frozenWeapon > missingSnapshotGate);
        assertTrue(prepare > frozenWeapon);
        assertTrue(request > prepare);
        assertTrue(assemble > request);
        assertTrue(evaluate > assemble);
        assertTrue(freezeOutcome > evaluate);
        assertTrue(nativeEntry >= 0);
        assertTrue(nativeEvaluate > nativeEntry);
        assertTrue(nativeAmount > nativeEvaluate);
        assertTrue(customEntry >= 0);
        assertTrue(customEvaluate > customEntry);
        int evaluatorEnd = events.indexOf(
                "public static void onLivingHurt(",
                consume
        );
        assertTrue(evaluatorEnd > consume);
        assertFalse(events.substring(consume, evaluatorEnd).contains(
                "player.getMainHandItem()"
        ));
        assertTrue(calculator.contains(
                "targetStats.getResilience()"
        ));
        assertTrue(calculator.contains(
                "ProfessionType.FIGHTER"
        ));
        assertTrue(calculator.contains(
                "equipmentStats.getCritChance()"
        ));
        assertTrue(assembly.contains(
                "StardewEnchantments.BUG_KILLER"
        ));
        assertFalse(events.contains("StardewEnchantments.BUG_KILLER"));
    }

    @Test
    void compatibilityOverloadSupportsIncrementalTrackerMigration()
            throws IOException {
        for (String relative : COMPATIBILITY_MIGRATIONS) {
            String tracker = normalizedSource(relative);
            assertTrue(
                    tracker.contains("WeaponSkillDamage.apply"),
                    relative
            );
            assertFalse(
                    tracker.contains(
                            "WeaponSkillContextStore.setPending("
                    ),
                    relative
            );
        }

        String fishcatch = normalizedSource(
                "combat/skill/handler/FishcatchThrustExecutionState.java"
        );
        assertTrue(fishcatch.contains("WeaponSkillDamage.apply("));
        assertTrue(fishcatch.contains(
                "beginStrike(target.getUUID(), fishCatchActive)"
        ));
        assertTrue(fishcatch.contains("boolean recordAppliedHit("));
        assertFalse(fishcatch.contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
        String carving = normalizedSource(
                "combat/skill/handler/CarvingThrustExecutionState.java"
        );
        assertTrue(carving.contains("WeaponSkillDamage.apply("));
        assertTrue(carving.contains("boolean recordCriticalHit("));
        assertFalse(carving.contains("WeaponSkillDamage.applyWithResult("));
        assertTrue(carving.contains("executionContext.weaponSnapshot()"));
        String dwarf = normalizedSource(
                "combat/skill/handler/DwarfDaggerThrustExecutionState.java"
        );
        assertTrue(dwarf.contains("WeaponSkillDamage.apply("));
        assertTrue(dwarf.contains("boolean recordAppliedHit("));
        assertFalse(dwarf.contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
        assertTrue(dwarf.contains("executionContext.weaponSnapshot()"));
        assertFalse(normalizedSource(
                "combat/skill/handler/IridiumNeedleThrustExecutionState.java"
        ).contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
        assertFalse(normalizedSource(
                "combat/skill/handler/GalaxyDaggerThrustExecutionState.java"
        ).contains(
                "return WeaponSkillDamage.apply("
        ));
        assertFalse(normalizedSource(
                "combat/skill/handler/InfinityDaggerThrustExecutionState.java"
        ).contains(
                "return WeaponSkillDamage.apply("
        ));
        assertTrue(normalizedSource(
                "combat/skill/handler/GalaxyDaggerThrustExecutionState.java"
        ).contains("context.weaponSnapshot()"));
        assertTrue(normalizedSource(
                "combat/skill/handler/InfinityDaggerThrustExecutionState.java"
        ).contains("context.weaponSnapshot()"));

        String state = normalizedSource(
                "combat/skill/handler/TemperedQuenchExecutionState.java"
        );

        assertTrue(state.contains(
                "WeaponSkillDamage.apply( context.player(), target, "
                        + "createBlastContext(), weaponSnapshot,"
        ));
        assertFalse(state.contains(
                "if (weaponSnapshot == null)"
        ));
        assertEquals(1, occurrences(
                state,
                "WeaponSkillDamage.apply("
        ));
        assertFalse(state.contains(
                "target.hurt( "
                        + "player.damageSources().playerAttack(player), "
                        + "1.0F"
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

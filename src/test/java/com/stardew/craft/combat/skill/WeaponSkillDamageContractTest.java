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
            "combat/skill/BrokenTridentThrustTracker.java",
            "combat/skill/CarvingKnifeThrustTracker.java",
            "combat/skill/DwarfDaggerThrustTracker.java",
            "combat/skill/IridiumNeedleThrustTracker.java",
            "combat/skill/GalaxyDaggerThrustTracker.java",
            "combat/skill/InfinityDaggerThrustTracker.java",
            "combat/skill/ClaymoreFoldbackTracker.java",
            "combat/skill/ObsidianCrackTracker.java",
            "combat/skill/DarkSwordBloodMoonTracker.java",
            "combat/skill/DwarfFortressTracker.java",
            "combat/skill/HolyBladeSanctuaryTracker.java",
            "combat/skill/LavaKatanaMarkTracker.java",
            "combat/skill/LavaKatanaReverbTracker.java",
            "combat/skill/ObsidianResonanceTracker.java",
            "combat/skill/OssifiedExecutionTracker.java",
            "combat/skill/SteelFalchionLineTracker.java",
            "combat/skill/TemperedFireRingTracker.java",
            "combat/skill/TemplarJudgementTracker.java",
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

        assertEquals(4, occurrences(
                source,
                "public static boolean apply("
        ));
        assertTrue(source.contains(
                "Objects.requireNonNull(weaponSnapshot, \"weaponSnapshot\")"
        ));
        assertTrue(source.contains(
                "weaponSnapshot.weapon().getItem() "
                        + "instanceof IStardewWeapon"
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
        int gateRejected = source.indexOf("return false;", attackGate);
        int hit = source.indexOf(
                "return target.hurt("
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
    void arithmeticAndCombatSemanticsRemainInTheCentralEvent()
            throws IOException {
        String entry = normalizedSource(
                "combat/skill/WeaponSkillDamage.java"
        );
        String events = normalizedSource(
                "combat/WeaponCombatEvents.java"
        );
        String calculator = normalizedSource(
                "combat/DamageCalculator.java"
        );

        int consume = events.indexOf(
                "WeaponSkillContextStore.consumePending(player, nowTick)"
        );
        int currentHand = events.indexOf(
                "player.getMainHandItem()",
                consume
        );
        int request = events.indexOf(
                "DamageCalculator.createPlayerDamageRequest(",
                currentHand
        );
        int evaluate = events.indexOf(
                "DamagePipeline.evaluate(damageRequest.build())",
                request
        );
        int applyFinal = events.indexOf(
                "event.setNewDamage(finalDamage)",
                evaluate
        );

        assertTrue(entry.contains(
                "WeaponSkillContextStore.setPending("
        ));
        assertTrue(consume >= 0);
        assertTrue(currentHand > consume);
        assertTrue(request > currentHand);
        assertTrue(evaluate > request);
        assertTrue(applyFinal > evaluate);
        assertTrue(calculator.contains(
                "targetStats.getResilience()"
        ));
        assertTrue(calculator.contains(
                "ProfessionType.FIGHTER"
        ));
        assertTrue(calculator.contains(
                "equipmentStats.getCritChance()"
        ));
        assertTrue(events.contains(
                "StardewEnchantments.BUG_KILLER"
        ));
    }

    @Test
    void compatibilityOverloadSupportsIncrementalTrackerMigration()
            throws IOException {
        for (String relative : COMPATIBILITY_MIGRATIONS) {
            String tracker = normalizedSource(relative);
            assertTrue(
                    tracker.contains("WeaponSkillDamage.apply("),
                    relative
            );
            assertFalse(
                    tracker.contains(
                            "WeaponSkillContextStore.setPending("
                    ),
                    relative
            );
        }

        assertTrue(normalizedSource(
                "combat/skill/BrokenTridentThrustTracker.java"
        ).contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
        assertTrue(normalizedSource(
                "combat/skill/CarvingKnifeThrustTracker.java"
        ).contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
        assertTrue(normalizedSource(
                "combat/skill/DwarfDaggerThrustTracker.java"
        ).contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
        assertTrue(normalizedSource(
                "combat/skill/IridiumNeedleThrustTracker.java"
        ).contains(
                "boolean hit = WeaponSkillDamage.apply("
        ));
        assertTrue(normalizedSource(
                "combat/skill/GalaxyDaggerThrustTracker.java"
        ).contains(
                "return WeaponSkillDamage.apply("
        ));
        assertTrue(normalizedSource(
                "combat/skill/InfinityDaggerThrustTracker.java"
        ).contains(
                "return WeaponSkillDamage.apply("
        ));

        String tracker = normalizedSource(
                "combat/skill/TemperedQuenchTracker.java"
        );

        assertTrue(tracker.contains(
                "WeaponSkillDamage.apply( player, target, "
                        + "createBlastContext(), weaponSnapshot,"
        ));
        assertTrue(tracker.contains(
                "if (weaponSnapshot == null)"
        ));
        assertEquals(2, occurrences(
                tracker,
                "WeaponSkillDamage.apply("
        ));
        assertFalse(tracker.contains(
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

package com.stardew.craft.combat;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponHitCoordinatorContractTest {
    @Test
    void neoforgeAdapterContainsNoBuiltInWeaponOrSkillBranches()
            throws IOException {
        String events = readMainSource("combat/WeaponCombatEvents.java");
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

        for (WeaponData weapon : WeaponRegistry.getAll()) {
            assertFalse(
                    events.contains("\"" + weapon.getId() + "\""),
                    () -> "weapon branch leaked into event adapter: "
                            + weapon.getId()
            );
            assertSkillAbsent(events, weapon.getSkill1());
            assertSkillAbsent(events, weapon.getSkill2());
        }

        assertIncreasing(resolver, List.of(
                "WeaponHitPreparation.prepare(",
                "WeaponDamageAssemblyRules.apply(",
                "DamagePipeline.evaluate(",
                "new EvaluatedWeaponHit("
        ));
        assertIncreasing(incoming, List.of(
                "evaluateWeaponHit(",
                "event.setAmount(resolution.authoritativeDamage())",
                "WeaponIncomingHitStore.bind("
        ));
        assertIncreasing(pre, List.of(
                "WeaponIncomingHitStore.consume(",
                "hit.preparationReservation().commit()",
                "WeaponEvaluatedHitCoordinator.apply(hit)"
        ));
        assertFalse(resolver.contains("event.getOriginalDamage()"));
        assertFalse(resolver.contains("nativeProtectionRatio"));
        assertTrue(events.contains("DamageNumberContextStore.consume("));
        assertTrue(events.contains("ResolvedWeaponHit.from("));
        assertTrue(events.contains("WeaponAppliedHitCoordinator.apply("));
    }

    @Test
    void neoforgePostAdapterOnlyResolvesAndDelegatesTheExactFrame()
            throws IOException {
        String events = readMainSource("combat/WeaponCombatEvents.java");
        String post = method(
                events,
                "public static void onLivingDamagePost(",
                "private static boolean isSweepDamageSource("
        );

        assertTrue(post.contains("DamageNumberContextStore.consume("));
        assertTrue(post.contains("if (meta == null) return;"));
        assertTrue(post.contains("ResolvedWeaponHit.from("));
        assertTrue(post.contains("WeaponAppliedHitCoordinator.apply("));
        assertFalse(post.contains("WeaponSkillDamage.apply("));
        assertFalse(post.contains(".equals(skillId)"));
        assertFalse(post.contains("getWeaponId()"));
        assertFalse(post.contains("sendParticles("));
        assertFalse(post.contains("playSound("));
    }

    @Test
    void preMetadataFreezesEverythingPostRulesNeed()
            throws IOException {
        String store = readMainSource("combat/DamageNumberContextStore.java");
        String hit = readMainSource("combat/ResolvedWeaponHit.java");

        for (String field : List.of(
                "WeaponCombatIdentity.Resolved weaponIdentity",
                "SkillContext skillContext",
                "DamageOutcome damageOutcome",
                "boolean primaryTarget",
                "boolean sweepTarget",
                "boolean inStardewDimension",
                "boolean targetAliveBeforeApplication"
        )) {
            assertTrue(store.contains(field), field);
        }
        assertTrue(hit.contains("frame.boundWeapon(),"));
        assertTrue(hit.contains("return frame.boundWeapon().weapon();"));
        assertFalse(hit.contains("getMainHandItem()"));
        assertTrue(hit.contains("DamageSource source"));
        assertTrue(hit.contains("float appliedDamage"));
    }

    @Test
    void mechanicalExtractionPreservesLegacyAppliedRuleOrder()
            throws IOException {
        String coordinator = readMainSource(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        assertIncreasing(coordinator, List.of(
                "BuiltinSkillAppliedHitRules.applyHolySmite(hit)",
                "BuiltinSkillAppliedHitRules.armTemperedQuench(hit)",
                "BuiltinSkillAppliedHitRules.applyDarkSwordLifeSteal(hit)",
                "CommonWeaponAppliedHitRules.applyKnockback(hit)",
                "BuiltinSkillAppliedHitRules.applyBurglarShank(hit)",
                "CommonWeaponAppliedHitRules.applyVampiricEnchantment(hit)",
                "CommonWeaponAppliedHitRules.notifyTrinkets(hit)",
                "CommonWeaponAppliedHitRules.applyKillRewards(hit)",
                "LegacyWeaponHitPresentation.emitDamageNumber(hit)",
                "LegacyWeaponHitPresentation.emitGeneralSkillImpact(hit)",
                "BuiltinSkillAppliedHitRules.applyInsectEyeStance(hit)",
                "BuiltinSkillAppliedHitRules.emitInsectDash(hit)",
                "BuiltinSkillAppliedHitRules.applyYetiMark(hit)",
                "BuiltinSkillAppliedHitRules.applyLavaBrand(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyOssifiedCriticalMark(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyYetiFollowup(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyLavaHeat(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyGalaxyMark(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyInfinityMark(hit)",
                "BuiltinWeaponPassiveAppliedHitRules.applyTideMark(hit)"
        ));
        assertFalse(coordinator.contains("\"burglar_shank\""));
        assertFalse(coordinator.contains("getWeaponId()"));
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

    private static void assertSkillAbsent(
            String source,
            WeaponSkillData skill
    ) {
        if (skill == null) {
            return;
        }
        assertFalse(
                source.contains("\"" + skill.getId() + "\""),
                () -> "skill branch leaked into event adapter: "
                        + skill.getId()
        );
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

    private static String readMainSource(String relative) throws IOException {
        Path sourceRoot = locate(Path.of(
                "src", "main", "java", "com", "stardew", "craft"
        ));
        return Files.readString(sourceRoot.resolve(relative));
    }

    private static Path locate(Path relative) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}

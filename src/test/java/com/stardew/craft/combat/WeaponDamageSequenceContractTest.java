package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponDamageSequenceContractTest {
    @Test
    void postHitMetadataIsBoundAndConsumedByExactDamageSequence()
            throws IOException {
        String events = readMainSource("combat/WeaponCombatEvents.java");
        String evaluated = readMainSource(
                "combat/CommonWeaponEvaluatedHitRules.java"
        );
        String store = readMainSource("combat/DamageNumberContextStore.java");
        String post = readMainSource(
                "combat/WeaponAppliedHitCoordinator.java"
        );

        assertOrdered(
                evaluated,
                "static void bindAppliedHitFrame(EvaluatedWeaponHit hit)",
                "DamageNumberContextStore.bind(",
                "hit.attacker(),",
                "hit.target(),",
                "hit.source(),",
                "hit.weaponSnapshot(),",
                "hit.weaponIdentity(),",
                "hit.skillContext(),",
                "hit.outcome(),"
        );
        assertOrdered(
                events,
                "public static void onLivingDamagePost(",
                "DamageNumberContextStore.consume(",
                "player,",
                "target,",
                "event.getSource(),",
                "if (meta == null) return;",
                "WeaponAppliedHitCoordinator.apply(",
                "ResolvedWeaponHit.from(event, player, meta, nowTick)"
        );
        assertTrue(events.contains("if (meta == null) return;"));
        assertFalse(events.contains("DamageNumberContextStore.peek("));
        assertOrdered(
                post,
                "static void apply(ResolvedWeaponHit hit)",
                "CommonWeaponAppliedHitRules.applyKnockback(hit);",
                "BuiltinSkillAppliedHitRules.applyBurglarShank(hit);"
        );

        assertTrue(store.contains("Map<UUID, Deque<BoundMeta>> ACTIVE"));
        assertTrue(store.contains(".push(new BoundMeta("));
        assertTrue(store.contains("BoundMeta bound = stack.peek();"));
        assertTrue(store.contains("source == candidate"));
        assertTrue(store.contains("if (!bound.matches(target, source)) {\n"
                + "            return null;"));
        assertFalse(store.contains("LAST_COMPLETED"));
        assertFalse(store.contains("Meta peek("));
    }

    @Test
    void authoredCriticalFollowupUsesExactAppliedPostAcrossNestedHits()
            throws IOException {
        String store = readMainSource("combat/DamageNumberContextStore.java");
        String damage = readMainSource(
                "combat/skill/WeaponSkillDamage.java"
        );
        String carving = readMainSource(
                "combat/skill/handler/CarvingThrustExecutionState.java"
        );
        String applied = readMainSource(
                "combat/BuiltinSkillAppliedHitRules.java"
        );

        int source = damage.indexOf("DamageSource source =");
        int hurt = damage.indexOf("target.hurt(", source);
        int cleanup = damage.indexOf("} finally {", hurt);
        assertTrue(source >= 0 && hurt > source && cleanup > hurt);
        assertFalse(store.contains("ResultCapture"));
        assertFalse(store.contains("IdentityHashMap"));
        assertFalse(damage.contains("public record Result("));
        assertFalse(damage.contains("applyWithResult("));
        assertTrue(damage.contains("public static void apply("));
        assertFalse(damage.contains("public static boolean apply("));

        assertFalse(carving.contains("WeaponSkillDamage.applyWithResult("));
        assertTrue(carving.contains("WeaponSkillDamage.apply("));
        assertTrue(carving.contains("boolean recordCriticalHit("));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains("hit.damageOutcome().isCrit()"));
        assertTrue(applied.contains(
                "CarvingThrustSkillHandler.recordCriticalHit("
        ));
        assertFalse(carving.contains("DamageNumberContextStore"));
    }

    @Test
    void stardewKnockbackHasOneOwnerAndFemurOptsIntoItsOwnImpulse()
            throws IOException {
        String evaluated = readMainSource(
                "combat/CommonWeaponEvaluatedHitRules.java"
        );
        String mixin = readMainSource("mixin/PlayerSweepAttackMixin.java");
        String femur = readMainSource(
                "combat/skill/handler/FemurSlamExecutionState.java"
        );

        assertTrue(mixin.contains("method = \"attack\""));
        assertTrue(mixin.contains(
                "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"
        ));
        assertTrue(mixin.contains(
                "if (!WeaponCombatIdentity.isWeapon(player.getMainHandItem()))"
        ));
        assertOrdered(
                evaluated,
                "float knockbackStrength = 0.0F;",
                "if (hit.skillContext().usesDefaultKnockback() "
                        + "&& hit.successful())",
                "knockbackStrength = calculateKnockbackStrength(",
                "DamageNumberContextStore.bind(",
                "knockbackStrength,"
        );
        assertTrue(femur.contains(".defaultKnockback(false)"));
        assertTrue(femur.contains("applyKnockback(player, target, knockback);"));
    }

    @Test
    void defaultKnockbackCommitsOnlyAfterPositiveFinalDamage()
            throws IOException {
        String events = readMainSource("combat/WeaponCombatEvents.java");
        String evaluated = readMainSource(
                "combat/CommonWeaponEvaluatedHitRules.java"
        );
        String applied = readMainSource(
                "combat/CommonWeaponAppliedHitRules.java"
        );
        String pre = method(
                events,
                "public static void onLivingHurt(LivingDamageEvent.Pre event)",
                "enum WeaponDamageProvenance"
        );

        assertFalse(pre.contains("target.knockback("));
        assertOrdered(
                evaluated,
                "float knockbackStrength = 0.0F;",
                "if (hit.skillContext().usesDefaultKnockback() "
                        + "&& hit.successful())",
                "knockbackStrength = calculateKnockbackStrength(",
                "DamageNumberContextStore.bind(",
                "knockbackStrength,"
        );
        assertFalse(evaluated.contains("target.knockback("));
        assertTrue(applied.contains(
                "if (!hit.dealtPositiveDamage() || strength <= 0.0F)"
        ));
        assertTrue(applied.contains(
                "target.knockback(strength, dx, dz);"
        ));
    }

    @Test
    void stardewNormalDamageDoesNotReapplyVanillaAttackStrength()
            throws IOException {
        String events = readMainSource("combat/WeaponCombatEvents.java");
        String mixin = readMainSource("mixin/PlayerSweepAttackMixin.java");
        String pre = method(
                events,
                "public static void onLivingHurt(LivingDamageEvent.Pre event)",
                "enum WeaponDamageProvenance"
        );

        assertFalse(pre.contains("getAttackStrengthScale("));
        assertFalse(pre.contains("\"attack_strength\""));
        assertOrdered(
                mixin,
                "private boolean stardewcraft$authorizedPrimaryHurt(",
                "WeaponDamageSnapshot.capture(",
                "OrdinaryWeaponAttackFrameStore.bind(",
                "return target.hurt(",
                "} finally {",
                "OrdinaryWeaponAttackFrameStore.discard("
        );
        assertOrdered(
                mixin,
                "private boolean stardewcraft$suppressNativeSweepHurt(",
                "if (WeaponCombatIdentity.isWeapon("
                        + "player.getMainHandItem()))",
                "return false;",
                "return target.hurt(source, vanillaDamage);"
        );
        assertTrue(mixin.contains("WeaponStats.fromItemStack(weapon)\n"
                + "                .getAverageDamage()"));
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

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + needle);
            previous = current;
        }
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

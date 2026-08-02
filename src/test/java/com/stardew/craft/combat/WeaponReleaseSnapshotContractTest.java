package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponReleaseSnapshotContractTest {
    @Test
    void centralDamageRequiresTheAdmissionReleaseSnapshot()
            throws IOException {
        String events = readSource("WeaponCombatEvents.java");
        String evaluated = readSource("CommonWeaponEvaluatedHitRules.java");
        String resolver = between(
                events,
                "private static IncomingWeaponResolution evaluateWeaponHit(",
                "public static void onLivingHurt(LivingDamageEvent.Pre event)"
        );
        String incoming = between(
                events,
                "public static void onLivingIncomingDamage(",
                "public static void onLivingIncomingDamageFinal("
        );
        String pre = between(
                events,
                "public static void onLivingHurt(LivingDamageEvent.Pre event)",
                "public static CustomHealthWeaponResolution "
                        + "evaluateCustomHealthWeaponHit("
        );
        assertOrdered(
                resolver,
                "WeaponSkillContextStore.consumePending(",
                "WeaponDamageSnapshot releaseWeapon = pendingHit == null",
                "if (releaseWeapon == null) return null;",
                "ItemStack weapon = releaseWeapon.weapon();",
                "WeaponCombatIdentity.resolve(weapon).orElse(null)",
                "WeaponDamageSnapshot damageWeaponSnapshot = releaseWeapon;",
                "EvaluatedWeaponHit hit = new EvaluatedWeaponHit(",
                "damageWeaponSnapshot,"
        );
        assertFalse(resolver.contains("player.getMainHandItem()"));
        assertFalse(resolver.contains("WeaponDamageSnapshot.capture("));
        assertOrdered(
                incoming,
                "evaluateWeaponHit(",
                "WeaponIncomingHitStore.bind(",
                "resolution.hit(),"
        );
        assertOrdered(
                pre,
                "WeaponIncomingHitStore.consume(",
                "WeaponEvaluatedHitCoordinator.apply(hit)"
        );
        assertFalse(pre.contains("player.getMainHandItem()"));
        assertFalse(pre.contains("WeaponDamageSnapshot.capture("));
        assertOrdered(
                evaluated,
                "DamageNumberContextStore.bind(",
                "hit.attacker(),",
                "hit.target(),",
                "hit.source(),",
                "hit.weaponSnapshot(),"
        );
    }

    @Test
    void damageCalculatorNeverReReadsTheAttackersCurrentHand()
            throws IOException {
        String source = readSource("DamageCalculator.java");

        assertFalse(source.contains("getMainHandItem()"));
        assertTrue(source.contains("\"infinity_blade\".equals(weaponId)"));
    }

    @Test
    void nestedChildHitsInheritTheCurrentDamageWeaponSnapshot()
            throws IOException {
        String passives = readSource(
                "BuiltinWeaponPassiveAppliedHitRules.java"
        );

        assertChildContextUsesSharedAppliedRule(
                passives,
                ".skillId(\"crystal_dagger_burst\")"
        );
        assertChildContextUsesSharedAppliedRule(
                passives,
                ".skillId(\"singularity_followup\")"
        );
        assertChildContextUsesSharedAppliedRule(
                passives,
                ".skillId(\"ossified_mark_bonus\")"
        );
        assertChildContextUsesSharedAppliedRule(
                passives,
                ".skillId(\"galaxy_dagger_mark_bonus\")"
        );
        assertChildContextUsesSharedAppliedRule(
                passives,
                ".skillId(\"infinity_dagger_mark_bonus\")"
        );

        int tideContext = passives.indexOf(
                "TideMarkTracker.createBonusContext()"
        );
        assertChildContextUsesSharedAppliedRule(passives, tideContext);
        assertEquals(
                1,
                occurrences(
                        passives,
                        "WeaponSkillDamage.apply("
                )
        );
        assertFalse(passives.contains("player.attack(target)"));
        assertFalse(passives.contains("WeaponSkillContextStore.setPending("));
        assertFalse(passives.contains("clearUnconsumedSkillContext("));
        assertTrue(passives.contains(
                "WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE"
        ));
        assertTrue(passives.contains(
                ".BYPASS_FOR_AUTHORED_SEQUENCE"
        ));

        assertTrue(passives.contains(
                "WeaponDamageSnapshot snapshot = "
                        + "hit.weaponSnapshot().orElseThrow();"
        ));
        assertFalse(passives.contains("target.invulnerableTime = 0;"));
        assertFalse(passives.contains("target.hurtTime = 0;"));

        int burst = passives.indexOf(".skillId(\"crystal_dagger_burst\")");
        int burstDamage = passives.lastIndexOf(
                "applyChildDamage(",
                burst
        );
        assertTrue(burstDamage >= 0 && burstDamage < burst);
        String trigger = method(
                passives,
                "static void triggerCrystalBurst(ResolvedWeaponHit hit)"
        );
        assertFalse(trigger.contains("new CrystalDaggerBurstPayload()"));
        String presentation = method(
                passives,
                "static void emitCrystalDaggerBurstPresentation("
                        + "ResolvedWeaponHit hit)"
        );
        assertOrdered(
                presentation,
                "\"crystal_dagger_burst\".equals(hit.skillId())",
                "hit.dealtPositiveDamage()",
                "new CrystalDaggerBurstPayload()"
        );
    }

    private static void assertChildContextUsesSharedAppliedRule(
            String source,
            String contextMarker
    ) {
        assertChildContextUsesSharedAppliedRule(
                source,
                source.indexOf(contextMarker)
        );
    }

    private static void assertChildContextUsesSharedAppliedRule(
            String source,
            int context
    ) {
        assertTrue(context >= 0);
        int before = source.lastIndexOf("applyChildDamage(", context);
        int after = source.indexOf("applyChildDamage(", context);
        boolean nearbyBefore = before >= 0 && context - before < 800;
        boolean nearbyAfter = after > context && after - context < 800;
        assertTrue(nearbyBefore || nearbyAfter);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int next = source.indexOf(
                "\n    static void ",
                start + signature.length()
        );
        int end = next >= 0 ? next : source.lastIndexOf('}');
        assertTrue(end > start, signature);
        return source.substring(start, end);
    }

    private static String between(
            String source,
            String startToken,
            String endToken
    ) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue(start >= 0 && end > start, startToken);
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

    private static String readSource(String fileName) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
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

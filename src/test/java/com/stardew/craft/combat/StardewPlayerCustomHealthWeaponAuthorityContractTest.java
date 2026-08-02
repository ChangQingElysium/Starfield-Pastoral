package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the single damage owner for Stardew-dimension ServerPlayer targets. */
class StardewPlayerCustomHealthWeaponAuthorityContractTest {
    @Test
    void stardewPlayerPreCanNeverReinjectNativeHealthDamage()
            throws IOException {
        String combat = source("combat/WeaponCombatEvents.java");
        String pre = method(
                combat,
                "public static void onLivingHurt(LivingDamageEvent.Pre event)"
        );

        assertOrdered(
                pre,
                "target instanceof ServerPlayer",
                "DimensionDamageMapper.isInStardewDimension(target)",
                "event.setNewDamage(0.0F);",
                "return;",
                "if (player == null) return;"
        );
        int playerGuard = pre.indexOf("target instanceof ServerPlayer");
        int stardewMobEvaluation = pre.indexOf(
                "if (DimensionDamageMapper.isInStardewDimension(target))",
                playerGuard + 1
        );
        assertTrue(stardewMobEvaluation > playerGuard);
        assertFalse(pre.substring(0, stardewMobEvaluation).contains(
                "evaluateWeaponHit("
        ));
    }

    @Test
    void exactWeaponEvaluationStartsOnlyAfterEveryCustomHealthRejectionGate()
            throws IOException {
        String incoming = method(
                source("player/PlayerDataEventHandler.java"),
                "public static void onPlayerHurt("
        );

        int evaluate = incoming.indexOf(
                "WeaponCombatEvents.evaluateCustomHealthWeaponHit("
        );
        assertTrue(evaluate >= 0);
        for (String rejectionGate : new String[] {
                "rawAmount <= 0.0f",
                "cancelBasiliskDamage(player, event.getSource())",
                "PassOutService.isInCombatDeathRecovery(player)",
                "PassOutService.isKnockedOut(player)",
                "player.invulnerableTime > 0",
                "YobaProtectionState.isActive(player, nowTick)",
                "eqStats.hasSlimeCharmer()",
                "eqStats.hasYobaProtection()"
        }) {
            int gate = incoming.indexOf(rejectionGate);
            assertTrue(gate >= 0, "Missing rejection gate: " + rejectionGate);
            assertTrue(gate < evaluate, "Gate must precede evaluation: " + rejectionGate);
        }

        assertOrdered(
                incoming,
                "player.invulnerableTime > 0",
                "DamageTypeTags.BYPASSES_COOLDOWN",
                "return;",
                "WeaponCombatEvents.evaluateCustomHealthWeaponHit("
        );
        assertFalse(incoming.substring(0, evaluate).contains(
                "WeaponSkillContextStore.consumePending("
        ));
    }

    @Test
    void oneSelectedOutcomeOwnsWeaponOrGenericCustomHealthDamage()
            throws IOException {
        String incoming = method(
                source("player/PlayerDataEventHandler.java"),
                "public static void onPlayerHurt("
        );
        String evaluator = method(
                source("combat/WeaponCombatEvents.java"),
                "private static IncomingWeaponResolution evaluateWeaponHit("
        );

        assertOrdered(
                evaluator,
                "WeaponSkillContextStore.consumePending(",
                "if (releaseWeapon == null) return null;",
                "ItemStack weapon = releaseWeapon.weapon();",
                "DamageCalculator",
                ".createPlayerDamageRequest(",
                "customProtection.defense()",
                "DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE",
                "\"incoming_event\"",
                "\"shelter\"",
                "DamagePipeline.evaluate(damageRequest.build())",
                "new EvaluatedWeaponHit("
        );
        assertEquals(1, occurrences(
                evaluator,
                "DamagePipeline.evaluate(damageRequest.build())"
        ));

        assertOrdered(
                incoming,
                "new WeaponCombatEvents.CustomHealthProtection(",
                "incomingEventMultiplier,",
                "shelterMultiplier,",
                "bombMultiplier,",
                "difficultyMultiplier,",
                "totalDefense",
                "EvaluatedWeaponHit weaponHit = weaponResolution.hit();",
                "DamageOutcome incomingOutcome = weaponHit != null",
                "? weaponHit.outcome()",
                ": DamagePipeline.evaluate(incomingDamage.build());",
                "incomingOutcome.getFinalDamage()"
        );
        assertEquals(1, occurrences(
                incoming,
                "DamagePipeline.evaluate(incomingDamage.build())"
        ));
    }

    @Test
    void customAppliedUsesActualHealthLossAndSettlesBeforeNativePost()
            throws IOException {
        String incoming = method(
                source("player/PlayerDataEventHandler.java"),
                "public static void onPlayerHurt("
        );
        String customApplied = method(
                source("combat/WeaponCombatEvents.java"),
                "public static void applyCustomHealthWeaponHit("
        );

        assertOrdered(
                incoming,
                "int oldSdHealth = data.getHealth();",
                "int newSdHealth = Math.max(0, oldSdHealth - sdDamage);",
                "data.setHealth(newSdHealth);",
                "WeaponCombatEvents.applyCustomHealthWeaponHit(",
                "weaponHit,",
                "oldSdHealth - newSdHealth,",
                "AuthoredDirectDamageEvents.onAppliedDamage(",
                "oldSdHealth - newSdHealth"
        );
        assertEquals(1, occurrences(
                incoming,
                "WeaponCombatEvents.applyCustomHealthWeaponHit("
        ));
        assertEquals(1, occurrences(
                incoming,
                "AuthoredDirectDamageEvents.onAppliedDamage("
        ));

        assertOrdered(
                customApplied,
                "appliedDamage <= 0.0F",
                "hit.preparationReservation().commit();",
                "WeaponEvaluatedHitCoordinator.apply(hit);",
                "DamageNumberContextStore.consume(",
                "WeaponAppliedHitCoordinator.apply(new ResolvedWeaponHit(",
                "appliedDamage"
        );
        assertFalse(customApplied.contains("LivingDamageEvent.Post"));
        assertFalse(customApplied.contains("event.getNewDamage()"));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        int cursor = 0;
        while ((cursor = value.indexOf(token, cursor)) >= 0) {
            count++;
            cursor += token.length();
        }
        return count;
    }

    private static void assertOrdered(String value, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = value.indexOf(token, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + token);
            previous = current;
        }
    }

    private static String method(String value, String signature) {
        int start = value.indexOf(signature);
        assertTrue(start >= 0, "Missing method: " + signature);
        int openingBrace = value.indexOf('{', start);
        assertTrue(openingBrace > start, "Missing method body: " + signature);
        int depth = 0;
        for (int index = openingBrace; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return value.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("Unclosed method: " + signature);
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

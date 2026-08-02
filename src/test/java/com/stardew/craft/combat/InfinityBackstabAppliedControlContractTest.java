package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfinityBackstabAppliedControlContractTest {
    @Test
    void exactPositiveAppliedHitRecordsOnlyIntoTheActiveCast()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        String handler = source(
                "combat/skill/handler/"
                        + "InfinityDaggerSingularityBackstabSkillHandler.java"
        );

        String applied = method(
                rules,
                "static void recordInfinityDaggerSingularityBackstab("
        );
        assertOrdered(
                applied,
                "\"infinity_dagger_singularity_backstab\""
                        + ".equals(hit.skillId())",
                "hit.dealtPositiveDamage()",
                "hit.attacker() instanceof ServerPlayer player",
                "InfinityDaggerSingularityBackstabSkillHandler"
                        + ".recordAppliedHit("
        );
        assertTrue(coordinator.contains(
                ".recordInfinityDaggerSingularityBackstab(hit)"
        ));

        String facade = method(
                handler,
                "public static boolean recordAppliedHit("
        );
        String compactFacade = facade.replaceAll("\\s+", "");
        assertTrue(compactFacade.contains(
                "BuiltinWeaponSkillHandlers"
                        + ".INFINITY_DAGGER_SINGULARITY_BACKSTAB"
        ));
        assertTrue(facade.contains(
                "InfinityDaggerSingularityBackstabExecutionState.class"
        ));
        assertTrue(facade.contains(
                "state.recordAppliedHit(target.getUUID())"
        ));
    }

    @Test
    void controlSettlesOnceAfterBothSynchronousDamageAttempts()
            throws IOException {
        String handler = source(
                "combat/skill/handler/"
                        + "InfinityDaggerSingularityBackstabSkillHandler.java"
        );
        String begin = method(handler, "public void begin(");
        String compactBegin = begin.replaceAll("\\s+", "");

        assertOrdered(
                begin,
                "new InfinityDaggerSingularityBackstabExecutionState(",
                "instance.initializeExecutionState(executionState)",
                "instance.registerCommittedEffect(",
                "teleportPlayer(context.player(), plan.destination())",
                "attack(",
                "if (shouldStrikeSecond(target.isAlive()))",
                "attack(",
                "if (executionState.settleControl())",
                "YetiFreezeTracker.applyWithEquipmentProtection("
        );
        assertTrue(begin.contains("FREEZE_DURATION_TICKS"));
        assertTrue(compactBegin.contains(
                "YetiFreezeTracker.PresentationPolicy"
                        + ".SYNC_FREEZE_OVERLAY"
        ));
        assertFalse(begin.contains("boolean hit ="));
        assertFalse(begin.contains("if (WeaponSkillDamage.apply("));
        assertTrue(handler.contains(
                "WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT"
        ));
        assertTrue(handler.contains(
                "WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA"
        ));
        assertTrue(handler.contains(".BYPASS_FOR_AUTHORED_SEQUENCE"));
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

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + needle);
            previous = current;
        }
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

package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesperatePlunderAppliedPostContractTest {
    @Test
    void rewardSettlementConsumesOnlyTheExactAppliedKillOutcome()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String handler = source(
                "combat/skill/handler/DesperatePlunderSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/DesperatePlunderExecutionState.java"
        );

        String applied = method(
                rules,
                "static void recordDesperatePlunderOutcome("
        );
        assertTrue(applied.contains(
                "\"desperate_plunder\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains("hit.killedByAttacker()"));
        assertTrue(applied.contains(
                "DesperatePlunderSkillHandler.recordAppliedHit("
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules."
                        + "recordDesperatePlunderOutcome(hit)"
        ));

        String begin = method(handler, "public void begin(");
        assertTrue(begin.contains(
                "new DesperatePlunderExecutionState("
        ));
        assertTrue(begin.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertOrdered(
                begin,
                "WeaponSkillDamage.apply(",
                "executionState.settleKilledByThisHit()",
                "healAfterKill(context)",
                "grantFury(context)"
        );
        assertFalse(begin.contains("target.isDeadOrDying()"));
        assertFalse(begin.contains("target.getHealth()"));

        assertTrue(state.contains("targetId.equals(appliedTargetId)"));
        assertTrue(state.contains("killedByThisHit |= killedByAttacker"));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.DESPERATE_PLUNDER"
        ));
    }

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + needle);
            previous = current;
        }
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

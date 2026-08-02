package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplarJudgementSettlementAppliedPostContractTest {
    @Test
    void settlementSuccessImpactRequiresExactPositiveAppliedDamage()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String state = source(
                "combat/skill/handler/TemplarJudgementExecutionState.java"
        );

        String applied = method(
                rules,
                "static void emitTemplarJudgementSettlementImpact("
        );
        assertTrue(applied.contains(
                "\"templar_judgement\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "new TemplarJudgementImpactPayload(hit.target().getId())"
        ));
        assertTrue(applied.contains("SoundEvents.PLAYER_ATTACK_CRIT"));
        assertTrue(coordinator.contains(
                "emitTemplarJudgementSettlementImpact(hit)"
        ));

        String settlement = method(
                state,
                "private void settle(SkillExecutionContext context)"
        );
        assertTrue(settlement.contains("WeaponSkillDamage.apply("));
        assertTrue(settlement.contains("weaponSnapshot"));
        assertTrue(settlement.contains(".RESPECT_AT_IMPACT"));
        assertTrue(settlement.contains(
                ".BYPASS_FOR_AUTHORED_SEQUENCE"
        ));
        assertFalse(settlement.contains("TemplarJudgementImpactPayload"));
        assertFalse(settlement.contains("PLAYER_ATTACK_CRIT"));
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

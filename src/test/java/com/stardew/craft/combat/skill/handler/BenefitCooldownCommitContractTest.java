package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BenefitCooldownCommitContractTest {
    @Test
    void bloodDebtFinishesAgainstTheActiveStateAndRuntimeTransaction()
            throws IOException {
        String finish = method(
                readHandler("DarkSwordBloodDebtSkillHandler.java"),
                "public void finish("
        );

        int stateCheck = finish.indexOf(
                "instance.executionState("
        );
        int cancel = finish.indexOf(
                "DarkSwordBloodDebtExecutionState::cancel"
        );
        assertTrue(stateCheck >= 0 && cancel > stateCheck);
        assertTrue(finish.contains(
                "shouldCommitCooldown(reason, stateWasActive)"
        ));
        assertTrue(finish.contains(
                "WeaponSkillRuntime.commitCooldown("
        ));
    }

    @Test
    void forestBlessingMarksActivationBeforeApplyingItsBenefits()
            throws IOException {
        String source = readHandler("ForestBlessingSkillHandler.java");
        String activate = method(source, "private static void activateBlessing(");
        String finish = method(source, "public void finish(");

        int activated = activate.indexOf("state.activated = true;");
        int attack = activate.indexOf("WeaponSkillDamage.apply(");
        int payload = activate.indexOf("new ForestBlessingPayload(");
        assertTrue(activated >= 0 && attack > activated && payload > activated);
        assertTrue(finish.contains(
                "shouldCommitCooldown(reason, state.activated)"
        ));
        assertTrue(finish.contains(
                "WeaponSkillRuntime.commitCooldown("
        ));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        assertTrue(openingBrace > start, "Missing method body " + signature);

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

    private static String readHandler(String fileName) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "handler",
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

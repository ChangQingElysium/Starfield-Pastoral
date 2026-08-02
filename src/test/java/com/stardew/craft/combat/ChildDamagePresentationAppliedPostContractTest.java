package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChildDamagePresentationAppliedPostContractTest {
    @Test
    void obsidianAndCrystalPresentationRequireTheirExactPositiveChildHit()
            throws IOException {
        String rules = source("BuiltinWeaponPassiveAppliedHitRules.java");
        String coordinator = source("WeaponAppliedHitCoordinator.java");

        assertExactPositivePresentation(
                rules,
                "static void emitObsidianResonancePresentation("
                        + "ResolvedWeaponHit hit)",
                "\"obsidian_resonance\".equals(hit.skillId())",
                "LegacyWeaponHitPresentation.emitObsidianResonance("
        );
        assertExactPositivePresentation(
                rules,
                "static void emitCrystalDaggerBurstPresentation("
                        + "ResolvedWeaponHit hit)",
                "\"crystal_dagger_burst\".equals(hit.skillId())",
                "new CrystalDaggerBurstPayload()"
        );
        assertTrue(coordinator.contains(
                ".emitObsidianResonancePresentation(hit)"
        ));
        assertTrue(coordinator.contains(
                ".emitCrystalDaggerBurstPresentation(hit)"
        ));
    }

    @Test
    void parentRulesOnlyConsumeResourcesAndDispatchVoidChildDamage()
            throws IOException {
        String rules = source("BuiltinWeaponPassiveAppliedHitRules.java");
        String obsidian = method(
                rules,
                "static void consumeObsidianResonance(ResolvedWeaponHit hit)"
        );
        String crystal = method(
                rules,
                "static void triggerCrystalBurst(ResolvedWeaponHit hit)"
        );

        assertTrue(obsidian.contains("applyChildDamage("));
        assertTrue(crystal.contains("applyChildDamage("));
        assertFalse(obsidian.contains(
                "LegacyWeaponHitPresentation.emitObsidianResonance("
        ));
        assertFalse(crystal.contains("new CrystalDaggerBurstPayload()"));
        assertFalse(rules.contains("boolean hurt = applyChildDamage("));
        assertTrue(rules.contains("private static void applyChildDamage("));
        assertFalse(rules.contains("return WeaponSkillDamage.apply("));
        assertTrue(rules.contains(
                "WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE"
        ));
        assertTrue(rules.contains(
                "WeaponSkillDamage.HitCooldownPolicy\n"
                        + "                        .BYPASS_FOR_AUTHORED_SEQUENCE"
        ));
    }

    private static void assertExactPositivePresentation(
            String source,
            String signature,
            String skillGate,
            String presentation
    ) {
        String method = method(source, signature);
        assertOrdered(
                method,
                skillGate,
                "hit.dealtPositiveDamage()",
                presentation
        );
        assertFalse(method.contains("WeaponSkillDamage.apply("));
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

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + needle);
            previous = current;
        }
    }

    private static String source(String fileName) throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "combat", fileName
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

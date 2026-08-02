package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponDamageAssemblyRulesContractTest {
    @Test
    void sharedResolverDelegatesOneOrderedAssemblyBeforeEvaluation()
            throws IOException {
        String events = source("WeaponCombatEvents.java");
        String resolver = method(
                events,
                "private static IncomingWeaponResolution evaluateWeaponHit(",
                "public static void onLivingHurt(LivingDamageEvent.Pre event)"
        );
        int prepare = resolver.indexOf("WeaponHitPreparation.prepare(");
        int reserve = resolver.indexOf("WeaponHitPreparation.reserve(");
        int assemble = resolver.indexOf("WeaponDamageAssemblyRules.apply(");
        int evaluate = resolver.indexOf(
                "DamagePipeline.evaluate(damageRequest.build())"
        );

        assertTrue(prepare >= 0 && reserve > prepare);
        assertTrue(assemble > reserve);
        assertTrue(evaluate > assemble);
        String beforeEvaluation = resolver.substring(assemble, evaluate);
        assertFalse(beforeEvaluation.contains("DamageAdjustment."));
        assertFalse(beforeEvaluation.contains("StardewEnchantments."));
    }

    @Test
    void modifierOrderIsExplicitAndStable() throws IOException {
        String rules = source("WeaponDamageAssemblyRules.java");
        assertIncreasing(rules, List.of(
                "request.defense(0.0F, false)",
                "\"steel_spine_bonus\"",
                "\"dark_sword_blood_moon\"",
                "\"enchantment_bug_killer\"",
                "\"enchantment_crusader\"",
                "\"dragontooth_slime_slayer\""
        ));
        assertFalse(rules.contains("minecraft_native_protection"));
        assertFalse(rules.contains("nativeProtectionRatio"));
        assertFalse(rules.contains("originalNativeDamage"));
        assertFalse(rules.contains("damageAfterNativeProtection"));
        assertFalse(rules.contains("sword_sweep"));
        assertFalse(rules.contains("SWEEPING_EDGE"));
        assertFalse(rules.contains("HashMap"));
        assertFalse(rules.contains("getMainHandItem()"));
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

    private static String method(
            String source,
            String startToken,
            String endToken
    ) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue(start >= 0 && end > start, startToken);
        return source.substring(start, end);
    }
}

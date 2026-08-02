package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileAppliedHitSideEffectContractTest {
    @Test
    void iceSpineDamageControlRequiresPositiveAppliedDamage()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String entity = source("entity/effect/IceSpineEffectEntity.java");
        String handler = source(
                "combat/skill/handler/YetiToothSpineSkillHandler.java"
        );

        String applied = method(
                rules,
                "static void applyYetiToothSpineControl("
        );
        assertTrue(applied.contains(
                "\"yeti_tooth_spine\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "YetiToothSpineSkillHandler.applySpineControl(hit.target())"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyYetiToothSpineControl(hit)"
        ));

        String collision = method(entity, "private void handleHit(");
        assertTrue(collision.contains("if (directFreezeTicks > 0)"));
        assertTrue(collision.contains("YetiToothEffects.applyFreeze("));
        assertTrue(collision.contains("directFreezeTicks"));
        assertFalse(collision.contains("YetiToothEffects.applySlow("));
        assertFalse(collision.contains(
                "YetiToothEffects.applyFreeze(serverLevel, target, 60)"
        ));
        assertTrue(handler.contains("HIT_SLOW_DURATION_TICKS = 40"));
        assertTrue(handler.contains("HIT_FREEZE_DURATION_TICKS = 60"));
        assertTrue(handler.contains("target.hasEffect("));
    }

    @Test
    void elfLeafMarkRequiresPositiveAppliedProjectileDamage()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String entity = source(
                "entity/projectile/ElfBladeLeafEntity.java"
        );
        String handler = source(
                "combat/skill/handler/ElfBladeLeafSkillHandler.java"
        );

        String applied = method(rules, "static void applyElfBladeLeafMark(");
        assertTrue(applied.contains(
                "\"elf_blade_leaf\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "ElfBladeLeafSkillHandler.applyLeafMark("
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyElfBladeLeafMark(hit)"
        ));
        assertFalse(entity.contains("ElfBladeMarkTracker"));
        assertTrue(handler.contains("MARK_DURATION_TICKS = 140"));
        assertTrue(handler.contains("MARK_STACKS_PER_HIT = 1"));
        assertTrue(handler.contains("ElfBladeMarkTracker.apply("));
    }

    @Test
    void billetRingRequiresPositiveRootDamageAndCannotRecurse()
            throws IOException {
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source("combat/WeaponAppliedHitCoordinator.java");
        String projectile = source(
                "entity/projectile/TemperedBilletProjectileEntity.java"
        );
        String handler = source(
                "combat/skill/handler/TemperedBilletSkillHandler.java"
        );
        String ring = source("combat/skill/TemperedFireRingTracker.java");

        String applied = method(
                rules,
                "static void startTemperedBilletFireRing("
        );
        assertTrue(applied.contains(
                "\"tempered_billet\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains("hit.weaponSnapshot().orElse(null)"));
        assertTrue(applied.contains(
                "TemperedBilletSkillHandler.startFireRing("
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.startTemperedBilletFireRing(hit)"
        ));
        assertFalse(projectile.contains("TemperedFireRingTracker"));
        assertTrue(handler.contains("FIRE_RING_RADIUS = 2.5F"));
        assertTrue(handler.contains("FIRE_RING_DURATION_TICKS = 10"));
        assertTrue(handler.contains("weaponSnapshot == null"));
        assertTrue(ring.contains("DAMAGE_SKILL_ID"));
        assertTrue(ring.contains("\"tempered_billet_fire_ring\""));
        assertTrue(ring.contains(".skillId(DAMAGE_SKILL_ID)"));
        assertFalse(ring.contains(".skillId(\"tempered_billet\")"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int openingBrace = source.indexOf('{', start);
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

    private static String source(String relative) throws IOException {
        Path root = javaRoot();
        return Files.readString(root.resolve(relative));
    }

    private static Path javaRoot() throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate main Java source root");
    }
}

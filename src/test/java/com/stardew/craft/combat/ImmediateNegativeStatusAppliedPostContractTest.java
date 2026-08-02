package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmediateNegativeStatusAppliedPostContractTest {
    @Test
    void tideReelSettlesItsCompositeControlOnceAfterPositiveDamage()
            throws IOException {
        String handler = source(
                "combat/skill/handler/TideReelSkillHandler.java"
        );
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        String begin = method(handler, "public void begin(");
        String applied = method(rules, "static void applyTideReel(");
        String stateApplied = method(
                handler,
                "private synchronized boolean onAppliedHit("
        );

        assertTrue(begin.contains(
                "new State(target.getUUID(), fishCatchActive)"
        ));
        assertTrue(begin.contains("WeaponSkillDamage.apply("));
        assertTrue(begin.contains(".RESPECT_AT_IMPACT"));
        assertFalse(begin.contains("MobEffects.MOVEMENT_SLOWDOWN"));
        assertFalse(begin.contains("pullTarget("));

        assertTrue(applied.contains(
                "\"tide_reel\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "TideReelSkillHandler.onAppliedHit(player, hit.target())"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules.applyTideReel(hit)"
        ));

        assertTrue(stateApplied.contains(
                "!targetId.equals(target.getUUID())"
        ));
        assertTrue(stateApplied.contains("consumed = true;"));
        assertEquals(
                1,
                occurrences(
                        stateApplied,
                        "EquipmentNegativeStatusProtection.decide("
                )
        );
        assertTrue(stateApplied.contains(
                "EquipmentMobEffectHandler.addPreAdjustedEffect("
        ));
        assertTrue(stateApplied.contains(
                "protection.durationTicks()"
        ));
        assertTrue(stateApplied.contains(
                "pullTarget(player, target, fishCatchActive)"
        ));
    }

    @Test
    void dragontoothStabFreezesOnlyAfterPositiveDamage()
            throws IOException {
        assertFreezeMovedToAppliedPost(
                "DragontoothShivStabSkillHandler.java",
                "applyDragontoothShivStab",
                "dragontooth_shiv_stab",
                "DragontoothShivStabSkillHandler.FREEZE_DURATION_TICKS"
        );
    }

    @Test
    void galaxyStarleapFreezesOnlyAfterPositiveDamage()
            throws IOException {
        assertFreezeMovedToAppliedPost(
                "GalaxyDaggerStarleapSkillHandler.java",
                "applyGalaxyDaggerStarleap",
                "galaxy_dagger_starleap",
                "GalaxyDaggerStarleapSkillHandler.FREEZE_DURATION_TICKS"
        );
    }

    private static void assertFreezeMovedToAppliedPost(
            String handlerFile,
            String ruleMethod,
            String skillId,
            String durationConstant
    ) throws IOException {
        String handler = source(
                "combat/skill/handler/" + handlerFile
        );
        String rules = source("combat/BuiltinSkillAppliedHitRules.java");
        String coordinator = source(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        String begin = method(handler, "public void begin(");
        String applied = method(
                rules,
                "static void " + ruleMethod + "("
        );

        assertTrue(begin.contains("WeaponSkillDamage.apply("));
        assertTrue(begin.contains(".RESPECT_AT_IMPACT"));
        assertFalse(begin.contains(
                "YetiFreezeTracker.applyWithEquipmentProtection("
        ));

        assertTrue(applied.contains(
                "\"" + skillId + "\".equals(hit.skillId())"
        ));
        assertTrue(applied.contains("hit.dealtPositiveDamage()"));
        assertTrue(applied.contains(
                "YetiFreezeTracker.applyWithEquipmentProtection("
        ));
        assertTrue(applied.contains(durationConstant));
        assertTrue(applied.contains(
                "YetiFreezeTracker.PresentationPolicy.SYNC_FREEZE_OVERLAY"
        ));
        assertTrue(coordinator.contains(
                "BuiltinSkillAppliedHitRules." + ruleMethod + "(hit)"
        ));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
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

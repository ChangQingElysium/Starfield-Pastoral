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
    void centralDamageConsumesPendingSnapshotBeforeCheckingCurrentHand()
            throws IOException {
        String source = readSource("WeaponCombatEvents.java");
        int method = source.indexOf(
                "public static void onLivingHurt(LivingDamageEvent.Pre event)"
        );
        int consume = source.indexOf(
                "WeaponSkillContextStore.consumePending(player, nowTick)",
                method
        );
        int currentHand = source.indexOf(
                "player.getMainHandItem()",
                method
        );
        int weaponGate = source.indexOf(
                "if (!(weapon.getItem() instanceof IStardewWeapon)) return;",
                method
        );

        assertTrue(method >= 0);
        assertTrue(consume > method);
        assertTrue(currentHand > consume);
        assertTrue(weaponGate > currentHand);
        assertTrue(source.contains(
                "DamageNumberContextStore.set(\n"
                        + "                player,\n"
                        + "                skillId,\n"
                        + "                displayCrit,\n"
                        + "                damageWeaponSnapshot,"
        ));
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
        String source = readSource("WeaponCombatEvents.java");

        assertChildContextUsesCentralDamage(
                source,
                ".skillId(\"crystal_dagger_burst\")"
        );
        assertChildContextUsesCentralDamage(
                source,
                ".skillId(\"singularity_followup\")"
        );
        assertChildContextUsesCentralDamage(
                source,
                ".skillId(\"ossified_mark_bonus\")"
        );
        assertChildContextUsesCentralDamage(
                source,
                ".skillId(\"galaxy_dagger_mark_bonus\")"
        );
        assertChildContextUsesCentralDamage(
                source,
                ".skillId(\"infinity_dagger_mark_bonus\")"
        );

        int tideContext = source.indexOf(
                "TideMarkTracker.createBonusContext()"
        );
        assertChildContextUsesCentralDamage(source, tideContext);
        assertEquals(
                6,
                occurrences(
                        source,
                        "WeaponSkillDamage.apply("
                )
        );
        assertFalse(source.contains("player.attack(target)"));
        assertFalse(source.contains("WeaponSkillContextStore.setPending("));
        assertFalse(source.contains("clearUnconsumedSkillContext("));
        assertFalse(source.contains("AttackGatePolicy"));

        int burst = source.indexOf(".skillId(\"crystal_dagger_burst\")");
        int burstDamage = source.indexOf("WeaponSkillDamage.apply(", burst);
        int burstPayload = source.indexOf(
                "new CrystalDaggerBurstPayload()",
                burst
        );
        assertTrue(burstDamage > burst && burstPayload > burstDamage);
    }

    private static void assertChildContextUsesCentralDamage(
            String source,
            String contextMarker
    ) {
        int context = source.indexOf(contextMarker);
        assertTrue(context >= 0, contextMarker);
        assertChildContextUsesCentralDamage(source, context);
    }

    private static void assertChildContextUsesCentralDamage(
        String source,
        int context
    ) {
        assertTrue(context >= 0);
        int damageBefore = source.lastIndexOf(
                "WeaponSkillDamage.apply(",
                context
        );
        int damageAfter = source.indexOf(
                "WeaponSkillDamage.apply(",
                context
        );
        int damage = damageBefore >= 0 && context - damageBefore < 300
                ? damageBefore
                : damageAfter;
        int snapshot = source.indexOf("damageWeaponSnapshot", context);
        int resetInvulnerability = source.lastIndexOf(
                "target.invulnerableTime = 0;",
                damage
        );
        int resetHurt = source.lastIndexOf(
                "target.hurtTime = 0;",
                damage
        );

        assertTrue(damage >= context - 300 && damage - context < 800);
        assertTrue(snapshot > damage && snapshot - context < 800);
        assertTrue(
                resetInvulnerability >= context - 300
                        && resetInvulnerability < damage
        );
        assertTrue(resetHurt > resetInvulnerability && resetHurt < damage);
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

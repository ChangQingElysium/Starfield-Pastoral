package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponHitPreparationContractTest {
    @Test
    void eventAdapterDelegatesOrderedContextTransformation()
            throws IOException {
        String events = readMainSource("combat/WeaponCombatEvents.java");
        String resolver = method(
                events,
                "private static IncomingWeaponResolution evaluateWeaponHit(",
                "public static void onLivingHurt(LivingDamageEvent.Pre event)"
        );
        String pre = method(
                events,
                "public static void onLivingHurt(LivingDamageEvent.Pre event)",
                "public static CustomHealthWeaponResolution "
                        + "evaluateCustomHealthWeaponHit("
        );

        assertIncreasing(resolver, List.of(
                "DimensionDamageMapper.isInStardewDimension(target)",
                "WeaponHitPreparation.prepare(",
                "WeaponHitPreparation.reserve(",
                "preparation.skillContext()",
                "preparation.steelSpineBoost()"
        ));
        assertIncreasing(pre, List.of(
                "WeaponIncomingHitStore.consume(",
                "hit.preparationReservation().commit()",
                "WeaponEvaluatedHitCoordinator.apply(hit)"
        ));
        assertFalse(events.contains(
                "InsectEyeStanceSkillHandler.consumeAttack("
        ));
        assertFalse(events.contains(
                "SteelSpineFurySkillHandler.consumeAttack("
        ));
        assertFalse(events.contains("CrystalDaggerLayerTracker.getStacks("));
        assertFalse(events.contains("isBackstab("));
    }

    @Test
    void contextRulesHaveOneExplicitAuthoredOrder() throws IOException {
        String preparation = readMainSource(
                "combat/WeaponHitPreparation.java"
        );
        assertIncreasing(preparation, List.of(
                "InsectEyeStanceSkillHandler.reserveAttack(",
                "InsectEyeStanceSkillHandler.consumeAttack(",
                "SteelSpineFurySkillHandler.reserveAttack(",
                "SteelSpineFurySkillHandler.consumeAttack(",
                "\"crystal_dagger\".equals(weaponId)",
                "\"dragontooth_cutlass\".equals(weaponId)",
                "\"dragontooth_shiv\".equals(weaponId)",
                "\"iridium_needle\".equals(weaponId)"
        ));
        assertFalse(preparation.contains("HashMap"));
        assertFalse(preparation.contains("HashSet"));
        assertFalse(preparation.contains("getMainHandItem()"));
    }

    @Test
    void reservationSettlesExactlyOnceAndReleasesInReverseOrder()
            throws IOException {
        String preparation = readMainSource(
                "combat/WeaponHitPreparation.java"
        );
        String reservation = method(
                preparation,
                "public static final class Reservation",
                "private static SkillContext.Builder copy("
        );

        assertTrue(reservation.contains("private boolean settled;"));
        assertTrue(reservation.contains("if (settled) return;"));
        assertTrue(reservation.contains("settled = true;"));
        assertTrue(reservation.contains("commits.forEach(Runnable::run);"));
        assertIncreasing(reservation, List.of(
                "for (int index = releases.size() - 1; index >= 0; index--)",
                "releases.get(index).run()"
        ));
    }

    @Test
    void everyAmendmentCopiesAllExistingSkillSemantics()
            throws IOException {
        String preparation = readMainSource(
                "combat/WeaponHitPreparation.java"
        );
        for (String field : List.of(
                ".skillId(context.getSkillId())",
                ".tier(context.getTier())",
                ".damageMultiplier(context.getDamageMultiplier())",
                ".ignoreDefense(context.isIgnoreDefense())",
                ".guaranteedCrit(context.isGuaranteedCrit())",
                ".critChanceBonus(context.getCritChanceBonus())",
                ".defaultKnockback(context.usesDefaultKnockback())"
        )) {
            assertTrue(preparation.contains(field), field);
        }
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

    private static String method(
            String source,
            String startToken,
            String endToken
    ) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String readMainSource(String relative) throws IOException {
        Path sourceRoot = locate(Path.of(
                "src", "main", "java", "com", "stardew", "craft"
        ));
        return Files.readString(sourceRoot.resolve(relative));
    }

    private static Path locate(Path relative) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}

package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemperedReleaseSnapshotContractTest {
    @Test
    void quenchBlastRetainsTheParentHitWeapon() throws IOException {
        String appliedRule = normalizedSource(
                "combat/BuiltinSkillAppliedHitRules.java"
        );
        String state = normalizedSource(
                "combat/skill/handler/TemperedQuenchExecutionState.java"
        );

        assertTrue(appliedRule.contains(
                "TemperedQuenchSkillHandler.armBlast( player, hit.target(), "
                        + "hit.gameTick(), "
                        + "TemperedQuenchSkillHandler.BLAST_DELAY_TICKS, "
                        + "hit.weaponSnapshot().orElseThrow() )"
        ));
        assertTrue(state.contains(
                "record PendingBlast("
        ));
        assertTrue(state.contains(
                "WeaponDamageSnapshot weaponSnapshot"
        ));
        assertTrue(state.contains(
                "WeaponSkillDamage.apply( context.player(), target, "
                        + "createBlastContext(), weaponSnapshot,"
        ));
    }

    @Test
    void billetProjectilePersistsAndForwardsItsReleaseWeapon()
            throws IOException {
        String handler = normalizedSource(
                "combat/skill/handler/TemperedBilletSkillHandler.java"
        );
        String projectile = normalizedSource(
                "entity/projectile/TemperedBilletProjectileEntity.java"
        );
        String appliedRules = normalizedSource(
                "combat/BuiltinSkillAppliedHitRules.java"
        );

        assertTrue(handler.contains(
                "WeaponDamageSnapshot releaseWeaponSnapshot "
                        + "= context.weaponSnapshot()"
        ));
        assertTrue(handler.contains(
                "PROJECTILE_STATE_TICKS, releaseWeaponSnapshot"
        ));
        assertTrue(handler.contains(
                "target, releaseWeaponSnapshot"
        ));
        assertEquals(3, occurrences(
                projectile,
                "public TemperedBilletProjectileEntity("
        ));
        assertTrue(projectile.contains(
                "private WeaponDamageSnapshot releaseWeaponSnapshot;"
        ));
        assertTrue(projectile.contains(
                "weapon.saveOptional(registries)"
        ));
        assertTrue(projectile.contains(
                "ItemStack.parseOptional("
        ));
        assertTrue(projectile.contains(
                "tag.putString(\"ReleaseWeaponId\""
        ));
        assertTrue(projectile.contains(
                "tag.put(\"ReleaseWeapon\""
        ));
        assertTrue(appliedRules.contains(
                "TemperedBilletSkillHandler.startFireRing( player, "
                        + "hit.target(), hit.gameTick(), "
                        + "hit.weaponSnapshot().orElse(null) )"
        ));
        assertTrue(handler.contains(
                "FIRE_RING_DURATION_TICKS, weaponSnapshot"
        ));
        assertCentralizedSnapshotBinding(projectile);
    }

    @Test
    void fireRingBindsEveryDelayedTargetToTheReleaseWeapon()
            throws IOException {
        String tracker = normalizedSource(
                "combat/skill/TemperedFireRingTracker.java"
        );

        assertEquals(2, occurrences(
                tracker,
                "public static void beginBilletCast("
        ));
        assertEquals(2, occurrences(
                tracker,
                "public static void start("
        ));
        assertTrue(tracker.contains(
                "private record BilletCastState( "
                        + "ResourceKey<Level> dimension, long endTick, "
                        + "WeaponDamageSnapshot weaponSnapshot"
        ));
        assertTrue(tracker.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertTrue(tracker.contains(
                "ring.weaponSnapshot, nowTick "
                        + "+ HIT_CONTEXT_LIFETIME_TICKS"
        ));
        assertCentralizedSnapshotBinding(tracker);
    }

    private static void assertCentralizedSnapshotBinding(String source) {
        assertTrue(source.contains(
                "WeaponSkillDamage.apply("
        ));
        assertTrue(source.contains(
                "releaseWeaponSnapshot"
        ) || source.contains(
                "ring.weaponSnapshot"
        ));
        assertTrue(!source.contains("playerAttack("));
        assertTrue(!source.contains(
                "WeaponSkillContextStore.setPending("
        ));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }

    private static String normalizedSource(String relativeFile)
            throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativeFile);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate)
                        .replaceAll("\\s+", " ");
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}

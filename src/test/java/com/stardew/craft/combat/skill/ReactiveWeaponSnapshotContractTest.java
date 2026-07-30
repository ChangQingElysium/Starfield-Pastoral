package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveWeaponSnapshotContractTest {
    @Test
    void lightCounterRetainsAndBindsItsActivationWeapon()
            throws IOException {
        String runtime = normalizedSource(
                "combat/skill/handler/LightCounterSkillHandler.java"
        );
        String state = normalizedSource(
                "combat/skill/LightCounterParryState.java"
        );
        String parry = normalizedSource(
                "combat/skill/LightCounterParryHandler.java"
        );

        assertTrue(runtime.contains(
                "weaponId, context.weaponSnapshot()"
        ));
        assertTrue(state.contains(
                "Map<UUID, WeaponDamageSnapshot> WEAPON_SNAPSHOTS"
        ));
        assertTrue(parry.contains(
                "LightCounterParryState.getWeaponSnapshot(player)"
        ));
        assertTrue(parry.contains(
                "player, attacker, context, weaponSnapshot, nowTick + 5, "
                        + "WeaponSkillDamage.AttackGatePolicy"
                        + ".RESPECT_AT_IMPACT"
        ));
        assertTrue(parry.contains(
                "player, attacker, context, nowTick + 5, "
                        + "WeaponSkillDamage.AttackGatePolicy"
                        + ".RESPECT_AT_IMPACT"
        ));
        assertRejectedHitCleanup(parry);
    }

    @Test
    void silverFoldbackRetainsTheInitialReleaseForStayStrike()
            throws IOException {
        String runtime = normalizedSource(
                "combat/skill/handler/SilverFoldbackSkillHandler.java"
        );
        String state = normalizedSource(
                "combat/skill/SilverSaberFoldbackState.java"
        );
        String helper = normalizedSource(
                "combat/skill/SilverSaberSkillHelper.java"
        );

        assertTrue(runtime.contains(
                "context.nowTick(), context.weaponSnapshot()"
        ));
        assertTrue(state.contains(
                "Map<UUID, WeaponDamageSnapshot> WEAPON_SNAPSHOTS"
        ));
        int snapshotRead = helper.indexOf(
                "SilverSaberFoldbackState.getWeaponSnapshot(player)"
        );
        int stateExit = helper.indexOf(
                "exitFoldbackState(player)",
                snapshotRead
        );
        assertTrue(snapshotRead >= 0 && stateExit > snapshotRead);
        assertTrue(helper.contains(
                "player, target, context, weaponSnapshot, expireTick, "
                        + "WeaponSkillDamage.AttackGatePolicy"
                        + ".RESPECT_AT_IMPACT"
        ));
        assertTrue(helper.contains(
                "player, target, context, expireTick, "
                        + "WeaponSkillDamage.AttackGatePolicy"
                        + ".RESPECT_AT_IMPACT"
        ));
        assertRejectedHitCleanup(helper);
    }

    @Test
    void obsidianChildHitBindsTheParentHitsWeapon()
            throws IOException {
        String combat = normalizedSource(
                "combat/WeaponCombatEvents.java"
        );
        String tracker = normalizedSource(
                "combat/skill/ObsidianResonanceTracker.java"
        );

        assertTrue(combat.contains(
                "result.isCrit(), damageWeaponSnapshot"
        ));
        assertTrue(tracker.contains(
                "boolean firstCrit, WeaponDamageSnapshot weaponSnapshot"
        ));
        assertTrue(tracker.contains(
                "player, context, weaponSnapshot, expireTick"
        ) || tracker.contains(
                "player, target, context, "
                        + "weaponSnapshot, expireTick"
        ));
        assertRejectedHitCleanup(tracker);
    }

    private static void assertRejectedHitCleanup(String source) {
        if (source.contains("WeaponSkillDamage.apply(")) {
            return;
        }
        assertTrue(source.contains("} finally {"));
        assertTrue(source.contains(
                "clearUnconsumedContext(player, nowTick);"
        ));
        assertTrue(source.contains(
                "WeaponSkillContextStore.hasPending(player, nowTick)"
        ));
        assertTrue(source.contains(
                "WeaponSkillContextStore.consume(player, nowTick)"
        ));
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

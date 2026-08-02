package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveWeaponSnapshotContractTest {
    @Test
    void lightCounterReadsItsActivationWeaponFromTheRuntime()
            throws IOException {
        String handler = normalizedSource(
                "combat/skill/handler/LightCounterSkillHandler.java"
        );
        String state = normalizedSource(
                "combat/skill/handler/LightCounterExecutionState.java"
        );
        String parry = normalizedSource(
                "combat/skill/LightCounterParryHandler.java"
        );

        assertTrue(handler.contains("context.weaponSnapshot()"));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.LIGHT_COUNTER"
        ));
        assertTrue(state.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertFalse(state.contains("static final Map"));
        assertTrue(parry.contains(
                "LightCounterSkillHandler.consumeParry( player, nowTick )"
        ));
        assertTrue(parry.contains(
                "WeaponDamageSnapshot weaponSnapshot = "
                        + "activation.weaponSnapshot();"
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
        String appliedRule = normalizedSource(
                "combat/BuiltinWeaponPassiveAppliedHitRules.java"
        );
        String tracker = normalizedSource(
                "combat/skill/ObsidianResonanceTracker.java"
        );

        assertTrue(appliedRule.contains(
                "hit.weaponIdentity().builtIn()"
        ));
        assertTrue(appliedRule.contains(
                "\"obsidian_edge\".equals( "
                        + "hit.weaponIdentity().logicId() )"
        ));
        assertTrue(appliedRule.contains(
                "ObsidianResonanceTracker.consumeCharge( "
                        + "player, hit.gameTick() )"
        ));
        assertTrue(appliedRule.contains(
                "ObsidianResonanceTracker.createBonusContext( "
                        + "hit.damageOutcome().isCrit() )"
        ));
        assertTrue(appliedRule.contains(
                "WeaponDamageSnapshot snapshot = "
                        + "hit.weaponSnapshot().orElseThrow();"
        ));
        assertTrue(appliedRule.contains(
                "WeaponSkillDamage.apply( "
                        + "hit.attacker(), target, context, snapshot,"
        ));
        assertTrue(tracker.contains(
                ".skillId(\"obsidian_resonance\")"
        ));
        assertFalse(tracker.contains("WeaponSkillDamage.apply("));
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

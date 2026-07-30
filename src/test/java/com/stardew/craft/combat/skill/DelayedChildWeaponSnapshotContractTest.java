package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedChildWeaponSnapshotContractTest {
    @Test
    void persistedYetiSpinesOwnTheirReleaseWeapon()
            throws IOException {
        String handler = normalizedSource(
                "handler/YetiToothSpineSkillHandler.java"
        );
        String tracker = normalizedSource(
                "YetiToothSpineTracker.java"
        );
        String entity = normalizedMainSource(
                "entity/effect/IceSpineEffectEntity.java"
        );

        assertTrue(handler.contains(
                "YetiToothSpineTracker.start("
        ));
        assertTrue(handler.contains("context.weaponSnapshot()"));
        assertEquals(2, occurrences(
                tracker,
                "public static void start("
        ));
        assertTrue(tracker.contains(
                "skillId, weaponSnapshot"
        ));
        assertTrue(entity.contains(
                "private WeaponDamageSnapshot releaseWeaponSnapshot;"
        ));
        assertTrue(entity.contains(
                "context, releaseWeaponSnapshot, nowTick + 5"
        ));
        assertReleaseSnapshotPersistence(entity);
        assertInlinePendingCleanup(entity);
    }

    @Test
    void persistedElfLeavesOwnTheirReleaseWeapon()
            throws IOException {
        String handler = normalizedSource(
                "handler/ElfBladeLeafSkillHandler.java"
        );
        String tracker = normalizedSource(
                "ElfBladeTracker.java"
        );
        String entity = normalizedMainSource(
                "entity/projectile/ElfBladeLeafEntity.java"
        );

        assertTrue(handler.contains(
                "ElfBladeTracker.start("
        ));
        assertTrue(handler.contains("context.weaponSnapshot()"));
        assertEquals(2, occurrences(
                tracker,
                "public static void start("
        ));
        assertTrue(tracker.contains(
                "endTick, weaponSnapshot"
        ));
        assertTrue(entity.contains(
                "private WeaponDamageSnapshot releaseWeaponSnapshot;"
        ));
        assertTrue(entity.contains(
                "context, releaseWeaponSnapshot, nowTick + 5"
        ));
        assertReleaseSnapshotPersistence(entity);
        assertInlinePendingCleanup(entity);
    }

    @Test
    void lavaKatanaBurnPersistsAndBindsTheBrandReleaseWeapon()
            throws IOException {
        String brandHandler = normalizedSource(
                "handler/LavaKatanaBrandSkillHandler.java"
        );
        String reverbHandler = normalizedSource(
                "handler/LavaKatanaReverbSkillHandler.java"
        );
        String tracker = normalizedSource(
                "LavaKatanaMarkTracker.java"
        );

        assertEquals(2, occurrences(
                tracker,
                "public static void apply("
        ));
        assertTrue(brandHandler.contains(
                "LavaKatanaMarkTracker.prepareRelease( "
                        + "target, context.player(), "
                        + "context.weaponSnapshot()"
        ));
        assertTrue(brandHandler.contains(
                "LavaKatanaMarkTracker.discardPreparedRelease("
        ));
        assertTrue(reverbHandler.contains(
                "LavaKatanaMarkTracker.apply( "
                        + "target, context.player(), "
                        + "context.nowTick(), "
                        + "LavaKatanaMarkTracker.MARK_DURATION_TICKS, "
                        + "context.weaponSnapshot()"
        ));
        assertReleaseSnapshotPersistence(tracker);
        assertTrue(tracker.contains(
                "readWeaponSnapshot(target, tag)"
        ));
        assertExplicitSnapshotBinding(tracker);
        assertInlinePendingCleanup(tracker);
        assertHandlerSnapshotBinding(brandHandler);
    }

    @Test
    void wickedKrisDotAndBurstPersistAndBindTheirReleaseWeapon()
            throws IOException {
        String rippleHandler = normalizedSource(
                "handler/WickedKrisVenomRippleSkillHandler.java"
        );
        String burstHandler = normalizedSource(
                "handler/WickedKrisNestBurstSkillHandler.java"
        );
        String tracker = normalizedSource(
                "WickedKrisPoisonTracker.java"
        );

        assertEquals(2, occurrences(
                tracker,
                "public static void applyPoison("
        ));
        assertTrue(rippleHandler.contains(
                "WickedKrisPoisonTracker.applyPoison("
        ));
        assertTrue(rippleHandler.contains("context.weaponSnapshot()"));
        assertTrue(burstHandler.contains(
                "WickedKrisPoisonTracker.applyPoison("
        ));
        assertTrue(burstHandler.contains("context.weaponSnapshot()"));
        assertHandlerSnapshotBinding(rippleHandler);
        assertHandlerSnapshotBinding(burstHandler);
        assertReleaseSnapshotPersistence(tracker);
        assertTrue(tracker.contains(
                "private WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertTrue(tracker.contains(
                "state.weaponSnapshot = readWeaponSnapshot(target, tag);"
        ));
        assertTrue(tracker.contains(
                "state.weaponSnapshot, nowTick + 5"
        ));
        assertTrue(tracker.contains(
                "weaponSnapshot, nowTick + 5"
        ));
        assertExplicitSnapshotBinding(tracker);
        assertCentralizedDamageBinding(tracker);
    }

    @Test
    void bloodMoonBurstRetainsAndBindsItsReleaseWeapon()
            throws IOException {
        String handler = normalizedSource(
                "handler/DarkSwordBloodMoonSkillHandler.java"
        );
        String tracker = normalizedSource(
                "DarkSwordBloodMoonTracker.java"
        );

        assertTrue(handler.contains(
                "DarkSwordBloodMoonTracker.start("
        ));
        assertTrue(handler.contains("context.weaponSnapshot()"));
        assertEquals(2, occurrences(
                tracker,
                "public static void start("
        ));
        assertTrue(tracker.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertTrue(tracker.contains(
                "createBurstContext(damageMultiplier), "
                        + "state.weaponSnapshot,"
        ));
        assertExplicitSnapshotBinding(tracker);
        assertRejectedHitCleanup(tracker);
    }

    @Test
    void ossifiedDotRetainsAndBindsItsReleaseWeapon()
            throws IOException {
        String handler = normalizedSource(
                "handler/OssifiedExecutionSkillHandler.java"
        );
        String tracker = normalizedSource(
                "OssifiedExecutionTracker.java"
        );

        assertTrue(handler.contains(
                "OssifiedExecutionTracker.start("
        ));
        assertTrue(handler.contains("context.weaponSnapshot()"));
        assertEquals(2, occurrences(
                tracker,
                "public static void start("
        ));
        assertTrue(tracker.contains(
                "createPulseContext(), state.weaponSnapshot,"
        ));
        assertExplicitSnapshotBinding(tracker);
        assertRejectedHitCleanup(tracker);
    }

    @Test
    void detachedSteelDotOwnsTheLineReleaseWeapon()
            throws IOException {
        String lineHandler = normalizedSource(
                "handler/SteelFalchionLineSkillHandler.java"
        );
        String traceHandler = normalizedSource(
                "handler/SteelFalchionTraceSkillHandler.java"
        );
        String tracker = normalizedSource(
                "SteelFalchionLineTracker.java"
        );

        assertTrue(lineHandler.contains(
                "SteelFalchionLineTracker.startMinorLine("
        ));
        assertTrue(lineHandler.contains("context.weaponSnapshot()"));
        assertTrue(traceHandler.contains(
                "SteelFalchionLineTracker.startTrace("
        ));
        assertTrue(traceHandler.contains("context.weaponSnapshot()"));
        assertEquals(2, occurrences(
                tracker,
                "public static void startMinorLine("
        ));
        assertEquals(2, occurrences(
                tracker,
                "public static void startTrace("
        ));
        assertTrue(occurrences(
                tracker,
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ) >= 2);
        assertTrue(tracker.contains(
                "line.weaponSnapshot"
        ));
        assertTrue(tracker.contains(
                "dot.weaponSnapshot"
        ));
        assertTrue(tracker.contains(
                "finalSnapshot = weaponSnapshot == null "
                        + "? existing.weaponSnapshot : weaponSnapshot;"
        ));
        assertExplicitSnapshotBinding(tracker);
        assertRejectedHitCleanup(tracker);
    }

    private static void assertExplicitSnapshotBinding(String tracker) {
        assertTrue(tracker.contains(
                "WeaponSkillContextStore.setPending( "
                        + "player, context, weaponSnapshot, expireTick"
        ) || tracker.contains(
                "WeaponSkillContextStore.setPending( "
                        + "owner, context, weaponSnapshot, expireTick"
        ) || tracker.contains(
                "WeaponSkillDamage.apply( "
                        + "player, target, context, "
                        + "weaponSnapshot, expireTick"
        ) || tracker.contains(
                "WeaponSkillDamage.apply( "
                        + "owner, target, context, "
                        + "weaponSnapshot, expireTick"
        ) || tracker.contains(
                "WeaponSkillDamage.apply("
        ) && tracker.contains(
                "weaponSnapshot"
        ));
    }

    private static void assertHandlerSnapshotBinding(String handler) {
        assertTrue(handler.contains(
                "WeaponSkillContextStore.setPending( "
                        + "context.player(), "
        ) || handler.contains(
                "WeaponSkillDamage.apply( "
                        + "context.player(), "
        ));
        assertTrue(handler.contains(
                "createHitContext(context.skillData()), "
                        + "context.weaponSnapshot(), "
        ));
    }

    private static void assertReleaseSnapshotPersistence(String tracker) {
        assertTrue(tracker.contains("TAG_RELEASE_WEAPON_ID")
                || tracker.contains("\"ReleaseWeaponId\""));
        assertTrue(tracker.contains("TAG_RELEASE_WEAPON")
                || tracker.contains("\"ReleaseWeapon\""));
        assertTrue(tracker.contains(".saveOptional("));
        assertTrue(tracker.contains("ItemStack.parse"));
    }

    private static void assertInlinePendingCleanup(String tracker) {
        if (tracker.contains("WeaponSkillDamage.apply(")) {
            assertCentralizedDamageBinding(tracker);
            return;
        }
        assertTrue(tracker.contains("} finally {"));
        assertTrue(tracker.contains(
                "WeaponSkillContextStore.hasPending(owner, nowTick)"
        ) || tracker.contains(
                "WeaponSkillContextStore.hasPending(player, nowTick)"
        ));
        assertTrue(tracker.contains(
                "WeaponSkillContextStore.consume(owner, nowTick);"
        ) || tracker.contains(
                "WeaponSkillContextStore.consume(player, nowTick);"
        ));
    }

    private static void assertRejectedHitCleanup(String tracker) {
        if (tracker.contains("WeaponSkillDamage.apply(")) {
            assertCentralizedDamageBinding(tracker);
            return;
        }
        assertTrue(tracker.contains("} finally {"));
        assertTrue(tracker.contains(
                "clearUnconsumedContext(player, nowTick);"
        ));
        assertTrue(tracker.contains(
                "WeaponSkillContextStore.hasPending(player, nowTick)"
        ));
    }

    private static void assertCentralizedDamageBinding(String tracker) {
        assertTrue(tracker.contains("WeaponSkillDamage.apply("));
        assertTrue(!tracker.contains("playerAttack("));
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
        return normalizedMainSource(
                Path.of("combat", "skill")
                        .resolve(relativeFile)
                        .toString()
        );
    }

    private static String normalizedMainSource(String relativeFile)
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

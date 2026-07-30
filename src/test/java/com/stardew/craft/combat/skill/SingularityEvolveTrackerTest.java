package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingularityEvolveTrackerTest {
    @Test
    void authoredPullExplosionSlashAndRiftValuesRemainStable() {
        assertEquals(
                20,
                SingularityEvolveTracker.ACTIVE_DURATION_TICKS
        );
        assertEquals(4.0D, SingularityEvolveTracker.EFFECT_RADIUS);
        assertEquals(
                1.6F,
                SingularityEvolveTracker
                        .EXPLOSION_DAMAGE_MULTIPLIER
        );
        assertEquals(
                1.2F,
                SingularityEvolveTracker.SLASH_DAMAGE_MULTIPLIER
        );
        assertEquals(5.0D, SingularityEvolveTracker.DASH_DISTANCE);
        assertEquals(
                5,
                SingularityEvolveTracker.DASH_DURATION_TICKS
        );
        assertEquals(0.15D, SingularityEvolveTracker.PULL_STRENGTH);
        assertEquals(3.0F, SingularityEvolveTracker.RIFT_LENGTH);
        assertEquals(
                40,
                SingularityEvolveTracker.RIFT_DURATION_TICKS
        );
    }

    @Test
    void pullWindowEndsExactlyWhenExplosionAndDashResolve() {
        assertTrue(
                SingularityEvolveTracker.isWithinPullWindow(
                        119L,
                        120L
                )
        );
        assertFalse(
                SingularityEvolveTracker.isWithinPullWindow(
                        120L,
                        120L
                )
        );
        assertTrue(
                SingularityEvolveTracker.shouldProcessTick(
                        119L,
                        118L
                )
        );
        assertFalse(
                SingularityEvolveTracker.shouldProcessTick(
                        119L,
                        119L
                )
        );
    }

    @Test
    void explosionAndSlashUseSeparateOriginalMinorContexts() {
        SkillContext explosion =
                SingularityEvolveTracker.createDamageContext(
                        "singularity_evolve",
                        1.6F
                );
        SkillContext slash =
                SingularityEvolveTracker.createDamageContext(
                        "singularity_evolve",
                        1.2F
                );

        assertEquals("singularity_evolve", explosion.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, explosion.getTier());
        assertEquals(1.6F, explosion.getDamageMultiplier());
        assertFalse(explosion.isGuaranteedCrit());
        assertFalse(explosion.isIgnoreDefense());
        assertEquals(1.2F, slash.getDamageMultiplier());
    }

    @Test
    void delayedStateRequiresTheCasterAndStartingDimension() {
        assertTrue(SingularityEvolveTracker.isValidContext(true, true));
        assertFalse(SingularityEvolveTracker.isValidContext(false, true));
        assertFalse(SingularityEvolveTracker.isValidContext(true, false));
        assertTrue(SingularityEvolveTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(SingularityEvolveTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void logoutCleanupIsIdempotentWithoutAnOnlineCaster() {
        assertDoesNotThrow(() ->
                SingularityEvolveTracker.removePlayer(
                        UUID.randomUUID()
                )
        );
    }

    @Test
    void releaseWeaponSnapshotFlowsIntoDetachedRiftDamage()
            throws IOException {
        assertDoesNotThrow(() ->
                SingularityEvolveTracker.class.getDeclaredMethod(
                        "start",
                        ServerPlayer.class,
                        long.class,
                        int.class,
                        double.class,
                        float.class,
                        float.class,
                        String.class,
                        boolean.class
                )
        );
        assertDoesNotThrow(() ->
                SingularityEvolveTracker.class.getDeclaredMethod(
                        "start",
                        ServerPlayer.class,
                        long.class,
                        int.class,
                        double.class,
                        float.class,
                        float.class,
                        String.class,
                        boolean.class,
                        WeaponDamageSnapshot.class
                )
        );
        assertDoesNotThrow(() ->
                RiftPathDamageTracker.class.getDeclaredMethod(
                        "start",
                        ServerPlayer.class,
                        Vec3.class,
                        float.class,
                        float.class,
                        int.class,
                        String.class
                )
        );
        assertDoesNotThrow(() ->
                RiftPathDamageTracker.class.getDeclaredMethod(
                        "start",
                        ServerPlayer.class,
                        Vec3.class,
                        float.class,
                        float.class,
                        int.class,
                        String.class,
                        WeaponDamageSnapshot.class
                )
        );

        Path sourceRoot = findSourceRoot();
        String handler = normalizedSource(
                sourceRoot.resolve(
                        "handler/SingularityEvolveSkillHandler.java"
                )
        );
        String parent = normalizedSource(
                sourceRoot.resolve("SingularityEvolveTracker.java")
        );
        String child = normalizedSource(
                sourceRoot.resolve("RiftPathDamageTracker.java")
        );

        assertTrue(handler.contains("context.weaponSnapshot()"));
        assertTrue(parent.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertTrue(parent.contains(
                "\"singularity_rift_path\", state.weaponSnapshot"
        ));
        assertTrue(child.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertTrue(child.contains(
                "WeaponSkillDamage.apply("
        ));
        assertTrue(child.contains(
                "WeaponSkillDamage.AttackGatePolicy "
                        + ".RESPECT_AT_IMPACT"
        ));
        assertFalse(child.contains(
                "WeaponSkillContextStore.setPending("
        ));
    }

    private static String normalizedSource(Path path) throws IOException {
        return Files.readString(path).replaceAll("\\s+", " ");
    }

    private static Path findSourceRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException(
                "Cannot locate combat skill sources from "
                        + Path.of("").toAbsolutePath()
        );
    }
}

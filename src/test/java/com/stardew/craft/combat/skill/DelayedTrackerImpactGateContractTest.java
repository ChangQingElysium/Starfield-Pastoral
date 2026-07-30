package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedTrackerImpactGateContractTest {
    private static final List<String> TRACKERS = List.of(
            "EternalCollapseTracker.java",
            "RiftPathDamageTracker.java",
            "FemurSlamTracker.java",
            "SingularityEvolveTracker.java",
            "StarfallTracker.java"
    );

    @Test
    void actualTickHitsUseTheCentralizedImpactGate()
            throws IOException {
        for (String tracker : TRACKERS) {
            String source = readSkillSource(tracker);
            assertTrue(
                    source.contains("WeaponSkillDamage.apply("),
                    tracker
            );
            assertTrue(
                    source.contains(
                            "WeaponSkillDamage.AttackGatePolicy"
                    ) && source.contains(".RESPECT_AT_IMPACT"),
                    tracker + " must recheck AttackEntityEvent at impact"
            );
            assertFalse(source.contains("player.attack("), tracker);
            assertFalse(
                    source.contains(
                            "WeaponSkillContextStore.setPending("
                    ),
                    tracker
            );
            assertFalse(
                    source.contains("clearUnconsumedContext("),
                    tracker
            );
            assertFalse(
                    source.contains("boolean hit = WeaponSkillDamage.apply(")
                            || source.contains(
                                    "if (WeaponSkillDamage.apply("
                            ),
                    tracker + " must not change authored follow-up logic"
            );
            assertFalse(
                    source.contains("getMainHandItem("),
                    tracker + " must not recapture the current hand"
            );
        }
    }

    @Test
    void missingSnapshotsAreThreadedFromTheirRuntimeHandlers()
            throws IOException {
        assertSnapshotThreading(
                "handler/EternalCollapseSkillHandler.java",
                "EternalCollapseTracker.java"
        );
        assertSnapshotThreading(
                "handler/FemurSlamSkillHandler.java",
                "FemurSlamTracker.java"
        );
        assertSnapshotThreading(
                "handler/GalaxyJudgementSkillHandler.java",
                "StarfallTracker.java"
        );
    }

    @Test
    void existingSnapshotsStayExplicitForEveryDamageShape()
            throws IOException {
        String rift = readSkillSource(
                "RiftPathDamageTracker.java"
        );
        String singularity = readSkillSource(
                "SingularityEvolveTracker.java"
        );

        assertTrue(occurrences(
                rift,
                "state.weaponSnapshot,"
        ) >= 2);
        assertTrue(occurrences(
                singularity,
                "state.weaponSnapshot,"
        ) >= 2);
        assertTrue(occurrences(
                rift,
                ".RESPECT_AT_IMPACT"
        ) >= 2);
        assertTrue(occurrences(
                singularity,
                ".RESPECT_AT_IMPACT"
        ) >= 2);
    }

    @Test
    void authoredMultiHitAndIFrameStructureRemainsPresent()
            throws IOException {
        String eternal = readSkillSource(
                "EternalCollapseTracker.java"
        );
        String rift = readSkillSource(
                "RiftPathDamageTracker.java"
        );
        String singularity = readSkillSource(
                "SingularityEvolveTracker.java"
        );
        String starfall = readSkillSource(
                "StarfallTracker.java"
        );

        assertIFrameResetBeforeDamage(eternal);
        assertIFrameResetBeforeDamage(rift);
        assertIFrameResetBeforeDamage(singularity);
        assertIFrameResetBeforeDamage(starfall);
        assertTrue(starfall.contains(
                "for (int i = 0; i < hits; i++)"
        ));
        assertTrue(rift.contains(
                "state.hit.add(target.getUUID())"
        ));
        assertTrue(singularity.contains(
                "for (LivingEntity target : slashTargets)"
        ));
    }

    private static void assertSnapshotThreading(
            String handlerFile,
            String trackerFile
    ) throws IOException {
        String handler = readSkillSource(handlerFile);
        String tracker = readSkillSource(trackerFile);
        assertTrue(handler.contains("context.weaponSnapshot()"));
        assertEquals(2, occurrences(
                tracker,
                "public static void start("
        ));
        assertTrue(tracker.contains(
                "WeaponDamageSnapshot weaponSnapshot"
        ));
        assertTrue(tracker.contains(
                "state.weaponSnapshot,"
        ));
    }

    private static void assertIFrameResetBeforeDamage(String source) {
        int invulnerable = source.indexOf(
                "target.invulnerableTime = 0;"
        );
        int hurtTime = source.indexOf(
                "target.hurtTime = 0;",
                invulnerable
        );
        int damage = source.indexOf(
                "WeaponSkillDamage.apply(",
                hurtTime
        );
        assertTrue(invulnerable >= 0);
        assertTrue(hurtTime > invulnerable);
        assertTrue(damage > hurtTime);
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

    private static String readSkillSource(String relativeFile)
            throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill"
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

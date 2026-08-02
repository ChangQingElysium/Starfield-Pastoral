package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedTrackerImpactGateContractTest {
    private static final List<String> DIRECT_ATTACKS = List.of(
            "handler/FemurSlamExecutionState.java"
    );
    private static final List<String> SKILL_EFFECTS = List.of(
            "handler/EternalCollapseExecutionState.java",
            "RiftPathDamageTracker.java",
            "handler/GalaxyJudgementExecutionState.java"
    );

    @Test
    void actualTickHitsUseTheCentralizedImpactGate()
            throws IOException {
        for (String tracker : DIRECT_ATTACKS) {
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
            assertCentralizedEntryOnly(source, tracker);
        }
    }

    @Test
    void delayedFieldsAndBurstsRemainSkillOwnedDamage()
            throws IOException {
        for (String tracker : SKILL_EFFECTS) {
            String source = readSkillSource(tracker);
            assertTrue(
                    source.contains("WeaponSkillDamage.apply("),
                    tracker
            );
            assertTrue(
                    source.contains(
                            "WeaponSkillDamage.AttackGatePolicy"
                    ) && source.contains(".SKILL_DAMAGE"),
                    tracker + " must not replay AttackEntityEvent"
            );
            assertFalse(source.contains(".RESPECT_AT_IMPACT"), tracker);
            assertCentralizedEntryOnly(source, tracker);
        }

        String singularity = readSkillSource(
                "handler/SingularityEvolveExecutionState.java"
        );
        assertTrue(singularity.contains(
                ".EXPLOSION_DAMAGE_MULTIPLIER,"
        ));
        assertTrue(singularity.contains(
                "WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE"
        ));
        assertTrue(singularity.contains(
                ".SLASH_DAMAGE_MULTIPLIER,"
        ));
        assertTrue(singularity.contains(
                "WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT"
        ));
        assertCentralizedEntryOnly(
                singularity,
                "handler/SingularityEvolveExecutionState.java"
        );
    }

    private static void assertCentralizedEntryOnly(
            String source,
            String tracker
    ) {
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

    @Test
    void missingSnapshotsAreThreadedFromTheirRuntimeHandlers()
            throws IOException {
        String eternalHandler = readSkillSource(
                "handler/EternalCollapseSkillHandler.java"
        );
        String eternalState = readSkillSource(
                "handler/EternalCollapseExecutionState.java"
        );
        assertTrue(eternalHandler.contains(
                "new EternalCollapseExecutionState("
        ));
        assertTrue(eternalState.contains(
                "executionContext.weaponSnapshot()"
        ));
        assertTrue(eternalState.contains("weaponSnapshot,"));
        String femurHandler = readSkillSource(
                "handler/FemurSlamSkillHandler.java"
        );
        String femurState = readSkillSource(
                "handler/FemurSlamExecutionState.java"
        );
        assertTrue(femurHandler.contains(
                "new FemurSlamExecutionState("
        ));
        assertTrue(femurState.contains("context.weaponSnapshot()"));
        assertTrue(femurState.contains("weaponSnapshot,"));
        String galaxyHandler = readSkillSource(
                "handler/GalaxyJudgementSkillHandler.java"
        );
        String starfall = readSkillSource(
                "handler/GalaxyJudgementExecutionState.java"
        );
        assertTrue(galaxyHandler.contains("context.weaponSnapshot()"));
        assertTrue(starfall.contains(
                "WeaponDamageSnapshot weaponSnapshot"
        ));
        assertTrue(starfall.contains("weaponSnapshot,"));
    }

    @Test
    void existingSnapshotsStayExplicitForEveryDamageShape()
            throws IOException {
        String rift = readSkillSource(
                "RiftPathDamageTracker.java"
        );
        String singularity = readSkillSource(
                "handler/SingularityEvolveExecutionState.java"
        );

        assertTrue(occurrences(
                rift,
                "state.weaponSnapshot,"
        ) >= 2);
        assertTrue(singularity.contains("weaponSnapshot,"));
        assertTrue(singularity.contains(
                ".EXPLOSION_DAMAGE_MULTIPLIER"
        ));
        assertTrue(singularity.contains(
                ".SLASH_DAMAGE_MULTIPLIER"
        ));
        assertTrue(occurrences(
                rift,
                ".SKILL_DAMAGE"
        ) >= 2);
        assertFalse(rift.contains(".RESPECT_AT_IMPACT"));
        assertTrue(occurrences(
                singularity,
                ".RESPECT_AT_IMPACT"
        ) >= 1);
        assertTrue(occurrences(
                singularity,
                ".SKILL_DAMAGE"
        ) >= 1);
    }

    @Test
    void authoredMultiHitUsesExplicitCooldownBypassPolicy()
            throws IOException {
        String eternal = readSkillSource(
                "handler/EternalCollapseExecutionState.java"
        );
        String rift = readSkillSource(
                "RiftPathDamageTracker.java"
        );
        String singularity = readSkillSource(
                "handler/SingularityEvolveExecutionState.java"
        );
        String starfall = readSkillSource(
                "handler/GalaxyJudgementExecutionState.java"
        );

        assertExplicitCooldownBypassWithoutDirectReset(eternal);
        assertExplicitCooldownBypassWithoutDirectReset(rift);
        assertExplicitCooldownBypassWithoutDirectReset(singularity);
        assertExplicitCooldownBypassWithoutDirectReset(starfall);
        assertTrue(starfall.contains(
                "for (int index = 0; index < hits; index++)"
        ));
        assertTrue(rift.contains(
                "state.hit.add(target.getUUID())"
        ));
        assertTrue(singularity.contains(
                "private void applySlashAfterDash("
        ));
        assertTrue(singularity.contains(
                "for (LivingEntity target : targets)"
        ));
    }

    private static void assertSnapshotThreading(
            String handlerFile,
            String trackerFile
    ) throws IOException {
        String handler = readSkillSource(handlerFile);
        String tracker = readSkillSource(trackerFile);
        assertTrue(handler.contains("context.weaponSnapshot()"));
        assertTrue(occurrences(
                tracker,
                "public static void start("
        ) >= 1);
        assertTrue(tracker.contains(
                "WeaponDamageSnapshot weaponSnapshot"
        ));
        assertTrue(tracker.contains(
                "state.weaponSnapshot,"
        ));
    }

    private static void assertExplicitCooldownBypassWithoutDirectReset(
            String source
    ) {
        int damage = source.indexOf("WeaponSkillDamage.apply(");
        int bypass = source.indexOf(
                ".BYPASS_FOR_AUTHORED_SEQUENCE",
                damage
        );
        assertFalse(source.contains("target.invulnerableTime = 0;"));
        assertFalse(source.contains("target.hurtTime = 0;"));
        assertTrue(damage >= 0);
        assertTrue(bypass > damage);
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

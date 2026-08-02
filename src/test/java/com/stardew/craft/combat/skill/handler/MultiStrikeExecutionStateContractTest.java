package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiStrikeExecutionStateContractTest {
    private static final List<String> HANDLERS = List.of(
            "CarvingThrustSkillHandler.java",
            "IridiumNeedleThrustSkillHandler.java",
            "FishcatchThrustSkillHandler.java"
    );
    private static final List<String> STATES = List.of(
            "CarvingThrustExecutionState.java",
            "IridiumNeedleThrustExecutionState.java",
            "FishcatchThrustExecutionState.java"
    );

    @Test
    void runtimeInstancesOwnAllThreeMultiStrikeSchedules()
            throws IOException {
        Path handlerRoot = handlerRoot();
        for (int index = 0; index < HANDLERS.size(); index++) {
            String handler = Files.readString(
                    handlerRoot.resolve(HANDLERS.get(index))
            );
            String stateName = STATES.get(index)
                    .replace(".java", "");
            String state = Files.readString(
                    handlerRoot.resolve(STATES.get(index))
            );

            assertTrue(handler.contains(
                    "instance.initializeExecutionState("
            ));
            assertTrue(handler.contains(
                    "instance.requireExecutionState("
            ));
            assertTrue(handler.contains(stateName + ".class"));
            assertTrue(state.contains(
                    "implements SkillInstance.ExecutionState"
            ));
            assertTrue(state.contains(
                    "executionContext.weaponSnapshot()"
            ));
            assertTrue(state.contains(
                    "return settleAfterObservedTick();"
            ));
            assertTrue(state.contains(
                    "phase == Phase.SETTLED"
            ));
            assertFalse(state.contains("static final Map"));
            assertFalse(state.contains("Map<UUID"));
            assertFalse(state.contains("@SubscribeEvent"));
        }
    }

    @Test
    void obsoleteExecutionTrackersAreDeleted() throws IOException {
        Path skillRoot = handlerRoot().getParent();
        String cleanup = Files.readString(
                skillRoot.getParent().resolve("CombatTrackerCleanup.java")
        );
        for (String tracker : List.of(
                "CarvingKnifeThrustTracker.java",
                "IridiumNeedleThrustTracker.java",
                "BrokenTridentThrustTracker.java",
                "GalaxyDaggerThrustTracker.java",
                "InfinityDaggerThrustTracker.java",
                "ClaymoreFoldbackTracker.java",
                "ObsidianCrackTracker.java",
                "StarfallTracker.java",
                "FemurSlamTracker.java"
        )) {
            assertFalse(Files.exists(skillRoot.resolve(tracker)), tracker);
            assertFalse(cleanup.contains(
                    tracker.replace(".java", "")
            ), tracker);
        }
    }

    @Test
    void daggerCombosUseInstanceStateAndReleaseSnapshots()
            throws IOException {
        Path handlerRoot = handlerRoot();
        for (List<String> pair : List.of(
                List.of(
                        "GalaxyDaggerStarstabSkillHandler.java",
                        "GalaxyDaggerThrustExecutionState.java"
                ),
                List.of(
                        "InfinityDaggerSingularityStabSkillHandler.java",
                        "InfinityDaggerThrustExecutionState.java"
                )
        )) {
            String handler = Files.readString(handlerRoot.resolve(pair.get(0)));
            String state = Files.readString(handlerRoot.resolve(pair.get(1)));
            String stateName = pair.get(1).replace(".java", "");

            assertTrue(handler.contains(
                    "instance.initializeExecutionState("
            ));
            assertTrue(handler.contains(
                    "instance.requireExecutionState("
            ));
            assertTrue(handler.contains(stateName + ".class"));
            assertTrue(state.contains(
                    "implements SkillInstance.ExecutionState"
            ));
            assertTrue(state.contains(
                    "WeaponDamageSnapshot weaponSnapshot = "
                            + "context.weaponSnapshot();"
            ));
            assertTrue(state.contains(
                    "return SkillTickResult.COMPLETE;"
            ));
            assertTrue(state.contains(".guaranteedCrit(true)"));
            assertTrue(state.contains("living.isPickable()"));
            assertTrue(state.contains("findTargetInFront("));
            assertTrue(state.contains("finalStrikeCandidateId"));
            assertTrue(state.contains("beginStrike("));
            assertTrue(state.contains("consumeFinalStrikeCandidate("));
            assertTrue(state.contains("clearFinalStrikeCandidate();"));
            assertTrue(state.contains("finally"));
            assertFalse(state.contains(
                    "boolean hit = WeaponSkillDamage.apply("
            ));
            assertFalse(state.contains("MarkTracker.apply("));
            int strikeAnimation = state.indexOf(
                    "WeaponSkillAnimationDispatcher.sendSkillAnim("
            );
            int strikeLock = state.indexOf(
                    "WeaponSkillAnimationLock.setLock(",
                    strikeAnimation
            );
            int snapshot = state.indexOf(
                    "context.weaponSnapshot()",
                    strikeLock
            );
            int damage = state.indexOf(
                    "WeaponSkillDamage.apply",
                    snapshot
            );
            assertTrue(strikeAnimation >= 0);
            assertTrue(strikeLock > strikeAnimation);
            assertTrue(snapshot > strikeLock);
            assertTrue(damage > snapshot);

            int initialLock = handler.indexOf(
                    "WeaponSkillAnimationLock.setLock("
            );
            int initialAnimation = handler.indexOf(
                    "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                    initialLock
            );
            assertTrue(initialLock >= 0);
            assertTrue(initialAnimation > initialLock);
            assertFalse(state.contains("static final Map"));
            assertFalse(state.contains("Map<UUID"));
            assertFalse(state.contains("Phase.SETTLED"));
        }
    }

    private static Path handlerRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "handler"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate handler source root");
    }
}

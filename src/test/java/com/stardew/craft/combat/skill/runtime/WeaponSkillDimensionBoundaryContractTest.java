package com.stardew.craft.combat.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillDimensionBoundaryContractTest {
    @Test
    void activeExecutionFreezesAndChecksReleaseDimensionBeforeTick()
            throws IOException {
        String runtime = normalizedSource(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );

        assertTrue(runtime.contains(
                "ResourceKey<Level> releaseDimension"
        ));
        assertTrue(runtime.contains(
                "instance, player.level().dimension(), "
                        + "context.weaponSnapshot()"
        ));

        int dimensionGuard = runtime.indexOf(
                "if (!execution.releaseDimension().equals("
                        + "player.level().dimension()))"
        );
        int handlerTick = runtime.indexOf(
                "execution.handler().tick("
        );
        assertTrue(dimensionGuard >= 0);
        assertTrue(handlerTick > dimensionGuard);

        String guardedBlock = runtime.substring(
                dimensionGuard,
                handlerTick
        );
        assertTrue(guardedBlock.contains(
                "SkillInstance.EndReason.INVALIDATED"
        ));
        assertTrue(guardedBlock.contains(
                "endExecution("
        ));
        assertTrue(guardedBlock.contains(
                "continue;"
        ));
    }

    @Test
    void runtimeOwnsExecutionStateCleanupForLongLivedHandlers()
            throws IOException {
        String runtime = normalizedSource(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );
        String crescent = normalizedSource(
                "combat/skill/handler/CrescentSlashSkillHandler.java"
        );
        String forest = normalizedSource(
                "combat/skill/handler/ForestBlessingSkillHandler.java"
        );
        String tideAnchor = normalizedSource(
                "combat/skill/handler/TideAnchorSkillHandler.java"
        );
        String meowmereShot = normalizedSource(
                "combat/skill/handler/MeowmereShotSkillHandler.java"
        );
        String meowmereSymphony = normalizedSource(
                "combat/skill/handler/MeowmereSymphonySkillHandler.java"
        );
        String lightCounter = normalizedSource(
                "combat/skill/handler/LightCounterSkillHandler.java"
        );
        String elfBlade = normalizedSource(
                "combat/skill/handler/ElfBladeLeafSkillHandler.java"
        );

        assertTrue(runtime.contains("instance.clearExecutionState();"));
        int handlerFinish = runtime.indexOf(
                "execution.handler().finish(context, instance, reason);"
        );
        int stateClear = runtime.indexOf(
                "instance.clearExecutionState();",
                handlerFinish
        );
        int activeRemove = runtime.indexOf(
                "ACTIVE.remove(instance.instanceId());",
                stateClear
        );
        assertTrue(handlerFinish >= 0);
        assertTrue(stateClear > handlerFinish);
        assertTrue(activeRemove > stateClear);

        assertTrue(crescent.contains("instance.requireExecutionState(State.class)"));
        assertTrue(forest.contains("instance.requireExecutionState(State.class)"));
        assertTrue(tideAnchor.contains("instance.requireExecutionState(State.class)"));
        assertTrue(meowmereShot.contains(
                "instance.requireExecutionState(State.class)"
        ));
        assertTrue(meowmereSymphony.contains(
                "instance.requireExecutionState(State.class)"
        ));
        assertFalse(crescent.contains("Map<UUID"));
        assertFalse(forest.contains("Map<UUID"));
        assertFalse(tideAnchor.contains("Map<UUID"));
        assertFalse(meowmereShot.contains("MeowmereShotTracker"));
        assertFalse(meowmereSymphony.contains("MeowmereSymphonyTracker"));
        assertTrue(forest.contains(
                "new ForestBlessingPayload("
        ));
        assertTrue(lightCounter.contains(
                "instance.executionState(LightCounterExecutionState.class) "
                        + ".ifPresent(LightCounterExecutionState::cancel);"
        ));
        assertTrue(elfBlade.contains(
                "if (shouldDiscardLeaves(reason)) { "
                        + "instance.executionState("
                        + "ElfBladeLeafExecutionState.class)"
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

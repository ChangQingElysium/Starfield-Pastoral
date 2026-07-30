package com.stardew.craft.combat.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

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
    void auditedLongLivedHandlersClearTheirOwnedStateOnInvalidation()
            throws IOException {
        String crescent = normalizedSource(
                "combat/skill/handler/CrescentSlashSkillHandler.java"
        );
        String forest = normalizedSource(
                "combat/skill/handler/ForestBlessingSkillHandler.java"
        );
        String lightCounter = normalizedSource(
                "combat/skill/handler/LightCounterSkillHandler.java"
        );
        String elfBlade = normalizedSource(
                "combat/skill/handler/ElfBladeLeafSkillHandler.java"
        );

        assertTrue(crescent.contains(
                "states.remove(instance.instanceId());"
        ));
        assertTrue(forest.contains(
                "State state = states.remove(instance.instanceId());"
        ));
        assertTrue(forest.contains(
                "new ForestBlessingPayload("
        ));
        assertTrue(lightCounter.contains(
                "LightCounterParryState.clear(context.player());"
        ));
        assertTrue(elfBlade.contains(
                "if (reason != SkillInstance.EndReason.COMPLETED) "
                        + "{ ElfBladeTracker.cancel("
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

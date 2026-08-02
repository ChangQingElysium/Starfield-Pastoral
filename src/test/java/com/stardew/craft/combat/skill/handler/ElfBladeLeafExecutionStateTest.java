package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElfBladeLeafExecutionStateTest {
    @Test
    void runtimeWindowEndsAtTheLeafOrbitExpirationTick() {
        assertTrue(ElfBladeLeafExecutionState.isWithinActiveWindow(
                199L,
                200L
        ));
        assertFalse(ElfBladeLeafExecutionState.isWithinActiveWindow(
                200L,
                200L
        ));
    }

    @Test
    void oneExecutionOwnsSpawnRollbackAndDeferredSettlement()
            throws IOException {
        Path root = handlerRoot();
        String handler = Files.readString(root.resolve(
                "ElfBladeLeafSkillHandler.java"
        ));
        String state = Files.readString(root.resolve(
                "ElfBladeLeafExecutionState.java"
        ));

        assertTrue(handler.contains(
                "instance.initializeExecutionState("
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.ELF_BLADE_LEAF"
        ));
        assertFalse(handler.contains("ElfBladeTracker"));
        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));

        int defer = handler.indexOf(
                "WeaponSkillRuntime.deferCooldown("
        );
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(",
                defer
        );
        int committed = handler.indexOf(
                "instance.registerCommittedEffect(",
                initialize
        );
        int start = handler.indexOf(
                "executionState.start(",
                initialize
        );
        int lock = handler.indexOf(
                "WeaponSkillAnimationLock.setLock(",
                start
        );
        int animation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                lock
        );
        assertTrue(defer >= 0);
        assertTrue(initialize > defer);
        assertTrue(committed > initialize);
        assertTrue(start > committed);
        assertTrue(lock > start);
        assertTrue(animation > lock);

        int activePayload = state.indexOf(
                "new ElfBladePayload("
        );
        int snapshot = state.indexOf(
                "WeaponDamageSnapshot weaponSnapshot = "
                        + "context.weaponSnapshot();",
                activePayload
        );
        int create = state.indexOf(
                "new ElfBladeLeafEntity(",
                snapshot
        );
        int position = state.indexOf("leaf.setPos(", create);
        int add = state.indexOf(
                "level.addFreshEntity(leaf)",
                position
        );
        int id = state.indexOf(
                "UUID leafId = leaf.getUUID();",
                add
        );
        int own = state.indexOf("leafIds.add(leafId);", id);
        int exactDiscard = state.indexOf(
                "private void clearLeaves(",
                own
        );
        assertTrue(activePayload >= 0);
        assertTrue(snapshot > activePayload);
        assertTrue(create > snapshot);
        assertTrue(position > create);
        assertTrue(add > position);
        assertTrue(id > add);
        assertTrue(own > id);
        assertFalse(state.contains("registerBeginFailureCleanup("));
        assertTrue(exactDiscard > own);
        assertTrue(state.contains("for (UUID leafId : Set.copyOf(leafIds))"));
        assertTrue(state.contains("leaf.discard();"));
    }

    @Test
    void naturalCompletionKeepsFiredLeavesButCancellationDiscards()
            throws IOException {
        String state = Files.readString(handlerRoot().resolve(
                "ElfBladeLeafExecutionState.java"
        ));

        int advance = state.indexOf(
                "SkillTickResult advance(SkillExecutionContext context)"
        );
        int natural = state.indexOf(
                "completeNaturally(context.player(), context.nowTick())",
                advance
        );
        int naturalMethod = state.indexOf(
                "private void completeNaturally(",
                natural
        );
        int settle = state.indexOf(
                "settle(player, nowTick);",
                naturalMethod
        );
        int nextMethod = state.indexOf(
                "private void settle(",
                naturalMethod
        );
        String naturalBody = state.substring(naturalMethod, nextMethod);
        assertTrue(advance >= 0);
        assertTrue(natural > advance);
        assertTrue(settle > naturalMethod);
        assertFalse(naturalBody.contains("clearLeaves("));

        int cancel = state.indexOf("void cancel(");
        int clear = state.indexOf("clearLeaves(player);", cancel);
        int cancelledSettlement = state.indexOf(
                "settle(player, nowTick);",
                clear
        );
        assertTrue(cancel >= 0);
        assertTrue(clear > cancel);
        assertTrue(cancelledSettlement > clear);
        assertTrue(state.contains(
                "WeaponSkillRuntime.commitDeferredCooldown("
        ));
        assertTrue(state.contains(
                "for (ServerLevel level : player.server.getAllLevels())"
        ));
        assertTrue(state.contains("for (UUID leafId :"));
        assertTrue(state.contains(
                "catch (RuntimeException exception)"
        ));
        assertTrue(state.contains(
                "cleanupFailure.addSuppressed(exception);"
        ));
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

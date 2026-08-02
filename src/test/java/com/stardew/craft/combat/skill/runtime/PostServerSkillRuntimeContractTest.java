package com.stardew.craft.combat.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostServerSkillRuntimeContractTest {
    @Test
    void runeThrustMovementHasOnePostConnectionTickOwner()
            throws IOException {
        String eventOwner = source(
                "combat/skill/runtime/WeaponSkillPostServerRuntime.java"
        );
        String runtime = source(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );
        String handler = source(
                "combat/skill/handler/DwarfDaggerThrustSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/DwarfDaggerThrustExecutionState.java"
        );
        String dragonHandler = source(
                "combat/skill/handler/DragonBreathThrustSkillHandler.java"
        );
        String dragonState = source(
                "combat/skill/handler/DragonBreathThrustExecutionState.java"
        );
        String sharedDash = source(
                "combat/skill/DashMovementTracker.java"
        );
        String dragonResource = source(
                "combat/skill/DragonBreathTracker.java"
        );
        String movementArbiter = source(
                "combat/skill/runtime/WeaponSkillMovementArbiter.java"
        );

        assertTrue(eventOwner.contains("ServerTickEvent.Post"));
        assertTrue(eventOwner.contains(
                "DashMovementTracker.tickServer(event.getServer())"
        ));
        assertTrue(eventOwner.indexOf(
                "DashMovementTracker.tickServer(event.getServer())"
        ) < eventOwner.indexOf(
                "WeaponSkillRuntime.tickPostServer(event.getServer())"
        ));
        assertTrue(runtime.contains(
                "instanceof PostServerRuntimeWeaponSkillHandler"
        ));
        assertTrue(runtime.contains(
                "ACTIVE.get(execution.instance().instanceId())"
        ));
        assertTrue(runtime.contains(
                "execution.context().player() != player"
        ));
        assertTrue(runtime.contains(
                "execution.releaseDimension().equals("
        ));
        assertTrue(runtime.contains(".postServerTick("));
        assertTrue(runtime.contains(
                "result == SkillTickResult.COMPLETE"
        ));
        assertTrue(runtime.contains(
                "result == SkillTickResult.CANCEL"
        ));
        assertTrue(handler.contains(
                "implements PostServerRuntimeWeaponSkillHandler"
        ));
        assertTrue(handler.contains(
                "public SkillTickResult postServerTick("
        ));
        assertTrue(handler.contains(
                "DwarfDaggerThrustExecutionState.class"
        ));
        assertFalse(handler.contains("player.move("));
        assertFalse(handler.contains("teleportTo("));
        assertFalse(handler.contains("ServerTickEvent"));

        assertTrue(state.contains(
                "StardewTimePauseService.shouldPauseLevel("
        ));
        assertTrue(state.contains("player.move(MoverType.SELF"));
        assertTrue(state.contains("player.teleportTo("));
        assertTrue(state.contains(
                "isWithinExecutionWindow(context.nowTick(), endTick)"
        ));
        assertTrue(state.contains("executionContext.weaponSnapshot()"));
        assertFalse(state.contains("private boolean active"));
        assertFalse(state.contains("void stop("));
        assertFalse(state.contains("Map<UUID"));
        assertFalse(state.contains("ServerTickEvent"));

        assertTrue(dragonHandler.contains(
                "implements RuntimeWeaponSkillHandler"
        ));
        assertFalse(dragonHandler.contains(
                "PostServerRuntimeWeaponSkillHandler"
        ));
        assertFalse(dragonHandler.contains("postServerTick("));
        assertTrue(dragonHandler.contains(
                "DragonBreathThrustExecutionState.class"
        ));
        assertTrue(dragonState.contains("DashMovementTracker.Handle"));
        assertTrue(dragonState.contains("DashMovementTracker.cancel("));
        assertFalse(dragonState.contains("Map<UUID"));
        assertFalse(dragonResource.contains("ACTIVE_THRUSTS"));
        assertFalse(sharedDash.contains("@EventBusSubscriber"));
        assertFalse(sharedDash.contains("@SubscribeEvent"));
        assertTrue(sharedDash.contains(
                "public static void onServerTick(ServerTickEvent.Post event)"
        ));
        assertTrue(sharedDash.contains(
                "tickServer(event.getServer())"
        ));
        assertTrue(sharedDash.contains(
                "state.movementId.equals(handle.movementId())"
        ));
        assertTrue(sharedDash.contains(
                "ACTIVE.remove(handle.playerId(), state)"
        ));
        assertTrue(sharedDash.contains(
                "WeaponSkillMovementArbiter.claim(player, state)"
        ));
        assertTrue(sharedDash.contains(
                "WeaponSkillMovementArbiter.owns(state.lease)"
        ));
        assertTrue(state.contains(
                "WeaponSkillMovementArbiter.claim(player, this)"
        ));
        assertTrue(state.contains(
                "WeaponSkillMovementArbiter.owns(movementLease)"
        ));
        assertTrue(movementArbiter.contains(
                "previous.owner().onMovementRevoked(player)"
        ));
        assertTrue(movementArbiter.contains(
                "if (!owns(lease))"
        ));
        assertTrue(movementArbiter.contains(
                "ACTIVE.remove(lease.playerId())"
        ));
    }

    private static String source(String relativeFile) throws IOException {
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
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}

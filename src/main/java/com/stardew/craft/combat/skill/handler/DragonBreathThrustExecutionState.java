package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** One Dragon Breath Thrust execution bound to one exact shared dash. */
final class DragonBreathThrustExecutionState
        implements SkillInstance.ExecutionState {
    private final long endTick;
    private DashMovementTracker.Handle movement;

    DragonBreathThrustExecutionState(long nowTick, int durationTicks) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Dragon Breath Thrust duration must be positive"
            );
        }
        this.endTick = nowTick + durationTicks;
    }

    void start(
            ServerPlayer player,
            long nowTick,
            Vec3 end,
            int durationTicks
    ) {
        if (movement != null) {
            throw new IllegalStateException(
                    "Dragon Breath Thrust movement is already started"
            );
        }
        movement = DashMovementTracker.startExact(
                player,
                nowTick,
                end,
                durationTicks
        );
        if (movement == null) {
            throw new IllegalStateException(
                    "Dragon Breath Thrust movement could not start"
            );
        }
    }

    SkillTickResult result(long nowTick) {
        return resultFor(nowTick, endTick);
    }

    void finish(
            ServerPlayer player,
            SkillInstance.EndReason reason
    ) {
        if (DragonBreathThrustSkillHandler.shouldCancelMovement(reason)) {
            DashMovementTracker.cancel(player, movement);
        }
    }

    static SkillTickResult resultFor(
            long nowTick,
            long endTick
    ) {
        return nowTick <= endTick
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }
}

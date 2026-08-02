package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** One Iridium Frenzy window owned by one runtime execution. */
final class IridiumNeedleFrenzyExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private final long endTick;
    private boolean settled;

    IridiumNeedleFrenzyExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Iridium Frenzy duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(
                dimension,
                "dimension"
        );
        this.endTick = nowTick + durationTicks;
    }

    boolean isActive(
            long nowTick,
            ResourceKey<Level> currentDimension
    ) {
        return !settled
                && dimension.equals(currentDimension)
                && isWithinActiveWindow(nowTick, endTick);
    }

    SkillTickResult advance(SkillExecutionContext context) {
        return advance(
                context.nowTick(),
                context.player().level().dimension()
        );
    }

    SkillTickResult advance(
            long nowTick,
            ResourceKey<Level> currentDimension
    ) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (!dimension.equals(currentDimension)) {
            settled = true;
            return SkillTickResult.CANCEL;
        }
        if (isWithinActiveWindow(nowTick, endTick)) {
            return SkillTickResult.CONTINUE;
        }
        settled = true;
        return SkillTickResult.COMPLETE;
    }

    void cancel() {
        settled = true;
    }

    static boolean isWithinActiveWindow(
            long nowTick,
            long endTick
    ) {
        return nowTick <= endTick;
    }
}

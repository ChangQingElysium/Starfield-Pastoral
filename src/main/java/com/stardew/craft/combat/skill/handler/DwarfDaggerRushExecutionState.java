package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.DwarfDaggerRushPayload;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** One server-authoritative Ley Line Rush window. */
final class DwarfDaggerRushExecutionState
        implements SkillInstance.ExecutionState {
    private final long endTick;
    private boolean started;
    private boolean settled;

    DwarfDaggerRushExecutionState(
            long nowTick,
            int durationTicks
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Dwarf Dagger Rush duration must be positive"
            );
        }
        this.endTick = nowTick + durationTicks;
    }

    void start(ServerPlayer player, int durationTicks) {
        if (started || settled) {
            return;
        }
        started = true;
        PacketDistributor.sendToPlayer(
                player,
                new DwarfDaggerRushPayload(true, durationTicks)
        );
    }

    boolean isActive(ServerPlayer player, long nowTick) {
        if (!started || settled) {
            return false;
        }
        if (isWithinActiveWindow(nowTick, endTick)) {
            return true;
        }
        settle(player);
        return false;
    }

    SkillTickResult advance(SkillExecutionContext context) {
        return isActive(context.player(), context.nowTick())
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    void cancel(ServerPlayer player) {
        settle(player);
    }

    static boolean isWithinActiveWindow(long nowTick, long endTick) {
        return nowTick < endTick;
    }

    private void settle(ServerPlayer player) {
        if (!started || settled) {
            return;
        }
        settled = true;
        PacketDistributor.sendToPlayer(
                player,
                new DwarfDaggerRushPayload(false, 0)
        );
    }
}

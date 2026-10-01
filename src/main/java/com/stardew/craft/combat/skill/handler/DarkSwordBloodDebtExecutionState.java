package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;

/** One authoritative Blood Debt activation window. */
final class DarkSwordBloodDebtExecutionState
        implements SkillInstance.ExecutionState {
    private net.minecraft.server.level.ServerLevel visualLevel;
    private int visualCaster;
    private long visualTick;
    private final long endTick;
    private boolean cancelled;

    DarkSwordBloodDebtExecutionState(long nowTick, int durationTicks) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Blood Debt duration must be positive"
            );
        }
        this.endTick = nowTick + durationTicks;
    }

    boolean isActive(long nowTick) {
        return !cancelled && nowTick <= endTick;
    }

    SkillTickResult advance(long nowTick) {
        if (isActive(nowTick)) {
            return SkillTickResult.CONTINUE;
        }
        cancelled = true;
        return SkillTickResult.COMPLETE;
    }

    void startPresentation(net.minecraft.server.level.ServerPlayer player,long tick) {
        visualLevel=player.serverLevel(); visualCaster=player.getId(); visualTick=tick;
        net.minecraftforge.network.PacketDistributor.sendToPlayersInDimension(visualLevel,
                new com.stardew.craft.combat.network.DarkSwordBloodDebtPayload(visualCaster,visualTick,true,DarkSwordBloodDebtSkillHandler.ACTIVE_DURATION_TICKS));
    }

    void cancel() {
        if(visualLevel != null) {
            net.minecraftforge.network.PacketDistributor.sendToPlayersInDimension(visualLevel,
                    new com.stardew.craft.combat.network.DarkSwordBloodDebtPayload(visualCaster,visualTick,false,0));
            visualLevel=null;
        }
        cancelled = true;
    }
}

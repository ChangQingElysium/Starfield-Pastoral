package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.InsectDashChainState;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Exact applied-hit settlement for one Wing Dash cast. */
final class InsectDashExecutionState implements SkillInstance.ExecutionState {
    private final int stage;
    private final Set<UUID> eligibleTargetIds;
    private final Set<UUID> appliedTargetIds = new HashSet<>();
    private final DeferredSkillCooldown deferredCooldown;
    private boolean settled;
    private boolean continuedChain;

    InsectDashExecutionState(
            int stage,
            Collection<UUID> eligibleTargetIds,
            DeferredSkillCooldown deferredCooldown
    ) {
        this.stage = stage;
        this.eligibleTargetIds = Set.copyOf(eligibleTargetIds);
        this.deferredCooldown = deferredCooldown;
    }

    synchronized boolean recordAppliedHit(UUID targetId) {
        if (settled || !eligibleTargetIds.contains(targetId)) {
            return false;
        }
        return appliedTargetIds.add(targetId);
    }

    synchronized int appliedHitCount() {
        return appliedTargetIds.size();
    }

    synchronized boolean earnedFinishSpeed() {
        return stage >= InsectDashSkillHandler.MAX_STAGE
                && !appliedTargetIds.isEmpty();
    }

    synchronized void settle(ServerPlayer player, long nowTick) {
        if (settled) {
            return;
        }
        boolean continueChain = InsectDashSkillHandler.continuesChain(
                stage,
                appliedTargetIds.size()
        );
        if (continueChain) {
            InsectDashChainState.setStage(
                    player,
                    nowTick,
                    stage,
                    deferredCooldown
            );
            continuedChain = true;
        } else {
            InsectDashChainState.clear(player);
            if (deferredCooldown != null) {
                WeaponSkillRuntime.commitDeferredCooldown(
                        player,
                        deferredCooldown,
                        nowTick
                );
            }
        }
        settled = true;
    }

    synchronized void cancel(ServerPlayer player, long nowTick) {
        if (continuedChain) {
            InsectDashChainState.cancel(player, nowTick);
            return;
        }
        if (settled) {
            return;
        }
        InsectDashChainState.clear(player);
        if (deferredCooldown != null) {
            WeaponSkillRuntime.commitDeferredCooldown(
                    player,
                    deferredCooldown,
                    nowTick
            );
        }
        settled = true;
    }
}

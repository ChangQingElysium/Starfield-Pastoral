package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Exact applied-hit reward ownership for one Startrail Rift cast. */
final class StartrailRiftExecutionState
        implements SkillInstance.ExecutionState {
    private final Set<UUID> eligibleTargetIds;
    private boolean rewardsClaimed;

    StartrailRiftExecutionState(Collection<UUID> eligibleTargetIds) {
        this.eligibleTargetIds = Set.copyOf(eligibleTargetIds);
    }

    synchronized boolean claimAppliedHitRewards(UUID targetId) {
        if (rewardsClaimed
                || targetId == null
                || !eligibleTargetIds.contains(targetId)) {
            return false;
        }
        rewardsClaimed = true;
        return true;
    }
}

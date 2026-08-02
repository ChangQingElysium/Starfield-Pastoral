package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import java.util.Objects;
import java.util.UUID;

/** Exact applied-hit control ownership for one Singularity Backstab cast. */
final class InfinityDaggerSingularityBackstabExecutionState
        implements SkillInstance.ExecutionState {
    private final UUID targetId;
    private boolean positiveHitRecorded;
    private boolean controlSettled;

    InfinityDaggerSingularityBackstabExecutionState(UUID targetId) {
        this.targetId = Objects.requireNonNull(targetId, "targetId");
    }

    synchronized boolean recordAppliedHit(UUID appliedTargetId) {
        if (controlSettled || !targetId.equals(appliedTargetId)) {
            return false;
        }
        boolean firstPositiveHit = !positiveHitRecorded;
        positiveHitRecorded = true;
        return firstPositiveHit;
    }

    synchronized boolean settleControl() {
        if (controlSettled) {
            return false;
        }
        controlSettled = true;
        return positiveHitRecorded;
    }
}

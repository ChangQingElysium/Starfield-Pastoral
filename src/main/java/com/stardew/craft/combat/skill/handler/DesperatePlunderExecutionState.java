package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import java.util.UUID;

/** Exact applied-hit outcome owned by one Desperate Plunder cast. */
final class DesperatePlunderExecutionState
        implements SkillInstance.ExecutionState {
    private final UUID targetId;
    private boolean killedByThisHit;
    private boolean settled;

    DesperatePlunderExecutionState(UUID targetId) {
        this.targetId = targetId;
    }

    synchronized boolean recordAppliedHit(
            UUID appliedTargetId,
            boolean killedByAttacker
    ) {
        if (settled
                || targetId == null
                || !targetId.equals(appliedTargetId)) {
            return false;
        }
        killedByThisHit |= killedByAttacker;
        return true;
    }

    synchronized boolean settleKilledByThisHit() {
        if (settled) {
            return false;
        }
        settled = true;
        return killedByThisHit;
    }
}

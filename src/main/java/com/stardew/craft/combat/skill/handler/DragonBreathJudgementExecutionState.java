package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Applied-hit ownership for one Dragon Breath Judgement cast. */
final class DragonBreathJudgementExecutionState
        implements SkillInstance.ExecutionState {
    private final Set<UUID> appliedTargets = new HashSet<>();
    private boolean settled;

    synchronized boolean recordAppliedTarget(UUID targetId) {
        if (settled || targetId == null) {
            return false;
        }
        return appliedTargets.add(targetId);
    }

    synchronized int settleRefund() {
        if (settled) {
            return 0;
        }
        settled = true;
        return DragonBreathJudgementSkillHandler.refundForTargetCount(
                appliedTargets.size()
        );
    }
}

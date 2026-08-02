package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;

/** One authoritative Blood Debt activation window. */
final class DarkSwordBloodDebtExecutionState
        implements SkillInstance.ExecutionState {
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

    void cancel() {
        cancelled = true;
    }
}

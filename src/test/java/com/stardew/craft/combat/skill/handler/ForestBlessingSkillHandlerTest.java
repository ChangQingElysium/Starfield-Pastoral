package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForestBlessingSkillHandlerTest {
    @Test
    void cooldownCommitsOnlyAfterActivationOrNormalCompletion() {
        assertTrue(ForestBlessingSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.COMPLETED,
                false
        ));
        assertTrue(ForestBlessingSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.INVALIDATED,
                true
        ));
        assertTrue(ForestBlessingSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.CASTER_UNAVAILABLE,
                true
        ));
        assertFalse(ForestBlessingSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.INVALIDATED,
                false
        ));
        assertFalse(ForestBlessingSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.CASTER_UNAVAILABLE,
                false
        ));
    }
}

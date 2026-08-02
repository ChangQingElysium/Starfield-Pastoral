package com.stardew.craft.combat.skill.runtime;

/**
 * Runtime skill phase for movement that must run after vanilla connection
 * position reconciliation has completed for the server tick.
 */
public interface PostServerRuntimeWeaponSkillHandler
        extends RuntimeWeaponSkillHandler {
    SkillTickResult postServerTick(
            SkillExecutionContext context,
            SkillInstance instance
    );
}

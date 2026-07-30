package com.stardew.craft.combat.skill.runtime;

public interface RuntimeWeaponSkillHandler {
    SkillValidation validate(SkillExecutionContext context);

    void begin(SkillExecutionContext context, SkillInstance instance);

    default boolean completesImmediately() {
        return true;
    }

    default SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        return SkillTickResult.CONTINUE;
    }

    default void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
    }
}

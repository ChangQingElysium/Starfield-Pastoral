package com.stardew.craft.combat.skill.runtime;

public interface RuntimeWeaponSkillHandler {
    SkillValidation validate(SkillExecutionContext context);

    /**
     * Prepares one execution. Implementations may reserve reversible resources
     * and attach execution state, but must not damage or mutate targets, spawn
     * entities, or move the caster. The runtime is the sole owner of the
     * prepare-to-commit transition.
     */
    void begin(SkillExecutionContext context, SkillInstance instance);

    /**
     * Applies effects after the execution has been irrevocably accepted.
     * Failures from this hook invalidate and finish the execution, but never
     * roll back its prepared payments or report the cast as rejected.
     */
    default void applyCommittedEffects(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        instance.runCommittedEffects();
    }

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

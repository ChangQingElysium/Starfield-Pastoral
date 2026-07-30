package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillValidation;

/**
 * Explicit runtime endpoint for Obsidian Edge's passive resonance slot.
 *
 * <p>The authored effect is charged and consumed by combat events. Activating
 * the slot manually has always succeeded without changing state.</p>
 */
public final class ObsidianResonanceSkillHandler implements RuntimeWeaponSkillHandler {
    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        return SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        // Passive-only slot: state is owned by ObsidianResonanceTracker.
    }
}

package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.TemplarVowTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;

/**
 * Server-authoritative lifecycle for Templar Blade's original counter vow.
 */
public final class TemplarVowSkillHandler implements RuntimeWeaponSkillHandler {
    public static final int ANIMATION_TICKS = 40;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }
        return WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || TemplarVowTracker.isActiveRaw(context.player())
                ? SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        TemplarVowTracker.start(
                context.player(),
                context.nowTick(),
                TemplarVowTracker.ACTIVE_DURATION_TICKS,
                weaponId,
                skillId,
                context.skillData().getCooldown() * 20,
                context.weaponSnapshot()
        );
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
        );
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        TemplarVowTracker.tick(context.player(), context.nowTick());
        return TemplarVowTracker.isActiveRaw(context.player())
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        TemplarVowTracker.cancel(
                context.player(),
                context.player().level().getGameTime(),
                reason != SkillInstance.EndReason.CASTER_UNAVAILABLE
        );
    }
}

package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.InsectEyeStanceTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;

/**
 * Server-authoritative lifecycle for Insect Head's original Compound Eye Stance.
 */
public final class InsectEyeStanceSkillHandler implements RuntimeWeaponSkillHandler {
    public static final int ACTIVE_DURATION_TICKS = 30;
    public static final int ANIMATION_TICKS = 1;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(context.player().getUUID(), context.skillId())
                || InsectEyeStanceTracker.isActive(
                        context.player(),
                        context.nowTick()
                )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        }
        return WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )
                ? SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        InsectEyeStanceTracker.start(
                context.player(),
                context.nowTick(),
                ACTIVE_DURATION_TICKS,
                weaponId,
                skillId,
                context.skillData().getCooldown() * 20
        );

        // The authored stance has a one-tick notification and no animation lock.
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
    public SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        return InsectEyeStanceTracker.isActive(
                context.player(),
                context.nowTick()
        )
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        // Natural expiry is normally finalized by isActive(). Cancellation,
        // dimension changes and logout all converge here and still begin cooldown.
        InsectEyeStanceTracker.cancel(
                context.player(),
                context.player().level().getGameTime()
        );
    }
}

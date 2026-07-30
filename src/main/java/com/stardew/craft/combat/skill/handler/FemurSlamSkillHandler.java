package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.FemurSlamTracker;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;

/**
 * Server-authoritative lifecycle for Femur's original charged slam.
 */
public final class FemurSlamSkillHandler
        implements RuntimeWeaponSkillHandler {
    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || FemurSlamTracker.isCharging(context.player())) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.COOLDOWN
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        context.player().startUsingItem(context.hand());
        FemurSlamTracker.start(
                context.player(),
                context.nowTick(),
                FemurSlamTracker.CHARGE_TICKS,
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.skillData().getDamagePercent() / 100.0F,
                context.skillData().getCooldown() * 20,
                context.weaponSnapshot()
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
        return switch (FemurSlamTracker.tick(
                context.player(),
                context.nowTick()
        )) {
            case ACTIVE -> SkillTickResult.CONTINUE;
            case COMPLETED -> SkillTickResult.COMPLETE;
            case INVALIDATED -> SkillTickResult.CANCEL;
        };
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        FemurSlamTracker.cancel(context.player());
    }
}

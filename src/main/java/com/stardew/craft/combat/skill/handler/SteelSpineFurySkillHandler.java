package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SteelSpineFuryState;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;

/**
 * Server-authoritative lifecycle for Iron Edge's original Steel Spine Fury.
 *
 * <p>The incoming-damage event charges the stance and the normal-attack event
 * consumes its stored strike. This handler owns activation, expiry ticking and
 * cancellation cleanup. Cooldown remains deferred until the stance is charged
 * or expires, matching the authored behavior.</p>
 */
public final class SteelSpineFurySkillHandler implements RuntimeWeaponSkillHandler {
    public static final int STANCE_DURATION_TICKS = 80;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(context.player().getUUID(), context.skillId())
                || SteelSpineFuryState.isBusy(context.player())) {
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        }
        boolean coolingDown = WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        );
        return coolingDown
                ? SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        SteelSpineFuryState.start(
                context.player(),
                context.nowTick(),
                STANCE_DURATION_TICKS,
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.skillData().getCooldown() * 20
        );
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(SkillExecutionContext context, SkillInstance instance) {
        SteelSpineFuryState.tick(context.player(), context.nowTick());
        return SteelSpineFuryState.isBusy(context.player())
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        SteelSpineFuryState.removePlayer(context.player().getUUID());
    }
}

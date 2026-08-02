package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
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
    public static final int CHARGE_TICKS = 20;
    public static final double RANGE = 3.0D;
    public static final double MIN_DOT = 0.5D;
    public static final int SLOW_TICKS = 40;
    public static final int SLOW_AMPLIFIER = 0;
    public static final int STAGGER_TICKS = 4;
    public static final int STAGGER_AMPLIFIER = 0;
    public static final float KNOCKBACK_MULTI = 0.7F;
    public static final float KNOCKBACK_SINGLE = 1.1F;
    public static final int QUAKE_TREMOR_MAX = 28;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
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
        instance.registerBeginFailureCleanup(
                context.player()::stopUsingItem
        );
        DeferredSkillCooldown cooldown = WeaponSkillRuntime.deferCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.initializeExecutionState(
                new FemurSlamExecutionState(
                        context.nowTick(),
                        CHARGE_TICKS,
                        cooldown
                )
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
        return instance.requireExecutionState(
                FemurSlamExecutionState.class
        ).advance(context);
    }

    /** Records one unique exact positive hit for this slam execution. */
    public static boolean recordAppliedHit(
            net.minecraft.server.level.ServerPlayer player,
            net.minecraft.world.entity.LivingEntity target
    ) {
        if (player == null || target == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.FEMUR_SLAM,
                FemurSlamExecutionState.class
        ).map(state -> state.recordAppliedHit(target))
                .orElse(false);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(FemurSlamExecutionState.class)
                .ifPresentOrElse(
                        state -> state.cancel(context.player()),
                        context.player()::stopUsingItem
                );
    }
}

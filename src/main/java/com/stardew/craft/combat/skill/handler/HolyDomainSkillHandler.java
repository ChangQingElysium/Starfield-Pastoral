package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.HolyBladeEffects;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;

/**
 * Server-authoritative lifecycle for Holy Blade's original Dawn Sanctuary.
 */
public final class HolyDomainSkillHandler implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final int DURATION_TICKS = 80;
    public static final float MAX_RADIUS = 4.0F;
    public static final int PULSE_INTERVAL_TICKS = 20;
    public static final float PULSE_DAMAGE_MULTIPLIER = 0.75F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int HEAL_AMOUNT = 4;
    public static final int RING_DURATION_TICKS = 12;

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
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.COOLDOWN
            );
        }
        return canPayEnergy(context)
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Dawn Sanctuary energy is no longer available"
            );
        }
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Dawn Sanctuary energy payment failed"
            );
        }

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        HolyDomainExecutionState executionState =
                new HolyDomainExecutionState(
                        context.nowTick(),
                        DURATION_TICKS,
                        MAX_RADIUS
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                HolyBladeEffects.playDomainActivate(context.player())
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
                HolyDomainExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(HolyDomainExecutionState.class)
                .ifPresent(HolyDomainExecutionState::cancel);
    }

    static boolean canPayEnergy(
            float currentEnergy,
            boolean creativeMode,
            boolean freeEnergyBlessing
    ) {
        return creativeMode
                || freeEnergyBlessing
                || currentEnergy >= ENERGY_COST;
    }

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                )
        );
    }
}

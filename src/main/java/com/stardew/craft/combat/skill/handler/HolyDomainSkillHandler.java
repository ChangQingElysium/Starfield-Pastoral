package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.HolyBladeSanctuaryTracker;
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

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || HolyBladeSanctuaryTracker.isActive(
                context.player(),
                context.nowTick()
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
        if (!context.player().getAbilities().instabuild
                && !PlayerStardewDataAPI.consumeEnergy(
                        context.player(),
                        ENERGY_COST
                )) {
            throw new IllegalStateException(
                    "Validated Dawn Sanctuary energy payment failed"
            );
        }

        WeaponSkillCooldowns.setCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        HolyBladeSanctuaryTracker.start(
                context.player(),
                context.nowTick(),
                DURATION_TICKS,
                MAX_RADIUS
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
        if (context.nowTick() >= instance.startGameTick() + DURATION_TICKS) {
            return SkillTickResult.COMPLETE;
        }
        return HolyBladeSanctuaryTracker.isActive(
                context.player(),
                context.nowTick()
        )
                ? SkillTickResult.CONTINUE
                : SkillTickResult.CANCEL;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        HolyBladeSanctuaryTracker.stop(context.player());
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

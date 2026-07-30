package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SteelFalchionLineTracker;
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
 * Server-authoritative lifecycle for Steel Falchion's original slash trace.
 */
public final class SteelFalchionTraceSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 12.0F;
    public static final int TRACE_DURATION_TICKS = 100;
    public static final float TRACE_DOT_DAMAGE_MULTIPLIER = 0.50F;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
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
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || SteelFalchionLineTracker.hasTrace(
                context.player().getUUID()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
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
                    "Validated Slash Trace energy is no longer available"
            );
        }
        if (!context.player().getAbilities().instabuild
                && !PlayerStardewDataAPI.consumeEnergy(
                        context.player(),
                        ENERGY_COST
                )) {
            throw new IllegalStateException(
                    "Validated Slash Trace energy payment failed"
            );
        }

        WeaponSkillCooldowns.setCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        SteelFalchionLineTracker.startTrace(
                context.player(),
                context.nowTick(),
                TRACE_DURATION_TICKS,
                TRACE_DOT_DAMAGE_MULTIPLIER,
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
        if (!SteelFalchionLineTracker.isTraceBoundToCurrentDimension(
                context.player()
        )) {
            SteelFalchionLineTracker.tick(
                    context.player(),
                    context.nowTick()
            );
            return context.nowTick()
                    >= instance.startGameTick() + TRACE_DURATION_TICKS
                    ? SkillTickResult.COMPLETE
                    : SkillTickResult.CANCEL;
        }
        SteelFalchionLineTracker.tick(
                context.player(),
                context.nowTick()
        );
        if (SteelFalchionLineTracker.hasTrace(
                context.player().getUUID()
        )) {
            return SkillTickResult.CONTINUE;
        }
        return context.nowTick()
                >= instance.startGameTick() + TRACE_DURATION_TICKS
                ? SkillTickResult.COMPLETE
                : SkillTickResult.CANCEL;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        if (reason == SkillInstance.EndReason.COMPLETED) {
            return;
        }
        if (reason == SkillInstance.EndReason.CASTER_UNAVAILABLE) {
            SteelFalchionLineTracker.removePlayer(
                    context.player().getUUID()
            );
            return;
        }
        SteelFalchionLineTracker.cancelTrace(context.player(), true);
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

package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Steel Falchion's original slash trace.
 */
public final class SteelFalchionTraceSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 12.0F;
    public static final int TRACE_DURATION_TICKS = 100;
    public static final float TRACE_DOT_DAMAGE_MULTIPLIER = 0.50F;
    public static final int TRACE_SPEED_AMPLIFIER =
            SteelFalchionExecutionSupport.TRACE_SPEED_AMPLIFIER;

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
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Slash Trace energy payment failed"
            );
        }

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        Vec3 start = new Vec3(
                context.player().getX(),
                context.player().getY() + 0.02D,
                context.player().getZ()
        );
        SteelFalchionTraceExecutionState executionState =
                new SteelFalchionTraceExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        TRACE_DURATION_TICKS,
                        start,
                        TRACE_DOT_DAMAGE_MULTIPLIER,
                        context.weaponSnapshot()
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                executionState.start(
                        context.player(),
                        TRACE_DURATION_TICKS
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
                SteelFalchionTraceExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(SteelFalchionTraceExecutionState.class)
                .ifPresent(state -> state.cancel(
                        context.player(),
                        reason != SkillInstance.EndReason.COMPLETED
                                && reason != SkillInstance.EndReason
                                .CASTER_UNAVAILABLE
                ));
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

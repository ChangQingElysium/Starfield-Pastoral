package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.OssifiedMarkTracker;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Server-authoritative lifecycle for Ossified Blade's original execution circle.
 */
public final class OssifiedExecutionSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 6.0;
    public static final float ENERGY_COST = 10.0F;
    public static final float CIRCLE_RADIUS = 4.0F;
    public static final int DURATION_TICKS = 60;
    public static final int DAMAGE_INTERVAL_TICKS = 20;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final float PULSE_DAMAGE_MULTIPLIER = 1.0F;
    public static final float CRIT_DAMAGE_BONUS = 0.20F;
    public static final int RING_PARTICLE_INTERVAL_TICKS = 5;
    public static final double MINIMUM_PULL_DISTANCE = 0.01D;
    public static final double BASE_PULL_STRENGTH = 0.02D;
    public static final double INNER_PULL_BONUS = 0.03D;

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
        LivingEntity target = findMarkedTarget(context);
        if (target == null) {
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
        LivingEntity target = findMarkedTarget(context);
        if (target == null) {
            throw new IllegalStateException(
                    "Validated Ossified Execution target is no longer marked"
            );
        }
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Ossified Execution energy is no longer available"
            );
        }
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Ossified Execution energy payment failed"
            );
        }
        instance.setTargetEntityIds(List.of(target.getId()));

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        OssifiedExecutionState executionState =
                new OssifiedExecutionState(
                        target.position(),
                        CIRCLE_RADIUS,
                        context.player().level().dimension(),
                        context.nowTick(),
                        DURATION_TICKS
        );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                executionState.activate(context, target)
        );
    }

    public static float getCritDamageBonus(
            Player attacker,
            LivingEntity target,
            long nowTick
    ) {
        if (!(attacker instanceof ServerPlayer serverPlayer)
                || target == null) {
            return 0.0F;
        }
        return WeaponSkillRuntime.activeExecutionState(
                serverPlayer.getUUID(),
                BuiltinWeaponSkillHandlers.OSSIFIED_EXECUTION,
                OssifiedExecutionState.class
        ).map(state -> state.critDamageBonus(
                nowTick,
                serverPlayer.level().dimension(),
                target
        )).orElse(0.0F);
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
                OssifiedExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(OssifiedExecutionState.class)
                .ifPresent(OssifiedExecutionState::cancel);
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

    private static LivingEntity findMarkedTarget(SkillExecutionContext context) {
        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        );
        return target != null && OssifiedMarkTracker.isMarkedBy(
                target,
                context.player(),
                context.nowTick()
        )
                ? target
                : null;
    }
}

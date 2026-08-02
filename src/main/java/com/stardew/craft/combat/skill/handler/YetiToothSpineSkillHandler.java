package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.YetiToothEffects;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative lifecycle for Yeti Tooth's original ice-spine fan.
 */
public final class YetiToothSpineSkillHandler implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final int ANIMATION_TICKS = 8;
    public static final int SPINE_COUNT = 5;
    public static final float ARC_DEGREES = 120.0F;
    public static final float ANGLE_STEP_DEGREES = 30.0F;
    public static final double SPAWN_RADIUS = 2.5D;
    public static final int HIT_SLOW_DURATION_TICKS = 40;
    public static final int HIT_SLOW_AMPLIFIER = 1;
    public static final int HIT_FREEZE_DURATION_TICKS = 60;

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
                    "Validated Yeti Tooth Spine energy is no longer available"
            );
        }
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Yeti Tooth Spine energy payment failed"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        YetiToothSpineExecutionState executionState =
                new YetiToothSpineExecutionState(
                        context.player().level().dimension()
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() ->
                executionState.spawnSpines(context)
        );

        // Preserve the authored server notification order.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
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
        return instance.requireExecutionState(
                YetiToothSpineExecutionState.class
        ).isActive(context.player())
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(YetiToothSpineExecutionState.class)
                .ifPresent(state -> state.discardSpines(
                        context.player().server
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

    /** Applies the spine's authored control after exact positive damage. */
    public static void applySpineControl(LivingEntity target) {
        if (target == null) {
            return;
        }
        boolean alreadySlowed = target.hasEffect(
                MobEffects.MOVEMENT_SLOWDOWN
        );
        if (alreadySlowed
                && target.level() instanceof ServerLevel serverLevel) {
            YetiToothEffects.applyFreeze(
                    serverLevel,
                    target,
                    HIT_FREEZE_DURATION_TICKS
            );
            return;
        }
        YetiToothEffects.applySlow(
                target,
                HIT_SLOW_DURATION_TICKS,
                HIT_SLOW_AMPLIFIER
        );
    }
}

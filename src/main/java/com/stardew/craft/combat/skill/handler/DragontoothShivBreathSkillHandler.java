package com.stardew.craft.combat.skill.handler;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Server-authoritative lifecycle for Dragontooth Shiv's original breath stance.
 */
public final class DragontoothShivBreathSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final int ACTIVE_DURATION_TICKS = 120;
    public static final int SPEED_AMPLIFIER = 0;
    public static final int RESISTANCE_AMPLIFIER = 1;
    public static final int ANIMATION_TICKS = 8;

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
        if (!canPayEnergy(context)
                || !WeaponSkillRuntime.consumeEnergyDuringBegin(
                        context,
                        instance,
                        ENERGY_COST
                )) {
            throw new IllegalStateException(
                    "Validated Dragontooth breath energy is unavailable"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        DragontoothShivBreathExecutionState executionState =
                new DragontoothShivBreathExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        ACTIVE_DURATION_TICKS
                );
        instance.initializeExecutionState(executionState);
        instance.registerCommittedEffect(() -> {
            executionState.start(
                    context.player(),
                    ACTIVE_DURATION_TICKS
            );
            context.player().addEffect(new MobEffectInstance(
                    ModMobEffects.SPEED,
                    ACTIVE_DURATION_TICKS,
                    SPEED_AMPLIFIER,
                    false,
                    true,
                    true
            ));
            context.player().addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    ACTIVE_DURATION_TICKS,
                    RESISTANCE_AMPLIFIER,
                    false,
                    true,
                    true
            ));
        });

        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                ANIMATION_TICKS
        );
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

    /**
     * Exact runtime-owned stance query for normal attacks and damage rules.
     */
    public static boolean isActive(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.DRAGONTOOTH_SHIV_BREATH,
                DragontoothShivBreathExecutionState.class
        ).filter(state -> state.isActive(
                nowTick,
                player.level().dimension(),
                player.isAlive() && !player.isRemoved()
        )).isPresent();
    }

    @Override
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        return instance.requireExecutionState(
                DragontoothShivBreathExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(DragontoothShivBreathExecutionState.class)
                .ifPresent(state -> state.cancel(context.player()));
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

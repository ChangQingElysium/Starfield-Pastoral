package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.IridiumNeedleFrenzyPayload;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative lifecycle for Iridium Needle's original Iridium Frenzy.
 */
public final class IridiumNeedleFrenzySkillHandler implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final int DURATION_TICKS = 120;
    public static final int SPEED_AMPLIFIER = 0;
    public static final float CRIT_CHANCE_BONUS = 0.30F;
    public static final int CRITICAL_HEAL_AMOUNT = 5;
    public static final float CRITICAL_ENERGY_RESTORE = 10.0F;
    public static final int CRITICAL_VULNERABLE_DURATION_TICKS = 40;
    public static final int CRITICAL_VULNERABLE_AMPLIFIER = 1;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
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
                    "Validated Iridium Frenzy energy is no longer available"
            );
        }
        MobEffect speedEffect = Objects.requireNonNull(
                ModMobEffects.SPEED.get(),
                "Iridium Frenzy speed effect"
        );
        Holder<MobEffect> speed = Holder.direct(speedEffect);

        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Iridium Frenzy energy payment failed"
            );
        }

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.initializeExecutionState(
                new IridiumNeedleFrenzyExecutionState(
                        context.player().level().dimension(),
                        context.nowTick(),
                        DURATION_TICKS
                )
        );
        instance.registerCommittedEffect(() -> {
            PacketDistributor.sendToPlayer(
                    context.player(),
                    new IridiumNeedleFrenzyPayload(true, DURATION_TICKS)
            );
            context.player().addEffect(new MobEffectInstance(
                    speed,
                    DURATION_TICKS,
                    SPEED_AMPLIFIER,
                    false,
                    true,
                    true
            ));
        });
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
                IridiumNeedleFrenzyExecutionState.class
        ).advance(context);
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(IridiumNeedleFrenzyExecutionState.class)
                .ifPresent(state -> {
                    state.cancel();
                    if (shouldNotifyOnFinish(reason)) {
                        PacketDistributor.sendToPlayer(
                                context.player(),
                                new IridiumNeedleFrenzyPayload(false, 0)
                        );
                    }
                });
    }

    public static boolean isActive(
            ServerPlayer player,
            long nowTick
    ) {
        if (player == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.IRIDIUM_NEEDLE_FRENZY,
                IridiumNeedleFrenzyExecutionState.class
        ).filter(state -> state.isActive(
                nowTick,
                player.level().dimension()
        )).isPresent();
    }

    static boolean shouldNotifyOnFinish(SkillInstance.EndReason reason) {
        return reason != SkillInstance.EndReason.CASTER_UNAVAILABLE;
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

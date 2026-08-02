package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.equipment.EquipmentMobEffectHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class YetiToothEffects {

    private YetiToothEffects() {}

    @SuppressWarnings("null")
    public static void applyFreeze(ServerLevel level, LivingEntity target, int durationTicks) {
        if (level == null || target == null) {
            return;
        }

        int appliedDuration = YetiFreezeTracker.applyWithEquipmentProtection(
                target,
                level.getGameTime(),
                durationTicks,
                YetiFreezeTracker.PresentationPolicy.SYNC_FREEZE_OVERLAY
        );
        if (appliedDuration <= 0) {
            return;
        }
        EquipmentMobEffectHandler.addPreAdjustedEffect(
                target,
                new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        appliedDuration,
                        255,
                        false,
                        true,
                        true
                )
        );
        EquipmentMobEffectHandler.addPreAdjustedEffect(
                target,
                new MobEffectInstance(
                        MobEffects.JUMP,
                        appliedDuration,
                        255,
                        false,
                        false,
                        false
                )
        );

        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6;
        double z = target.getZ();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
            x, y, z,
            12, 0.35, 0.25, 0.35, 0.02);
        level.playSound(null, target.blockPosition(),
            net.minecraft.sounds.SoundEvents.GLASS_BREAK,
            net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    @SuppressWarnings("null")
    public static void applySlow(LivingEntity target, int durationTicks, int amplifier) {
        if (target == null) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, amplifier, false, true, true));
    }

    public static void applyPreAdjustedSlow(
            LivingEntity target,
            int durationTicks,
            int amplifier
    ) {
        if (target == null || durationTicks <= 0) {
            return;
        }
        EquipmentMobEffectHandler.addPreAdjustedEffect(
                target,
                new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        durationTicks,
                        amplifier,
                        false,
                        true,
                        true
                )
        );
    }
}

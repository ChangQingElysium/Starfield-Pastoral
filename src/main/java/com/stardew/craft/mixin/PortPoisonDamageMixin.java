package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.PortDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): NeoForge 21.1 replaces {@code DamageSources#magic()} in the poison effect tick with the
 * {@code neoforge:poison} damage type (see {@link PortDamageTypes}). In 1.20.1 the poison branch is the first
 * {@code magic()} call of {@code MobEffect#applyEffectTick} (the second one is instant harming/healing).
 */
@Mixin(MobEffect.class)
public abstract class PortPoisonDamageMixin {
    @WrapOperation(method = "applyEffectTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSources;magic()Lnet/minecraft/world/damagesource/DamageSource;",
                    ordinal = 0))
    private DamageSource stardewcraft$poisonDamageType(DamageSources sources, Operation<DamageSource> original,
            LivingEntity entity, int amplifier) {
        return PortDamageTypes.poison(entity);
    }
}

package com.stardew.craft.platform.mixin.event.entity.living.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.event.entity.living.MobEffectHooks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class MobEffectApplicationMixin {
    @WrapOperation(method = {"addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", "forceAddEffect"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean stardewcraft$effectApplicable(LivingEntity target, MobEffectInstance effect, Operation<Boolean> original,
            @Local(argsOnly = true) Entity source) {
        return MobEffectHooks.applicable(target, effect, source, () -> original.call(target, effect));
    }
}

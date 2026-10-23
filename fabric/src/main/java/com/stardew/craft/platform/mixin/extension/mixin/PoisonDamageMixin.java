package com.stardew.craft.platform.mixin.extension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.extension.PoisonDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.effect.PoisonMobEffect")
public abstract class PoisonDamageMixin {
    @WrapOperation(method = "applyEffectTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/DamageSources;magic()Lnet/minecraft/world/damagesource/DamageSource;"))
    private DamageSource stardewcraft$poisonType(DamageSources sources, Operation<DamageSource> original,
            @Local(argsOnly = true) LivingEntity target) {
        return PoisonDamage.source(target.registryAccess(), () -> original.call(sources));
    }
}

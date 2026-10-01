package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.event.PortEventHooks;
import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): NeoForge's {@code MobEffectEvent.Applicable#getEffectSource()}. Forge fires Applicable from
 * {@code canBeAffected}, which has no source; the source passed to {@code addEffect}/{@code forceAddEffect} is
 * exposed for the duration of that check through {@link PortEventHooks#effectSource()}.
 */
@Mixin(LivingEntity.class)
public abstract class PortLivingEntityEffectSourceMixin {
    @WrapOperation(
            method = {
                    "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
                    "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V"
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean stardewcraft$exposeEffectSource(LivingEntity self, MobEffectInstance effect, Operation<Boolean> original,
            @Local(argsOnly = true) @Nullable Entity source) {
        return PortEventHooks.withEffectSource(source, () -> original.call(self, effect));
    }
}

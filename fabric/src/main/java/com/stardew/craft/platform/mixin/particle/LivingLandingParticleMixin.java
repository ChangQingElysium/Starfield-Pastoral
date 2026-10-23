package com.stardew.craft.platform.mixin.particle;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.particle.BlockParticleSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingLandingParticleMixin {
    @ModifyExpressionValue(method = "checkFallDamage", at = @At(value = "NEW",
            target = "(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/particles/BlockParticleOption;"), require = 1, allow = 1)
    private BlockParticleOption stardewcraft$landingSource(BlockParticleOption option, @Local(argsOnly = true) BlockPos sourcePos) {
        return BlockParticleSources.withSource(option, sourcePos);
    }
}

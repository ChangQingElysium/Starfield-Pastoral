package com.stardew.craft.platform.mixin.particle;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.particle.BlockParticleSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntitySprintParticleMixin {
    @ModifyExpressionValue(method = "spawnSprintParticle", at = @At(value = "NEW",
            target = "(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/particles/BlockParticleOption;"), require = 1, allow = 1)
    private BlockParticleOption stardewcraft$sprintSource(BlockParticleOption option, @Local(ordinal = 0) BlockPos sourcePos) {
        // The first local is getOnPosLegacy(); the second is the entity's own blockPosition().
        return BlockParticleSources.withSource(option, sourcePos);
    }
}

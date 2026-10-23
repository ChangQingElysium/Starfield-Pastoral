package com.stardew.craft.platform.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.client.particle.TerrainParticleSprites;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineSpriteMixin {
    // method_34020 is the native destroy() shape-box lambda. Both native calls explicitly pass the source position.
    @WrapOperation(method = { "crack", "method_34020" }, at = @At(value = "NEW",
            target = "(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/client/particle/TerrainParticle;"), require = 2, allow = 2)
    private TerrainParticle stardewcraft$blockSource(ClientLevel level, double x, double y, double z,
            double vx, double vy, double vz, BlockState state, BlockPos sourcePos, Operation<TerrainParticle> original) {
        return TerrainParticleSprites.update(original.call(level, x, y, z, vx, vy, vz, state, sourcePos), state, sourcePos);
    }
}

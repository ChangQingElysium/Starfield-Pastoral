package com.stardew.craft.platform.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.client.particle.TerrainParticleSprites;
import com.stardew.craft.platform.fabric.particle.BlockParticleSources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TerrainParticle.class)
public abstract class TerrainParticleSpriteMixin extends TextureSheetParticle implements TerrainParticleSprites.SpriteAccess {
    protected TerrainParticleSpriteMixin(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }

    @Override public void stardewcraft$updateSprite(BlockState state, BlockPos sourcePos) {
        setSprite(TerrainParticleSprites.getTexture(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper(), state, level, sourcePos));
    }

    @WrapOperation(method = "createTerrainParticle", at = @At(value = "NEW",
            target = "(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/particle/TerrainParticle;"), require = 1, allow = 1)
    private static TerrainParticle stardewcraft$providerSource(ClientLevel level, double x, double y, double z,
            double vx, double vy, double vz, BlockState state, Operation<TerrainParticle> original,
            @Local(argsOnly = true) BlockParticleOption option) {
        return TerrainParticleSprites.update(original.call(level, x, y, z, vx, vy, vz, state), state, BlockParticleSources.get(option));
    }
}

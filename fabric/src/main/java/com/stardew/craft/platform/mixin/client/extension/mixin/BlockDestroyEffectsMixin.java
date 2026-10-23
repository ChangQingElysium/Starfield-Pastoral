package com.stardew.craft.platform.mixin.client.extension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.client.extension.IClientBlockExtensions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ParticleEngine.class)
public abstract class BlockDestroyEffectsMixin {
    @Shadow private ClientLevel level;
    @WrapOperation(method = "destroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;shouldSpawnTerrainParticles()Z"))
    private boolean stardewcraft$destroy(BlockState state, Operation<Boolean> original, BlockPos pos, BlockState originalState) {
        // Replaces only the non-air predicate. DEFAULT delegates to the original terrain-particle flag.
        return !IClientBlockExtensions.of(state).addDestroyEffects(state, level, pos, (ParticleEngine) (Object) this);
    }
}

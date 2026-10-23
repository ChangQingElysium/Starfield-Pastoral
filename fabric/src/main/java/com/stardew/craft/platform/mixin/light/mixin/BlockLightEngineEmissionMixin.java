package com.stardew.craft.platform.mixin.light.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.light.LightEmissionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockLightEngine.class)
abstract class BlockLightEngineEmissionMixin<M extends DataLayerStorageMap<M>, S extends LayerLightSectionStorage<M>>
        extends LightEngine<M, S> {
    protected BlockLightEngineEmissionMixin(LightChunkGetter chunkSource, S storage) {
        super(chunkSource, storage);
    }

    @WrapOperation(method = "getEmission", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"), require = 1)
    private int stardewcraft$contextualNodeEmission(BlockState queried, Operation<Integer> original,
            long packedPos, BlockState state) {
        return LightEmissionHooks.resolve(queried, chunkSource.getLevel(), BlockPos.of(packedPos), original.call(queried));
    }

    @WrapOperation(method = "method_51532(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"), require = 1)
    private int stardewcraft$contextualInitialSource(BlockState queried, Operation<Integer> original,
            BlockPos pos, BlockState state) {
        return LightEmissionHooks.resolve(queried, chunkSource.getLevel(), pos, original.call(queried));
    }
}

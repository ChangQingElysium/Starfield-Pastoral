package com.stardew.craft.platform.mixin.light.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.light.LightEmissionHooks;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkAccess.class)
abstract class ChunkLightSourcesMixin {
    @WrapOperation(method = "findBlockLightSources", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;findBlocks(Ljava/util/function/Predicate;Ljava/util/function/BiConsumer;)V"), require = 1)
    private void stardewcraft$includeDynamicPaletteSources(ChunkAccess chunk, Predicate<BlockState> coarse,
            BiConsumer<BlockPos, BlockState> output, Operation<Void> original) {
        // Keep the native section-palette optimization; only the dynamic candidates need a position-aware fine filter.
        original.call(chunk, (Predicate<BlockState>) state -> coarse.test(state) || LightEmissionHooks.dynamic(state),
                (BiConsumer<BlockPos, BlockState>) (pos, state) -> {
                    if (!LightEmissionHooks.dynamic(state) || LightEmissionHooks.getLightEmission(state, chunk, pos) > 0) {
                        output.accept(pos, state);
                    }
                });
    }
}

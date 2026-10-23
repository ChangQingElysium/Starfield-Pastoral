package com.stardew.craft.platform.mixin.event.level.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.level.PlacementTransactions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunk.class)
public abstract class LevelChunkPlacementMixin {
    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private void stardewcraft$deferOnPlace(BlockState state, Level level, BlockPos pos, BlockState previous,
            boolean moving, Operation<Void> original) {
        if (!PlacementTransactions.isCapturing(level) && !PlacementTransactions.isRestoring(level)) {
            original.call(state, level, pos, previous, moving);
        }
    }
}

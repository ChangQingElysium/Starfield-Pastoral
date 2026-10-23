package com.stardew.craft.platform.mixin.event.level.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.level.BlockSnapshot;
import com.stardew.craft.platform.fabric.event.level.PlacementTransactions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(Level.class)
public abstract class LevelPlacementMixin {
    @WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState stardewcraft$capture(LevelChunk chunk, BlockPos pos, BlockState state, boolean moving,
            Operation<BlockState> original, BlockPos requestedPos, BlockState requestedState, int flags, int recursionLeft) {
        BlockSnapshot snapshot = PlacementTransactions.beforeSet((Level) (Object) this, pos, flags, recursionLeft);
        BlockState previous = original.call(chunk, pos, state, moving);
        PlacementTransactions.afterSet(snapshot, previous != null);
        return previous;
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"),
            cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void stardewcraft$deferUpdates(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir, LevelChunk chunk, Block block, BlockState previous) {
        Level level = (Level) (Object) this;
        if (PlacementTransactions.isCapturing(level) || PlacementTransactions.isRestoring(level)) {
            cir.setReturnValue(previous != null);
        }
    }
}

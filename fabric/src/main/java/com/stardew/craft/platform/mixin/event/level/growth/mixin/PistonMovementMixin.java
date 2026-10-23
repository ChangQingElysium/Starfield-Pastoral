package com.stardew.craft.platform.mixin.event.level.growth.mixin;


import com.stardew.craft.platform.fabric.event.level.growth.GrowthEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
abstract class PistonMovementMixin {
    @Inject(method = "triggerEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/piston/PistonBaseBlock;moveBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Z", ordinal = 0), cancellable = true, require = 1)
    private void stardew$extend(BlockState state, Level level, BlockPos pos, int id, int param, CallbackInfoReturnable<Boolean> cir) {
        if (GrowthEvents.piston(level, pos, state.getValue(PistonBaseBlock.FACING), true)) cir.setReturnValue(false);
    }
    @Inject(method = "triggerEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;", ordinal = 0), cancellable = true, require = 1)
    private void stardew$retract(BlockState state, Level level, BlockPos pos, int id, int param, CallbackInfoReturnable<Boolean> cir) {
        if (GrowthEvents.piston(level, pos, state.getValue(PistonBaseBlock.FACING), false)) cir.setReturnValue(false);
    }
}

package com.stardew.craft.platform.mixin.event.level.growth.mixin;


import com.stardew.craft.platform.fabric.event.level.growth.GrowthEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.FluidState;

@Mixin(net.minecraft.world.level.material.LavaFluid.class)
abstract class FluidPlaceBlockMixin {
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"), require = 2)
    private boolean stardew$ignition(Level level, BlockPos pos, BlockState proposed, Operation<Boolean> original,
            Level methodLevel, BlockPos liquidPos, FluidState fluid, RandomSource random) {
        return original.call(level, pos, GrowthEvents.fluidState(level, pos, liquidPos, proposed));
    }
    @WrapOperation(method = "spreadTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), require = 1)
    private boolean stardew$stone(LevelAccessor level, BlockPos pos, BlockState proposed, int flags, Operation<Boolean> original) {
        return original.call(level, pos, GrowthEvents.fluidState(level, pos, pos, proposed), flags);
    }
}

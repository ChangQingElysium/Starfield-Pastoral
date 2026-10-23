package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChorusFlowerBlock.class)
abstract class ChorusFlowerGrowthSoilMixin {
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 0), require = 1)
    private boolean stardewcraft$initialSoil(BlockState soil, Block queried, Operation<Boolean> original,
            BlockState plant, ServerLevel level, BlockPos pos, RandomSource random) {
        var decision = PlantHooks.canSustainPlant(soil, level, pos.below(), Direction.UP, plant);
        return decision.isDefault() ? original.call(soil, queried) : decision.isTrue();
    }
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 1), require = 1)
    private boolean stardewcraft$vetoSkipsTheNativeColumnBranch(BlockState soil, Block queried, Operation<Boolean> original,
            BlockState plant, ServerLevel level, BlockPos pos, RandomSource random) {
        return !PlantHooks.canSustainPlant(soil, level, pos.below(), Direction.UP, plant).isFalse() && original.call(soil, queried);
    }
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"), require = 1)
    private boolean stardewcraft$vetoSkipsTheNativeAirBranch(BlockState soil, Operation<Boolean> original,
            BlockState plant, ServerLevel level, BlockPos pos, RandomSource random) {
        return !PlantHooks.canSustainPlant(soil, level, pos.below(), Direction.UP, plant).isFalse() && original.call(soil);
    }
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 3), require = 1)
    private boolean stardewcraft$columnRootSoil(BlockState soil, Block queried, Operation<Boolean> original,
            BlockState plant, ServerLevel level, BlockPos pos, RandomSource random, @Local(index = 10) int depth) {
        return original.call(soil, queried) || PlantHooks.canSustainPlant(soil, level, pos.below(depth + 1), Direction.UP, plant).isTrue();
    }
}

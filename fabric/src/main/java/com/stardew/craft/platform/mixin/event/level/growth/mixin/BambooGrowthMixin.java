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

/** Neo draws only after space, light and maximum-height checks. */
@Mixin(net.minecraft.world.level.block.BambooStalkBlock.class)
abstract class BambooGrowthMixin {
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 0), require = 1)
    private int stardew$deferRoll(RandomSource random, int bound, Operation<Integer> original) { return 0; }

    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BambooStalkBlock;growBamboo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;I)V"), require = 1)
    private void stardew$decide(net.minecraft.world.level.block.BambooStalkBlock block, BlockState state,
            net.minecraft.world.level.Level level, BlockPos pos, RandomSource random, int height, Operation<Void> original) {
        if (GrowthEvents.canGrow(level, pos, state, random.nextInt(3) == 0)) original.call(block, state, level, pos, random, height);
    }
}

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

/** These three native draws already occur after their exact Neo growth guards. */
@Mixin({net.minecraft.world.level.block.CropBlock.class, net.minecraft.world.level.block.StemBlock.class,
        net.minecraft.world.level.block.NetherWartBlock.class})
abstract class RandomCropGrowthMixin {
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 0), require = 1)
    private int stardew$growthRoll(RandomSource random, int bound, Operation<Integer> original,
            BlockState state, ServerLevel level, BlockPos pos, RandomSource tickRandom) {
        return GrowthEvents.canGrow(level, pos, state, original.call(random, bound) == 0) ? 0 : 1;
    }
}

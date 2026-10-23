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

@Mixin(net.minecraft.world.level.block.SweetBerryBushBlock.class)
abstract class BerryGrowthMixin {
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 0), require = 1)
    private int stardew$lightBeforeRoll(RandomSource random, int bound, Operation<Integer> original,
            BlockState state, ServerLevel level, BlockPos pos, RandomSource tickRandom) {
        if (level.getRawBrightness(pos.above(), 0) < 9) return 1;
        return GrowthEvents.canGrow(level, pos, state, original.call(random, bound) == 0) ? 0 : 1;
    }
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getRawBrightness(Lnet/minecraft/core/BlockPos;I)I"), require = 1)
    private int stardew$alreadyCheckedLight(ServerLevel level, BlockPos pos, int darken, Operation<Integer> original) { return 9; }
}

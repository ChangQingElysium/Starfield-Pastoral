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
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

@Mixin(net.minecraft.world.level.block.GrowingPlantHeadBlock.class)
abstract class GrowingHeadGrowthMixin extends net.minecraft.world.level.block.GrowingPlantBlock {
    protected GrowingHeadGrowthMixin(Properties properties, Direction direction, net.minecraft.world.phys.shapes.VoxelShape shape, boolean fluidTicks) {
        super(properties, direction, shape, fluidTicks);
    }
    @Shadow @Final private double growPerTickProbability;
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextDouble()D"), require = 1)
    private double stardew$growthRoll(RandomSource random, Operation<Double> original,
            BlockState state, ServerLevel level, BlockPos pos, RandomSource tickRandom) {
        boolean grow = GrowthEvents.canGrow(level, pos.relative(growthDirection), state, original.call(random) < growPerTickProbability);
        return grow ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    }
}

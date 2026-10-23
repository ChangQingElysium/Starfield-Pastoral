package com.stardew.craft.platform.mixin.event.level.growth.mixin;


import com.stardew.craft.platform.fabric.event.level.growth.GrowthEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.level.block.grower.TreeGrower.class)
abstract class TreeFeatureGrowthMixin {
    @ModifyExpressionValue(method = "growTree", at = @At(value = "INVOKE", target = "Ljava/util/Optional;orElse(Ljava/lang/Object;)Ljava/lang/Object;"), require = 2)
    @SuppressWarnings("unchecked")
    private Object stardew$chosenFeature(Object chosen, ServerLevel level, ChunkGenerator generator, BlockPos pos,
            BlockState state, RandomSource random, @Share("stardew$featureCanceled") LocalBooleanRef canceled) {
        var event = GrowthEvents.feature(level, random, pos, (Holder<ConfiguredFeature<?, ?>>) chosen);
        if (event.isCanceled()) { canceled.set(true); return null; }
        return event.getFeature();
    }
    @Inject(method = "growTree", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/grower/TreeGrower;hasFlowers(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Z"), cancellable = true, require = 1)
    private void stardew$cancelMegaWithoutFallback(ServerLevel level, ChunkGenerator generator, BlockPos pos,
            BlockState state, RandomSource random, CallbackInfoReturnable<Boolean> cir,
            @Share("stardew$featureCanceled") LocalBooleanRef canceled) {
        if (canceled.get()) cir.setReturnValue(false);
    }
}

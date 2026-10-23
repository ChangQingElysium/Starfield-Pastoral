package com.stardew.craft.platform.mixin.event.level.growth.mixin;


import com.stardew.craft.platform.fabric.event.level.growth.GrowthEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

@Mixin(net.minecraft.world.level.block.MushroomBlock.class)
abstract class MushroomFeatureGrowthMixin {
    @ModifyExpressionValue(method = "growMushroom", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;getHolder(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;"), require = 1)
    private Optional<Holder<ConfiguredFeature<?, ?>>> stardew$chosenFeature(Optional<Holder<ConfiguredFeature<?, ?>>> selected,
            ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        var event = GrowthEvents.feature(level, random, pos, selected.orElse(null));
        return event.isCanceled() ? Optional.empty() : Optional.ofNullable(event.getFeature());
    }
}

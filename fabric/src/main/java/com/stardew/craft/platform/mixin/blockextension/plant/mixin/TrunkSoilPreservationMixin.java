package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrunkPlacer.class)
abstract class TrunkSoilPreservationMixin {
    @Inject(method = "setDirtAt", at = @At("HEAD"), cancellable = true, require = 1)
    private static void stardewcraft$preserveHandledSoil(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> place,
            RandomSource random, BlockPos pos, TreeConfiguration config, CallbackInfo result) {
        if (PlantHooks.handlesTreeSoil(level, place, random, pos, config)) result.cancel();
    }
}

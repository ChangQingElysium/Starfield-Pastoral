package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MushroomBlock.class)
abstract class MushroomSoilMixin {
    @Inject(method = "canSurvive", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelReader;getRawBrightness(Lnet/minecraft/core/BlockPos;I)I"), cancellable = true, require = 1)
    private void stardewcraft$soilAfterNativeGrowTag(BlockState plant, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        var decision = PlantHooks.upwardDecision(plant, level, pos);
        if (!decision.isDefault()) result.setReturnValue(decision.isTrue());
    }
}

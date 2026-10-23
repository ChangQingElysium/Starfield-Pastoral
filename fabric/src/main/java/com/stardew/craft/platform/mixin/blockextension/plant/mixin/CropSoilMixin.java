package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({BushBlock.class, CropBlock.class, PitcherCropBlock.class})
abstract class CropSoilMixin {
    @ModifyReturnValue(method = "mayPlaceOn", at = @At("RETURN"), require = 1)
    private boolean stardewcraft$nativeFarmlandSubclasses(boolean nativeResult, BlockState soil, BlockGetter level, BlockPos pos) {
        return nativeResult || soil.getBlock() instanceof FarmBlock;
    }
}

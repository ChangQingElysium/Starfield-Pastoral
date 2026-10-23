package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PitcherCropBlock.class)
abstract class PitcherSoilMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$lowerHalfInDimLight(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        if (state.is(Blocks.PITCHER_CROP) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER && level.getRawBrightness(pos, 0) < 8)
            result.setReturnValue(PlantHooks.upwardDecision(state, level, pos).isTrue());
    }
}

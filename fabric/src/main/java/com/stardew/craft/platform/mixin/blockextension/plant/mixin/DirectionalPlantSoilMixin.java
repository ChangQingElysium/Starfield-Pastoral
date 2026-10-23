package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CocoaBlock.class, MangrovePropaguleBlock.class})
abstract class DirectionalPlantSoilMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$directionalSoil(BlockState plant, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        BlockPos soil; Direction facing;
        if (plant.getBlock() instanceof CocoaBlock) { var direction = plant.getValue(CocoaBlock.FACING); soil = pos.relative(direction); facing = direction.getOpposite(); }
        else if (plant.getValue(MangrovePropaguleBlock.HANGING)) { soil = pos.above(); facing = Direction.DOWN; }
        else return;
        var decision = PlantHooks.canSustainPlant(level.getBlockState(soil), level, soil, facing, plant);
        if (!decision.isDefault()) result.setReturnValue(decision.isTrue());
    }
}

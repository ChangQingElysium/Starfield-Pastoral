package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BushBlock.class, CropBlock.class, BambooStalkBlock.class, BambooSaplingBlock.class, BigDripleafBlock.class, ChorusFlowerBlock.class})
abstract class UpwardPlantSupportMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$contextualSoil(BlockState plant, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        var decision = PlantHooks.upwardDecision(plant, level, pos);
        if (!decision.isDefault()) result.setReturnValue(decision.isTrue());
    }
}

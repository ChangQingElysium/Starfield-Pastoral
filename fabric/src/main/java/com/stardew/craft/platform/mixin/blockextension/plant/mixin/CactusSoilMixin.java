package com.stardew.craft.platform.mixin.blockextension.plant.mixin;


import com.stardew.craft.platform.fabric.blockextension.plant.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CactusBlock.class)
abstract class CactusSoilMixin {
    @Inject(method = "canSurvive", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 0), cancellable = true, require = 1)
    private void stardewcraft$soilAfterSideObstructionChecks(BlockState plant, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        var decision = PlantHooks.upwardDecision(plant, level, pos);
        if (!decision.isDefault()) result.setReturnValue(decision.isTrue());
    }
}

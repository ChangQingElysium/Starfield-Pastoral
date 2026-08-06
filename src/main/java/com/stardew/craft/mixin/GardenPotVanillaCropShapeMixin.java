package com.stardew.craft.mixin;

import com.stardew.craft.block.utility.GardenPotBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps selection and Jade targeting on the pot rather than its invisible carrier crop. */
@Mixin(CropBlock.class)
public abstract class GardenPotVanillaCropShapeMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$emptyPottedCropShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context,
            CallbackInfoReturnable<VoxelShape> callback
    ) {
        if (GardenPotBlock.isPottedPlant(level, pos, state)) {
            callback.setReturnValue(Shapes.empty());
        }
    }
}

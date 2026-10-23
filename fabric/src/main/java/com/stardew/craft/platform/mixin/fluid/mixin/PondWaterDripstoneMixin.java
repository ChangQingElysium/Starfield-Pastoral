package com.stardew.craft.platform.mixin.fluid.mixin;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stardew.craft.platform.fabric.fluid.PondWaterDrips;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PointedDripstoneBlock.class)
public abstract class PondWaterDripstoneMixin {
    @ModifyExpressionValue(method = "maybeTransferFluid", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/block/PointedDripstoneBlock$FluidInfo;fluid:Lnet/minecraft/world/level/material/Fluid;"))
    private static Fluid stardewcraft$useWaterDripChanceAndCauldron(Fluid fluid) {
        return PondWaterDrips.cauldronFluid(fluid);
    }

    @ModifyReturnValue(method = "canFillCauldron", at = @At("RETURN"))
    private static boolean stardewcraft$acceptPondWater(boolean original, Fluid fluid) {
        return original || PondWaterDrips.canFillCauldron(fluid);
    }

    @ModifyReturnValue(method = "getCauldronFillFluidType", at = @At("RETURN"))
    private static Fluid stardewcraft$deliverWaterToCauldron(Fluid fluid) {
        return PondWaterDrips.cauldronFluid(fluid);
    }
}

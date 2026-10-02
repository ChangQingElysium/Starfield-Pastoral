package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.PortPlantSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PORT(1.20.1): NeoForge 1.21.1 stem soil and fruit-placement rules (any FarmBlock; fruit only on farmland or dirt). */
@Mixin(StemBlock.class)
public abstract class PortStemBlockPlantSupportMixin {
    @Inject(method = "mayPlaceOn", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121MayPlaceOn(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!PortPlantSupport.portRules(state, (Block) (Object) this)) return;
        cir.setReturnValue(PortPlantSupport.farmland(state));
    }

    /** 1.21.1 places the fruit on {@code instanceof FarmBlock || #dirt}; 1.20.1 also asked the soil's Forge hook. */
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;canSustainPlant(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraftforge/common/IPlantable;)Z", remap = false))
    private boolean stardewcraft$port121FruitSoil(BlockState soil, BlockGetter level, BlockPos pos, Direction facing,
                                                  IPlantable fruit, Operation<Boolean> original) {
        return PortPlantSupport.legacyForgeSoil(soil) || !PortPlantSupport.portRules(soil, (Block) (Object) this)
                ? original.call(soil, level, pos, facing, fruit) : PortPlantSupport.farmland(soil);
    }
}

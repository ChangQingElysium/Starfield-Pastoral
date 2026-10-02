package com.stardew.craft.mixin;

import com.stardew.craft.port.PortPlantSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PORT(1.20.1): NeoForge 1.21.1 accepts any FarmBlock below an attached stem. */
@Mixin(AttachedStemBlock.class)
public abstract class PortAttachedStemBlockPlantSupportMixin {
    @Inject(method = "mayPlaceOn", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121MayPlaceOn(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!PortPlantSupport.portRules(state, (Block) (Object) this)) return;
        cir.setReturnValue(PortPlantSupport.farmland(state));
    }
}

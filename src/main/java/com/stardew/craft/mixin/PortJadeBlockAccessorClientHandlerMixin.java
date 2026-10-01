package com.stardew.craft.mixin;

import com.stardew.craft.integration.jade.JadeBlockDataProviders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snownee.jade.api.BlockAccessor;

/** PORT(1.20.1): Jade 15 requests server data for block-keyed providers even without a block entity. */
@Pseudo
@Mixin(targets = "snownee.jade.impl.BlockAccessorClientHandler", remap = false)
public abstract class PortJadeBlockAccessorClientHandlerMixin {
    @Inject(method = "shouldRequestData(Lsnownee/jade/api/BlockAccessor;)Z", at = @At("RETURN"), cancellable = true)
    private void stardewcraft$requestBlockKeyedData(BlockAccessor accessor, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && JadeBlockDataProviders.hasBlockProviders(accessor.getBlock())) {
            callback.setReturnValue(true);
        }
    }
}

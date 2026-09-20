package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorIrisPipeline;
import com.stardew.craft.client.interior.TownDoorRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents Iris' deferred shader-pack framebuffer pipeline from being entered recursively. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public abstract class TownDoorIrisPipelineManagerMixin {
    @Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true, require = 0)
    @SuppressWarnings("rawtypes")
    private void stardewcraft$useVanillaNestedPipeline(@Coerce Object dimension,
                                                         CallbackInfoReturnable cir) {
        if (!TownDoorRenderContext.isRendering()) return;
        Object pipeline = TownDoorIrisPipeline.vanilla();
        if (pipeline != null) cir.setReturnValue(pipeline);
    }
}

package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorIrisPipeline;
import com.stardew.craft.client.interior.TownDoorRenderContext;
import com.stardew.craft.client.interior.TownDoorClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Prevents Iris' deferred shader-pack framebuffer pipeline from being entered recursively. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public abstract class TownDoorIrisPipelineManagerMixin {
    @Inject(method = "destroyPipeline", at = @At("HEAD"), require = 1)
    private void stardewcraft$releaseDoorPipelines(CallbackInfo ci) {
        TownDoorClient.shaderReload();
    }
    @Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true, require = 1)
    @SuppressWarnings("rawtypes")
    private void stardewcraft$useVanillaNestedPipeline(@Coerce Object dimension,
                                                         CallbackInfoReturnable cir) {
        if (!TownDoorRenderContext.isRendering()) return;
        Object pipeline = TownDoorIrisPipeline.nested();
        if (pipeline != null) cir.setReturnValue(pipeline);
    }

    // Sodium, entity shader overrides and particles query the manager directly. Returning a
    // vanilla pipeline only from preparePipeline leaves those callers drawing into the outer pack.
    @Inject(method = "getPipelineNullable", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$nestedPipeline(CallbackInfoReturnable<Object> cir) {
        if (TownDoorRenderContext.isRendering() && TownDoorIrisPipeline.nested() != null) {
            cir.setReturnValue(TownDoorIrisPipeline.nested());
        }
    }

    @Inject(method = "getPipeline", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$nestedOptionalPipeline(CallbackInfoReturnable<Optional<?>> cir) {
        if (TownDoorRenderContext.isRendering() && TownDoorIrisPipeline.nested() != null) {
            cir.setReturnValue(Optional.of(TownDoorIrisPipeline.nested()));
        }
    }
}

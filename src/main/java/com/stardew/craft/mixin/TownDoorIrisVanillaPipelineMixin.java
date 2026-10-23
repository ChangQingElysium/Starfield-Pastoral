package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorIrisPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe Iris' own construction; never construct a vanilla pipeline inside a shader frame. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.VanillaRenderingPipeline", remap = false)
public abstract class TownDoorIrisVanillaPipelineMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void stardewcraft$captureVanillaPipeline(CallbackInfo ci) {
        TownDoorIrisPipeline.captureVanilla(this);
    }
}

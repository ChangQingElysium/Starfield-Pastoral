package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.client.interior.TownDoorRenderContext;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Shadow casters outside the aperture still illuminate the destination world. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class TownDoorIrisShadowMixin {
    @WrapMethod(method = "renderShadows")
    private void stardewcraft$unclippedShadows(@Coerce Object renderer, Camera camera, Operation<Void> original) {
        boolean previous = TownDoorRenderContext.isShadowPass();
        if (TownDoorRenderContext.isRendering()) TownDoorRenderContext.shadowPass(true);
        try {
            original.call(renderer, camera);
        } finally {
            TownDoorRenderContext.shadowPass(previous);
        }
    }
}

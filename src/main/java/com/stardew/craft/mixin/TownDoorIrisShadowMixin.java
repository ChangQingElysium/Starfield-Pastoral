package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.At;
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
    @WrapOperation(method = "renderShadows", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V"))
    private void stardewcraft$unclippedShadows(@Coerce Object shadows, @Coerce Object renderer, Camera camera, Operation<Void> original) {
        boolean previous = TownDoorRenderContext.isShadowPass();
        if (TownDoorRenderContext.isRendering()) TownDoorRenderContext.shadowPass(true);
        try {
            original.call(shadows, renderer, camera);
        } finally {
            TownDoorRenderContext.shadowPass(previous);
        }
    }
}

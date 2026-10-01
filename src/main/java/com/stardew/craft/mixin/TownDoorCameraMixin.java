package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorClient;
import com.stardew.craft.port.net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pre-render traversal and stencil preparation hooks adapted from Immersive Portals (Apache-2.0). */
@Mixin(GameRenderer.class)
public abstract class TownDoorCameraMixin {
    // PORT(1.20.1): GameRenderer#render(float partialTick, long nanoTime, boolean renderLevel) and
    // renderLevel(float, long, PoseStack) replace the 1.21 DeltaTracker signatures.
    @Inject(method = "render", at = @At("HEAD"))
    private void stardewcraft$crossDoorBeforeCamera(float partialTick, long nanoTime, boolean renderWorld,
                                                     CallbackInfo ci) {
        if (renderWorld) TownDoorClient.beforeRender(DeltaTracker.of(partialTick));
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void stardewcraft$prepareDoorBeforeWorld(float partialTick, long nanoTime, boolean renderWorld,
                                                      CallbackInfo ci) {
        TownDoorClient.prepareFrame();
    }
}

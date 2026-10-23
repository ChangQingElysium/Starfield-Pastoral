package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
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

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    private void stardewcraft$prepareDoorBeforeWorld(float partialTick, long finishNanoTime, PoseStack pose, CallbackInfo ci) {
        // Camera.setup has now run, including a predicted traversal/third-person translation.
        TownDoorClient.prepareFrame();
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    private void stardewcraft$resolveShaderDoors(LevelRenderer renderer, PoseStack view, float partialTick, long finishNanoTime, boolean outline,
                                                Camera camera, GameRenderer gameRenderer, LightTexture light,
                                                Matrix4f projection, Operation<Void> original) {
        original.call(renderer, view, partialTick, finishNanoTime, outline, camera, gameRenderer, light, projection);
        TownDoorClient.renderAfterWorld(DeltaTracker.of(partialTick), finishNanoTime, camera, view.last(), projection);
    }
}

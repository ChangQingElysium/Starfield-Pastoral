package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pre-render traversal and stencil preparation hooks adapted from Immersive Portals (Apache-2.0). */
@Mixin(GameRenderer.class)
public abstract class TownDoorCameraMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void stardewcraft$crossDoorBeforeCamera(DeltaTracker delta, boolean renderWorld, CallbackInfo ci) {
        if (renderWorld) TownDoorClient.beforeRender(delta);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void stardewcraft$prepareDoorBeforeWorld(DeltaTracker delta, CallbackInfo ci) {
        // Camera.setup has now run, including a predicted traversal/third-person translation.
        TownDoorClient.prepareFrame();
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void stardewcraft$resolveShaderDoors(LevelRenderer renderer, DeltaTracker delta, boolean outline,
                                                Camera camera, GameRenderer gameRenderer, LightTexture light,
                                                Matrix4f view, Matrix4f projection, Operation<Void> original) {
        original.call(renderer, delta, outline, camera, gameRenderer, light, view, projection);
        TownDoorClient.renderAfterWorld(delta, camera, view, projection);
    }
}

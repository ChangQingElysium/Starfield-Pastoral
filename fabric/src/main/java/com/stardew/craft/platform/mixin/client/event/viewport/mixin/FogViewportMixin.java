package com.stardew.craft.platform.mixin.client.event.viewport.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.platform.fabric.client.event.viewport.FabricViewportEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class FogViewportMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE", ordinal = 1, target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V"))
    private static void stardewcraft$fogColor(float red, float green, float blue, float alpha, Operation<Void> original,
                                            Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorld) {
        // Wrap the final call's arguments, including the existing pond-water ModifyArgs, rather
        // than reading stale static colors after those arguments have already been pushed.
        var event = FabricViewportEvents.color(camera, partialTick, red, green, blue);
        fogRed = event.getRed(); fogGreen = event.getGreen(); fogBlue = event.getBlue();
        original.call(fogRed, fogGreen, fogBlue, alpha);
    }

    @WrapOperation(method = "setupFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogStart(F)V"))
    private static void stardewcraft$near(float value, Operation<Void> original, @Share("stardewcraft$near") LocalFloatRef near) {
        near.set(value); original.call(value);
    }
    @WrapOperation(method = "setupFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogEnd(F)V"))
    private static void stardewcraft$far(float value, Operation<Void> original, @Share("stardewcraft$far") LocalFloatRef far) {
        far.set(value); original.call(value);
    }
    @WrapOperation(method = "setupFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogShape(Lcom/mojang/blaze3d/shaders/FogShape;)V"))
    private static void stardewcraft$shape(FogShape value, Operation<Void> original, @Share("stardewcraft$shape") LocalRef<FogShape> shape) {
        shape.set(value); original.call(value);
    }
    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void stardewcraft$fogPlanes(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thickFog,
                                             float partialTick, CallbackInfo callback, @Local FogType type,
                                             @Share("stardewcraft$near") LocalFloatRef near,
                                             @Share("stardewcraft$far") LocalFloatRef far,
                                             @Share("stardewcraft$shape") LocalRef<FogShape> shape) {
        FabricViewportEvents.fog(mode, type, camera, partialTick, near.get(), far.get(), shape.get(), (start, end, form) -> {
            RenderSystem.setShaderFogStart(start); RenderSystem.setShaderFogEnd(end); RenderSystem.setShaderFogShape(form);
        });
    }
}

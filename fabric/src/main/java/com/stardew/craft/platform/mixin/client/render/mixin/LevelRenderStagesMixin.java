package com.stardew.craft.platform.mixin.client.render.mixin;


import com.stardew.craft.platform.fabric.client.event.RenderLevelStageEvent.Stage;
import com.stardew.craft.platform.fabric.client.render.FabricLevelRenderEvents;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
// INVOKE injectors must have a strictly higher priority than Sodium's 1000
// overwrite. Both layer implementations retain one of the boundaries below.
@Mixin(value = LevelRenderer.class, priority = 1001)
public abstract class LevelRenderStagesMixin {
    @WrapMethod(method = "renderLevel")
    private void stardewcraft$frameScope(DeltaTracker delta, boolean outline, Camera camera,
            GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f modelView, Matrix4f projection,
            Operation<Void> original) {
        var renderer = (LevelRenderer) (Object) this;
        var state = (LevelRenderStateAccessor) this;
        var captured = state.stardewcraft$getCapturedFrustum();
        var frustum = captured != null ? captured : state.stardewcraft$getCullingFrustum();
        try (var frame = FabricLevelRenderEvents.beginFrame(renderer, delta, camera,
                modelView, projection, state.stardewcraft$getRenderTicks(), frustum)) {
            original.call(delta, outline, camera, gameRenderer, lightTexture, modelView, projection);
        }
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "NEW",
            target = "com/mojang/blaze3d/vertex/PoseStack"), require = 1, allow = 1)
    private PoseStack stardewcraft$captureNativeMatrixStack(PoseStack pose) {
        return FabricLevelRenderEvents.captureMatrixStack((LevelRenderer) (Object) this, pose);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            shift = At.Shift.AFTER))
    private void stardewcraft$afterSky(CallbackInfo callback) { post(Stage.AFTER_SKY); }

    // Vanilla dispatches before clearing its RenderType. Sodium has already
    // finished its own pass cleanup when it reaches its platform event hook;
    // this is the same boundary its NeoForge implementation uses for stages.
    // Require exactly one boundary: an unknown overwrite must not silently
    // discard the stage, and an implementation containing both must not double-post.
    @Inject(method = "renderSectionLayer", at = {
            @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;clearRenderState()V"),
            @At(value = "INVOKE", shift = At.Shift.AFTER, remap = false,
                    target = "Lnet/caffeinemc/mods/sodium/client/services/PlatformLevelRenderHooks;runChunkLayerEvents(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V")
    }, require = 1, allow = 1)
    private void stardewcraft$afterSectionLayer(RenderType type, double x, double y, double z,
            Matrix4f modelView, Matrix4f projection, CallbackInfo callback) {
        Stage stage = Stage.fromRenderType(type);
        if (stage != null) post(stage);
    }

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=destroyProgress"))
    private void stardewcraft$afterBlockEntities(CallbackInfo callback) { post(Stage.AFTER_BLOCK_ENTITIES); }

    // Both graphics branches call ParticleEngine.render once. Publish while
    // PARTICLES_TARGET is still bound in fabulous mode, before clearRenderState.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
            shift = At.Shift.AFTER))
    private void stardewcraft$afterParticles(CallbackInfo callback) { post(Stage.AFTER_PARTICLES); }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
            shift = At.Shift.AFTER))
    private void stardewcraft$afterWeather(CallbackInfo callback) { post(Stage.AFTER_WEATHER); }

    @Unique
    private void post(Stage stage) { FabricLevelRenderEvents.postNativeStage(stage, (LevelRenderer) (Object) this); }
}

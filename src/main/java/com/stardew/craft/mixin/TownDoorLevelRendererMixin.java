package com.stardew.craft.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.interior.TownDoorClient;
import com.stardew.craft.client.interior.TownDoorRenderContext;
import com.stardew.craft.client.interior.TownDoorVanillaVisibility;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import com.stardew.craft.port.net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/** Nested world-render hooks adapted from Immersive Portals (Apache-2.0). */
@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class TownDoorLevelRendererMixin {
    @Shadow private RenderTarget itemEntityTarget;
    @Shadow private RenderTarget weatherTarget;
    @Shadow private RenderTarget translucentTarget;
    @Shadow @Nullable private ClientLevel level;
    @Shadow @Nullable private ViewArea viewArea;
    @Shadow @Nullable private ChunkRenderDispatcher chunkRenderDispatcher;
    // PORT(1.20.1): 1.21 visibleSections is renderChunksInFrustum here, a list of package-private RenderChunkInfo.
    @Shadow @Final private ObjectArrayList<Object> renderChunksInFrustum;

    /** Immersive Portals skips the nested world's full framebuffer clear. */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", remap = false))
    private void stardewcraft$skipNestedClear(int mask, boolean clearError) {
        if (!TownDoorRenderContext.isRendering()) RenderSystem.clear(mask, clearError);
    }

    /** Render after opaque blocks/entities and before translucent buffers, matching the upstream hook. */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Sheets;translucentCullBlockSheet()Lnet/minecraft/client/renderer/RenderType;"))
    private void stardewcraft$renderTownDoor(PoseStack viewStack, float partialTick, long finishNanoTime,
                                                boolean outline, Camera camera, GameRenderer gameRenderer,
                                                LightTexture lightTexture, Matrix4f projection, CallbackInfo ci) {
        if (!TownDoorRenderContext.isRendering()) {
            // PORT(1.20.1): the view rotation is the PoseStack's base pose (1.21 passes it as frustumMatrix).
            TownDoorClient.renderPortal(DeltaTracker.of(partialTick), finishNanoTime, camera, viewStack.last(),
                    projection);
        }
    }

    /** Keep the outer world's Fabulous targets intact while the nested view draws into the main target. */
    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;itemEntityTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget stardewcraft$nestedItemTarget(LevelRenderer renderer) {
        return TownDoorRenderContext.isRendering() ? null : itemEntityTarget;
    }

    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;weatherTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget stardewcraft$nestedWeatherTarget(LevelRenderer renderer) {
        return TownDoorRenderContext.isRendering() ? null : weatherTarget;
    }

    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;translucentTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget stardewcraft$nestedTranslucentTarget(LevelRenderer renderer) {
        return TownDoorRenderContext.isRendering() ? null : translucentTarget;
    }

    @Inject(method = "shouldShowEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$skipNestedOutlines(CallbackInfoReturnable<Boolean> cir) {
        if (TownDoorRenderContext.isRendering()) cir.setReturnValue(false);
    }

    /**
     * Vanilla's asynchronous visibility graph belongs to the outer camera. Reusing it for a
     * vertically distant interior causes both views to invalidate and overwrite each other every
     * frame. Immersive Portals avoids that graph for nested views; this does the same bounded walk.
     */
    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$discoverNestedSections(Camera camera, Frustum frustum,
                                                       boolean hasCapturedFrustum, boolean spectator,
                                                       CallbackInfo ci) {
        if (!TownDoorRenderContext.shouldOverrideVanillaTerrainSetup()
                || level == null || viewArea == null || chunkRenderDispatcher == null) return;
        chunkRenderDispatcher.setCamera(camera.getPosition());
        TownDoorVanillaVisibility.discover(level, viewArea, camera, frustum, renderChunksInFrustum);
        ci.cancel();
    }
}

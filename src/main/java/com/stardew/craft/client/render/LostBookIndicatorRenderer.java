package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Per-player museum indicator matching LibraryMuseum.resetLocalState:
 * Cursors (144,447,15,15), scale 4, 4-second vertical bob.
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class LostBookIndicatorRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/misc/lost_book_indicator.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final double RANGE_SQ = 48.0D * 48.0D;
    private static final float SIZE = 15.0F / 32.0F;
    private static final double BOB_AMPLITUDE = 0.25D;
    private static final double SURFACE_CLEARANCE = 0.01D;
    private static final int FULL_LIGHT = 0xF000F0;

    private LostBookIndicatorRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !ClientPlayerDataCache.isSynced()) {
            return;
        }

        String dimension = minecraft.level.dimension().location().toString();
        int found = ClientPlayerDataCache.getLostBooksFound();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        boolean rendered = false;

        double bob = Math.sin((System.currentTimeMillis() % 4000L) / 4000.0D * Math.PI * 2.0D)
                * BOB_AMPLITUDE;
        for (ClientPlayerDataCache.LostBookMarker marker : ClientPlayerDataCache.getLostBookMarkers()) {
            if (!dimension.equals(marker.dimension())
                    || marker.unlockAt() > found
                    || ClientPlayerDataCache.hasMailFlag(marker.readFlag())) {
                continue;
            }

            // marker.y is the top of the shelf interaction column. Offset the
            // center by half the quad plus the full downward bob amplitude, so
            // even the lowest frame stays above that y plane.
            Vec3 center = new Vec3(
                    marker.x() + 0.5D,
                    marker.y() + SIZE * 0.5D + BOB_AMPLITUDE + SURFACE_CLEARANCE + bob,
                    marker.z() + 0.5D);
            if (center.distanceToSqr(camera) > RANGE_SQ) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            poseStack.mulPose(event.getCamera().rotation());
            Matrix4f pose = poseStack.last().pose();
            float half = SIZE * 0.5F;
            consumer.addVertex(pose, -half, half, 0.0F)
                    .setColor(255, 255, 255, 255).setUv(0.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_LIGHT).setNormal(0.0F, 0.0F, 1.0F);
            consumer.addVertex(pose, half, half, 0.0F)
                    .setColor(255, 255, 255, 255).setUv(1.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_LIGHT).setNormal(0.0F, 0.0F, 1.0F);
            consumer.addVertex(pose, half, -half, 0.0F)
                    .setColor(255, 255, 255, 255).setUv(1.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_LIGHT).setNormal(0.0F, 0.0F, 1.0F);
            consumer.addVertex(pose, -half, -half, 0.0F)
                    .setColor(255, 255, 255, 255).setUv(0.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_LIGHT).setNormal(0.0F, 0.0F, 1.0F);
            poseStack.popPose();
            rendered = true;
        }

        if (rendered) {
            buffers.endBatch(RENDER_TYPE);
        }
    }
}

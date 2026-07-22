package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.client.model.entity.NpcGeoModel;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.renderer.entity.indicator.NpcOverheadIndicator;
import com.stardew.craft.client.renderer.entity.indicator.NpcOverheadIndicatorRegistry;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.ClientNpcVisibilityState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@SuppressWarnings("null")
public class NpcGeoRenderer extends GeoEntityRenderer<StardewNpcEntity> {
    public NpcGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcGeoModel());
        this.shadowRadius = 0.35F;
    }

    @Override
    public boolean shouldRender(StardewNpcEntity entity, Frustum camera, double camX, double camY, double camZ) {
        String npcId = entity.getNpcId();
        if (isHiddenForLocalPlayer(npcId)) {
            return false;
        }
        return super.shouldRender(entity, camera, camX, camY, camZ);
    }

    @Override
    public void render(StardewNpcEntity entity,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight) {
        // Per-player cutscene visibility: skip rendering if hidden for this client
        String npcId = entity.getNpcId();
        if (isHiddenForLocalPlayer(npcId)) {
            return;
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        renderOverheadIndicator(entity, poseStack, bufferSource, packedLight);
    }

    private static boolean isHiddenForLocalPlayer(String npcId) {
        return npcId != null && (ClientNpcVisibilityState.isHidden(npcId)
                || ("henchman".equalsIgnoreCase(npcId)
                && ClientPlayerDataCache.hasMailFlag("henchmanGone")));
    }

    private void renderOverheadIndicator(StardewNpcEntity entity,
                                         PoseStack poseStack,
                                         MultiBufferSource bufferSource,
                                         int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null || !entity.hasLineOfSight(localPlayer) || entity.distanceToSqr(localPlayer) > 32.0D * 32.0D) {
            return;
        }

        NpcOverheadIndicator indicator = NpcOverheadIndicatorRegistry.resolve(entity, localPlayer);
        if (indicator == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + indicator.yOffset(), 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-indicator.scale(), indicator.scale(), indicator.scale());

        float u0 = indicator.u() / (float) indicator.textureWidth();
        float u1 = (indicator.u() + indicator.width()) / (float) indicator.textureWidth();
        float v0 = indicator.v() / (float) indicator.textureHeight();
        float v1 = (indicator.v() + indicator.height()) / (float) indicator.textureHeight();
        float halfW = indicator.width() * 0.5f;
        float halfH = indicator.height() * 0.5f;

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(indicator.texture()));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix4f = pose.pose();

        vertexConsumer.addVertex(matrix4f, -halfW, -halfH, 0.0f)
            .setColor(255, 255, 255, 255)
            .setUv(u0, v1)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, 0.0f, 1.0f, 0.0f);
        vertexConsumer.addVertex(matrix4f, halfW, -halfH, 0.0f)
            .setColor(255, 255, 255, 255)
            .setUv(u1, v1)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, 0.0f, 1.0f, 0.0f);
        vertexConsumer.addVertex(matrix4f, halfW, halfH, 0.0f)
            .setColor(255, 255, 255, 255)
            .setUv(u1, v0)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, 0.0f, 1.0f, 0.0f);
        vertexConsumer.addVertex(matrix4f, -halfW, halfH, 0.0f)
            .setColor(255, 255, 255, 255)
            .setUv(u0, v0)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, 0.0f, 1.0f, 0.0f);

        poseStack.popPose();
    }
}

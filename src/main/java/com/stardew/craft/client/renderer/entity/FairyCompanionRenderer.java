package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.trinket.FairyCompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import com.stardew.craft.port.PortVertex;
public class FairyCompanionRenderer extends EntityRenderer<FairyCompanionEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
        StardewCraft.MODID,
        "textures/entity/trinket/fairy_companion.png"
    );
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 16;

    public FairyCompanionRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(@SuppressWarnings("null") FairyCompanionEntity entity) {
        return TEXTURE;
    }

    @SuppressWarnings("null")
    @Override
    public void render(FairyCompanionEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !entity.isOwnedBy(minecraft.player.getUUID())) {
            return;
        }

        int frame = (entity.tickCount / 5) % 4;
        float u0 = (frame * 16) / (float) TEXTURE_WIDTH;
        float u1 = (frame * 16 + 16) / (float) TEXTURE_WIDTH;
        float v0 = 0.0f;
        float v1 = 16.0f / TEXTURE_HEIGHT;

        poseStack.pushPose();
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.52f, 0.52f, 0.52f);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        quad(consumer, pose, -0.5f, -0.5f, 0.5f, 0.5f, u0, v0, u1, v1, 255, 255, 255, 255);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, 0xF000F0);
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, float x0, float y0, float x1, float y1,
                             float u0, float v0, float u1, float v1,
                             int red, int green, int blue, int alpha) {
        vertex(consumer, pose, x0, y1, u0, v0, red, green, blue, alpha);
        vertex(consumer, pose, x1, y1, u1, v0, red, green, blue, alpha);
        vertex(consumer, pose, x1, y0, u1, v1, red, green, blue, alpha);
        vertex(consumer, pose, x0, y0, u0, v1, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float u, float v,
                               int red, int green, int blue, int alpha) {
        PortVertex.of(consumer).addVertex(pose, x, y, 0.0f)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(0xF000F0)
            .setNormal(0.0f, 1.0f, 0.0f).endVertex();
    }
}
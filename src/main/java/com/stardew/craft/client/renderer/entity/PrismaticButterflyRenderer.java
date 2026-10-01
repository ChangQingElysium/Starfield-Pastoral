package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.mastery.PrismaticButterflyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class PrismaticButterflyRenderer extends EntityRenderer<PrismaticButterflyEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
        StardewCraft.MODID,
        "textures/entity/mastery/critters.png"
    );
    private static final ResourceLocation CURSORS = new ResourceLocation(
        StardewCraft.MODID,
        "textures/gui/cursors_1_6.png"
    );
    private static final ResourceLocation POWDER_TEXTURE = new ResourceLocation(
        StardewCraft.MODID,
        "textures/entity/pet/butterfly_powder_effect.png"
    );
    private static final int TEXTURE_WIDTH = 320;
    private static final int TEXTURE_HEIGHT = 640;
    private static final int CURSORS_SIZE = 512;
    private static final int[] PRISMATIC_FRAMES = {395, 396, 395, 394};

    public PrismaticButterflyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(@SuppressWarnings("null") PrismaticButterflyEntity entity) {
        return TEXTURE;
    }

    @SuppressWarnings("null")
    @Override
    public void render(PrismaticButterflyEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (!entity.isVisualOnly() && (mc.player == null || !entity.isOwnedBy(mc.player.getUUID()))) {
            return;
        }

        if (entity.isPowderVisual()) {
            renderPowderVisual(entity, partialTick, poseStack, buffer, mc);
            super.render(entity, entityYaw, partialTick, poseStack, buffer, 0xF000F0);
            return;
        }

        int frame = entity.isVisualOnly()
            ? entity.getBaseFrame() + ((entity.tickCount / 4) % 2)
            : PRISMATIC_FRAMES[((entity.tickCount / 3) % PRISMATIC_FRAMES.length + PRISMATIC_FRAMES.length) % PRISMATIC_FRAMES.length];
        int frameX = (frame % 20) * 16;
        int frameY = (frame / 20) * 16;
        float u0 = frameX / (float) TEXTURE_WIDTH;
        float v0 = frameY / (float) TEXTURE_HEIGHT;
        float u1 = (frameX + 16) / (float) TEXTURE_WIDTH;
        float v1 = (frameY + 16) / (float) TEXTURE_HEIGHT;

        int red = 255;
        int green = 255;
        int blue = 255;
        if (!entity.isVisualOnly()) {
            int color = Mth.hsvToRgb(((entity.tickCount + partialTick) % 140.0f) / 140.0f, 0.85f, 1.0f);
            red = (color >> 16) & 0xFF;
            green = (color >> 8) & 0xFF;
            blue = color & 0xFF;
        }

        poseStack.pushPose();
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float size = entity.isCapturing() ? 0.92f : 1.0f;
        poseStack.scale(size, size, size);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        int alpha = entity.getBurstTicks() > 0 ? 0 : 255;
        if (alpha > 0) {
            quad(consumer, pose, -0.5f, -0.5f, 0.5f, 0.5f, u0, v0, u1, v1, red, green, blue, alpha);
        }
        renderSparkles(entity, partialTick, poseStack, buffer);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, 0xF000F0);
    }

    private static void renderPowderVisual(PrismaticButterflyEntity entity, float partialTick,
                                           PoseStack poseStack, MultiBufferSource buffer, Minecraft mc) {
        float life = Mth.clamp((entity.tickCount + partialTick) / entity.visualLifetimeTicks(), 0.0F, 1.0F);
        float size = 0.44F + life * 0.12F;
        int alpha = (int) (191.0F * (1.0F - life));
        poseStack.pushPose();
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees((entity.getId() * 47.0F + entity.tickCount * 2.0F) % 360.0F));
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(POWDER_TEXTURE));
        quad(consumer, pose, -size / 2.0F, -size / 2.0F, size / 2.0F, size / 2.0F,
                0.0F, 0.0F, 1.0F, 1.0F, 255, 255, 255, alpha);
        poseStack.popPose();
    }

    private static void renderSparkles(PrismaticButterflyEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(CURSORS));
        float u0 = 144.0f / CURSORS_SIZE;
        float v0 = 249.0f / CURSORS_SIZE;
        float u1 = 151.0f / CURSORS_SIZE;
        float v1 = 256.0f / CURSORS_SIZE;
        int count = entity.getBurstTicks() > 0 ? 16 : (entity.isCapturing() ? 8 : (entity.isVisualOnly() ? 0 : 3));
        for (int i = 0; i < count; i++) {
            float age = entity.tickCount + partialTick + i * 9.0f;
            float life = (age % 16.0f) / 16.0f;
            if (life > 0.68f && !entity.isCapturing() && entity.getBurstTicks() <= 0) {
                continue;
            }
            float angle = entity.getId() * 0.73f + i * 2.11f + age * 0.08f;
            float radius = entity.getBurstTicks() > 0 ? 0.95f : (entity.isCapturing() ? 0.52f + 0.25f * Mth.sin(age * 0.23f) : 0.42f);
            float x = Mth.cos(angle) * radius;
            float y = Mth.sin(angle * 1.37f) * radius * 0.8f;
            float size = entity.getBurstTicks() > 0 ? 0.38f : (entity.isCapturing() ? 0.34f : 0.28f);
            int alpha = entity.isCapturing() || entity.getBurstTicks() > 0 ? 255 : (int) (255.0f * (1.0f - life));

            poseStack.pushPose();
            poseStack.translate(x, y, -0.015f - i * 0.001f);
            Matrix4f pose = poseStack.last().pose();
            quad(consumer, pose, -size / 2.0f, -size / 2.0f, size / 2.0f, size / 2.0f,
                u0, v0, u1, v1, 255, 255, 255, alpha);
            poseStack.popPose();
        }
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
        consumer.addVertex(pose, x, y, 0.0f)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(0xF000F0)
            .setNormal(0.0f, 1.0f, 0.0f);
    }
}

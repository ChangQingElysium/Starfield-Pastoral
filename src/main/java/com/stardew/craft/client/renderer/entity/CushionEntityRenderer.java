package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.entity.seat.CushionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("null")
public final class CushionEntityRenderer extends EntityRenderer<CushionEntity> {
    public CushionEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(CushionEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                entity.getRenderState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CushionEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

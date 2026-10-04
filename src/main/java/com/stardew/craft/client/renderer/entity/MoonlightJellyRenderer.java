package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.client.model.entity.MoonlightJellyGeoModel;
import com.stardew.craft.entity.festival.MoonlightJellyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import com.stardew.craft.client.model.nativebb.BlockbenchEntityRenderer;

public class MoonlightJellyRenderer extends BlockbenchEntityRenderer<MoonlightJellyEntity> {
    private static final int FULL_LIGHT = 0xF000F0;

    public MoonlightJellyRenderer(EntityRendererProvider.Context context) {
        super(context, new MoonlightJellyGeoModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(MoonlightJellyEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, FULL_LIGHT);
    }

    @Override
    public int getRenderColor(MoonlightJellyEntity animatable, float partialTick, int packedLight) {
        return (Math.clamp(Math.round(animatable.getAlpha() * 255), 0, 255) << 24) | 0xFFFFFF;
    }

    @Nullable
    @Override
    public RenderType getRenderType(MoonlightJellyEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }
}
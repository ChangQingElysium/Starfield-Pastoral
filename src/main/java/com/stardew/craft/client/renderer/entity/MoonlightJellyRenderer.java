package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.client.model.entity.MoonlightJellyGeoModel;
import com.stardew.craft.entity.festival.MoonlightJellyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.core.object.Color;

public class MoonlightJellyRenderer extends GeoEntityRenderer<MoonlightJellyEntity> {
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
    public Color getRenderColor(MoonlightJellyEntity animatable, float partialTick, int packedLight) {
        Color base = super.getRenderColor(animatable, partialTick, packedLight);
        int alpha = Math.round(animatable.getAlpha() * 255.0F);
        return Color.ofARGB(alpha, base.getRed(), base.getGreen(), base.getBlue());
    }

    @Nullable
    @Override
    public RenderType getRenderType(MoonlightJellyEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }
}
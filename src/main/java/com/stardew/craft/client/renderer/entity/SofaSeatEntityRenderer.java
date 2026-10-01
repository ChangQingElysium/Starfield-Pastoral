package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.entity.seat.SofaSeatEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("null")
public class SofaSeatEntityRenderer extends EntityRenderer<SofaSeatEntity> {
    private static final ResourceLocation EMPTY = new ResourceLocation("textures/atlas/blocks.png");

    public SofaSeatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SofaSeatEntity entity) {
        return EMPTY;
    }
}

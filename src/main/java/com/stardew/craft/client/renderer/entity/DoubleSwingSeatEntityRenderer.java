package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.entity.seat.DoubleSwingSeatEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("null")
public class DoubleSwingSeatEntityRenderer extends EntityRenderer<DoubleSwingSeatEntity> {
    private static final ResourceLocation EMPTY = new ResourceLocation("textures/atlas/blocks.png");

    public DoubleSwingSeatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DoubleSwingSeatEntity entity) {
        return EMPTY;
    }
}

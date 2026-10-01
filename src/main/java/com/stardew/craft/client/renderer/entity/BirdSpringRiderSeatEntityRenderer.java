package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.entity.seat.BirdSpringRiderSeatEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("null")
public class BirdSpringRiderSeatEntityRenderer extends EntityRenderer<BirdSpringRiderSeatEntity> {
    private static final ResourceLocation EMPTY = new ResourceLocation("textures/atlas/blocks.png");

    public BirdSpringRiderSeatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(BirdSpringRiderSeatEntity entity) {
        return EMPTY;
    }
}

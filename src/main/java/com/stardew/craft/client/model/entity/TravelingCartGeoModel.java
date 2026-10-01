package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.TravelingCartEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class TravelingCartGeoModel extends GeoModel<TravelingCartEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(
            StardewCraft.MODID, "geo/entity/npc/traveling_cart.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/entity/npc/traveling_cart.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(
            StardewCraft.MODID, "animations/entity/npc/traveling_cart.animation.json");

    @Override
    public ResourceLocation getModelResource(TravelingCartEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TravelingCartEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TravelingCartEntity animatable) {
        return ANIMATION;
    }
}
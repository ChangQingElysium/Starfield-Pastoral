package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.TravelingCartEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class TravelingCartGeoModel extends BlockbenchModel<TravelingCartEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "geo/entity/npc/traveling_cart.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/entity/npc/traveling_cart.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
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
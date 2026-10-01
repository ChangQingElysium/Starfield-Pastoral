package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.junimo.JunimoEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class JunimoGeoModel extends BlockbenchModel<JunimoEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "geo/entity/junimo/junimo.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/entity/junimo/junimo_forbidden.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "animations/entity/junimo/junimo.animation.json");

    @Override
    public ResourceLocation getModelResource(JunimoEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(JunimoEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(JunimoEntity animatable) {
        return ANIMATION;
    }
}

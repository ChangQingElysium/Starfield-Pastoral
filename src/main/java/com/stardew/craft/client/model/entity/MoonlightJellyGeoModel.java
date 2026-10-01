package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.festival.MoonlightJellyEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class MoonlightJellyGeoModel extends BlockbenchModel<MoonlightJellyEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geo/entity/festival/moonlight_jelly.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/entity/festival/moonlight_jelly.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animations/entity/festival/moonlight_jelly.animation.json");

    @Override
    public ResourceLocation getModelResource(MoonlightJellyEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MoonlightJellyEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MoonlightJellyEntity animatable) {
        return ANIMATION;
    }
}
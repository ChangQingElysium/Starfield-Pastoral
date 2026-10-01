package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.festival.MoonlightJellyEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MoonlightJellyGeoModel extends GeoModel<MoonlightJellyEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "geo/entity/festival/moonlight_jelly.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/entity/festival/moonlight_jelly.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/entity/festival/moonlight_jelly.animation.json");

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
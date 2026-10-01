package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.BooksellerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BooksellerGeoModel extends GeoModel<BooksellerEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(
            StardewCraft.MODID, "geo/entity/bookseller/bookseller.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/entity/bookseller/bookseller.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(
            StardewCraft.MODID, "animations/entity/bookseller/bookseller.animation.json");

    @Override
    public ResourceLocation getModelResource(BooksellerEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(BooksellerEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(BooksellerEntity animatable) {
        return ANIMATION;
    }
}

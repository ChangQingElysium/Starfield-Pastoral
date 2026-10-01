package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.BooksellerEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class BooksellerGeoModel extends BlockbenchModel<BooksellerEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "geo/entity/bookseller/bookseller.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/entity/bookseller/bookseller.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
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

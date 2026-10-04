package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.AutoPetterBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class AutoPetterGeoModel extends BlockbenchModel<AutoPetterBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geo/block/utility/auto_petter.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/block/utility/auto_petter.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animations/block/utility/auto_petter.animation.json");

    @Override
    public ResourceLocation getModelResource(AutoPetterBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AutoPetterBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(AutoPetterBlockEntity animatable) {
        return ANIMATION;
    }
}
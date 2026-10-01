package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.AutoPetterBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class AutoPetterGeoModel extends GeoModel<AutoPetterBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/utility/auto_petter.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/utility/auto_petter.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/block/utility/auto_petter.animation.json");

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
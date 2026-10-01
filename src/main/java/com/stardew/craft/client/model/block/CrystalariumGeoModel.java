package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.CrystalariumBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

@SuppressWarnings("null")
public class CrystalariumGeoModel extends BlockbenchModel<CrystalariumBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/utility/crystalarium.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/block/utility/crystalarium.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/utility/crystalarium.png");

    @Override
    public ResourceLocation getModelResource(CrystalariumBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CrystalariumBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CrystalariumBlockEntity animatable) {
        return ANIMATION;
    }
}

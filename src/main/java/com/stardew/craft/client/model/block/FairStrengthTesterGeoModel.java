package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.FairStrengthTesterBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class FairStrengthTesterGeoModel extends BlockbenchModel<FairStrengthTesterBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/festival/fair_strength_tester.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/festival/fair_strength_tester.png");

    @Override
    public ResourceLocation getModelResource(FairStrengthTesterBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FairStrengthTesterBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(FairStrengthTesterBlockEntity animatable) {
        return null;
    }
}

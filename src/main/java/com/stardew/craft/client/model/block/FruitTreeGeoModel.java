package com.stardew.craft.client.model.block;

import com.stardew.craft.blockentity.FruitTreeBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class FruitTreeGeoModel extends BlockbenchModel<FruitTreeBlockEntity> {
    @Override
    public ResourceLocation getModelResource(FruitTreeBlockEntity animatable) {
        return animatable.getFruitTreeType().matureModel();
    }

    @Override
    public ResourceLocation getTextureResource(FruitTreeBlockEntity animatable) {
        return animatable.getFruitTreeType().matureTexture();
    }

    @Override
    public ResourceLocation getAnimationResource(FruitTreeBlockEntity animatable) {
        return null;
    }
}

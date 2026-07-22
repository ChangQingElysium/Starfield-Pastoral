package com.stardew.craft.client.model.block;

import com.stardew.craft.blockentity.WizardBuildingBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class WizardBuildingGeoModel extends GeoModel<WizardBuildingBlockEntity> {
    @Override
    public ResourceLocation getModelResource(WizardBuildingBlockEntity animatable) {
        return animatable.kind().model();
    }

    @Override
    public ResourceLocation getTextureResource(WizardBuildingBlockEntity animatable) {
        return animatable.kind().texture();
    }

    @Override
    public ResourceLocation getAnimationResource(WizardBuildingBlockEntity animatable) {
        return null;
    }
}

package com.stardew.craft.client.model.block;

import com.stardew.craft.blockentity.WizardBuildingBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public final class WizardBuildingGeoModel extends BlockbenchModel<WizardBuildingBlockEntity> {
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

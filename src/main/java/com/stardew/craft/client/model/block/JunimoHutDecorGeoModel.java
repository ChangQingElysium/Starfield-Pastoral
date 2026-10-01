package com.stardew.craft.client.model.block;

import com.stardew.craft.block.utility.WizardBuildingKind;
import com.stardew.craft.blockentity.JunimoHutDecorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class JunimoHutDecorGeoModel extends BlockbenchModel<JunimoHutDecorBlockEntity> {
    @Override
    public ResourceLocation getModelResource(JunimoHutDecorBlockEntity animatable) {
        return WizardBuildingKind.JUNIMO_HUT.model();
    }

    @Override
    public ResourceLocation getTextureResource(JunimoHutDecorBlockEntity animatable) {
        return WizardBuildingKind.JUNIMO_HUT.texture();
    }

    @Override
    public ResourceLocation getAnimationResource(JunimoHutDecorBlockEntity animatable) {
        // No animation
        return null;
    }
}

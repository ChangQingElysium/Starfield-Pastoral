package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.JunimoHutDecorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class JunimoHutDecorGeoModel extends GeoModel<JunimoHutDecorBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geo/block/decor/junimo_hut_decor.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/block/decor/common/junimo_hut_decor.png");

    @Override
    public ResourceLocation getModelResource(JunimoHutDecorBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(JunimoHutDecorBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(JunimoHutDecorBlockEntity animatable) {
        // No animation
        return null;
    }
}

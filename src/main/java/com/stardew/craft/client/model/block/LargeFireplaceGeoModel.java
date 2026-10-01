package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.LargeFireplaceBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LargeFireplaceGeoModel extends GeoModel<LargeFireplaceBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/decor/fireplace_1.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/deco/misc/common/fireplace_1.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/block/decor/fireplace_1.animation.json");

    @Override
    public ResourceLocation getModelResource(LargeFireplaceBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LargeFireplaceBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LargeFireplaceBlockEntity animatable) {
        return ANIMATION;
    }
}

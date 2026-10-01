package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.TrashBinBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

@SuppressWarnings("null")
public class TrashBinGeoModel extends GeoModel<TrashBinBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/utility/trash_bin.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/block/utility/trash_bin.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/utility/trash_bin.png");

    @Override
    public ResourceLocation getModelResource(TrashBinBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TrashBinBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TrashBinBlockEntity animatable) {
        return ANIMATION;
    }
}

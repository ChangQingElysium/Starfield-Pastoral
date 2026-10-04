package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.TrashBinBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

@SuppressWarnings("null")
public class TrashBinGeoModel extends BlockbenchModel<TrashBinBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geo/block/utility/trash_bin.geo.json");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animations/block/utility/trash_bin.animation.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/block/utility/trash_bin.png");

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

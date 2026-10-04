package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PillarGeoBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class PillarGeoModel extends BlockbenchModel<PillarGeoBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geo/block/decor/pillar_1.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/block/deco/misc/common/pillar_1.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animations/block/decor/pillar_1.animation.json");
    private static final ResourceLocation GALAXY_MODEL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geo/block/decor/desert_galaxy_pillar.geo.json");
    private static final ResourceLocation GALAXY_TEXTURE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/block/deco/misc/common/desert_galaxy_pillar.png");

    @Override
    public ResourceLocation getModelResource(PillarGeoBlockEntity animatable) {
        if (animatable.getBlockState().getBlock() == ModBlocks.GALAXY_PILLAR.get()) {
            return GALAXY_MODEL;
        }
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(PillarGeoBlockEntity animatable) {
        if (animatable.getBlockState().getBlock() == ModBlocks.GALAXY_PILLAR.get()) {
            return GALAXY_TEXTURE;
        }
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PillarGeoBlockEntity animatable) {
        return ANIMATION;
    }
}

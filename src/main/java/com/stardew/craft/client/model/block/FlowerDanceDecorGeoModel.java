package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.FlowerDanceDecorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FlowerDanceDecorGeoModel extends GeoModel<FlowerDanceDecorBlockEntity> {
    private static final ResourceLocation FLOWER_CLUSTER_MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/festival/flower_cluster.geo.json");
    private static final ResourceLocation FLOWER_CLUSTER_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/festival/flower_cluster.png");
    private static final ResourceLocation SEASONAL_DECOR_MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/festival/seasonal_decor.geo.json");
    private static final ResourceLocation SEASONAL_DECOR_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/festival/seasonal_decor.png");

    @Override
    public ResourceLocation getModelResource(FlowerDanceDecorBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.SEASONAL_DECOR.get())) {
            return SEASONAL_DECOR_MODEL;
        }
        return FLOWER_CLUSTER_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FlowerDanceDecorBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.SEASONAL_DECOR.get())) {
            return SEASONAL_DECOR_TEXTURE;
        }
        return FLOWER_CLUSTER_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(FlowerDanceDecorBlockEntity animatable) {
        return null;
    }
}
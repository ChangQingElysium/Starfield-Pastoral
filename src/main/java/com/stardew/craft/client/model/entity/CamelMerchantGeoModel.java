package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.CamelMerchantEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CamelMerchantGeoModel extends GeoModel<CamelMerchantEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(
            StardewCraft.MODID, "geo/entity/camel_merchant/camel_merchant.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/entity/camel_merchant/camel_merchant.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(
            StardewCraft.MODID, "animations/entity/camel_merchant/camel_merchant.animation.json");

    @Override
    public ResourceLocation getModelResource(CamelMerchantEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CamelMerchantEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CamelMerchantEntity animatable) {
        return ANIMATION;
    }
}

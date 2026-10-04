package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.CamelMerchantEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class CamelMerchantGeoModel extends BlockbenchModel<CamelMerchantEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "geo/entity/camel_merchant/camel_merchant.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/entity/camel_merchant/camel_merchant.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
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

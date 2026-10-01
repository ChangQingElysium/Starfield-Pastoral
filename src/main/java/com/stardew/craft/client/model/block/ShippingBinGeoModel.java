package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

@SuppressWarnings("null")
public class ShippingBinGeoModel extends BlockbenchModel<ShippingBinBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geo/block/utility/shipping_bin.geo.json");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animations/block/utility/shipping_bin.animation.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/block/utility/shipping_bin.png");

    @Override
    public ResourceLocation getModelResource(ShippingBinBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ShippingBinBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ShippingBinBlockEntity animatable) {
        return ANIMATION;
    }
    @Override
    public void pose(ShippingBinBlockEntity bin, com.stardew.craft.client.npcnative.NativeNpcPose pose, float partialTick) {
        // Preserve the old one-cell footprint: animation-space X is reflected in world space.
        boolean compact = !bin.hasFullFootprint();
        pose.setScale("root", compact ? .5 : 1, 1, 1);
        if (compact) pose.addPosition("root", -4, 0, 0);
        pose.blendRotation("lid", Math.toDegrees(bin.lidMotion.radians(partialTick)), 0, 0, 1);
    }
}

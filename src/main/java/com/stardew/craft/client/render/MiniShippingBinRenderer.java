package com.stardew.craft.client.render;

import com.stardew.craft.blockentity.MiniShippingBinBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public final class MiniShippingBinRenderer extends StardewGeoBlockRenderer<MiniShippingBinBlockEntity> {
    public MiniShippingBinRenderer(BlockEntityRendererProvider.Context context) {
        super(new BlockbenchModel<MiniShippingBinBlockEntity>() {
            private ResourceLocation path(String path) { return new ResourceLocation("stardewcraft", path); }
            @Override public void pose(MiniShippingBinBlockEntity bin,
                    com.stardew.craft.client.npcnative.NativeNpcPose pose, float partialTick) {
                pose.blendRotation("lid", Math.toDegrees(bin.lidMotion.radians(partialTick)), 0, 0, 1);
            }
            @Override public ResourceLocation getModelResource(MiniShippingBinBlockEntity bin) {
                return path("geo/block/utility/mini_shipping_bin.geo.json");
            }
            @Override public ResourceLocation getTextureResource(MiniShippingBinBlockEntity bin) {
                return path("textures/block/utility/shipping_bins/mini_shipping_bin.png");
            }
            @Override public ResourceLocation getAnimationResource(MiniShippingBinBlockEntity bin) {
                return path("animations/block/utility/mini_shipping_bin.animation.json");
            }
        });
    }
}

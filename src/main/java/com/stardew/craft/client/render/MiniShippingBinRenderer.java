package com.stardew.craft.client.render;

import com.stardew.craft.blockentity.MiniShippingBinBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class MiniShippingBinRenderer extends StardewGeoBlockRenderer<MiniShippingBinBlockEntity> {
    public MiniShippingBinRenderer(BlockEntityRendererProvider.Context context) {
        super(new GeoModel<MiniShippingBinBlockEntity>() {
            private ResourceLocation path(String path) { return new ResourceLocation("stardewcraft", path); }
            @Override public void setCustomAnimations(MiniShippingBinBlockEntity bin, long id,
                    software.bernie.geckolib.core.animation.AnimationState<MiniShippingBinBlockEntity> state) {
                super.setCustomAnimations(bin, id, state);
                getBone("lid").ifPresent(bone -> bone.setRotX(bin.lidMotion.radians(state.getPartialTick())));
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

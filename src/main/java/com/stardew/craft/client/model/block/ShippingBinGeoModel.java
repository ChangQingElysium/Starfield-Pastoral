package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

@SuppressWarnings("null")
public class ShippingBinGeoModel extends GeoModel<ShippingBinBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/utility/shipping_bin.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/block/utility/shipping_bin.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/utility/shipping_bin.png");

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
    public void setCustomAnimations(ShippingBinBlockEntity bin, long id,
            software.bernie.geckolib.core.animation.AnimationState<ShippingBinBlockEntity> state) {
        super.setCustomAnimations(bin, id, state);
        // Old one-cell installations stay inside their existing cell if a neighbor blocks expansion.
        boolean compact = !bin.hasFullFootprint();
        getBone("root").ifPresent(bone -> {
            bone.setScaleX(compact ? .5f : 1);
            bone.setPosX(compact ? 4 : 0);
        });
        getBone("lid").ifPresent(bone -> bone.setRotX(bin.lidMotion.radians(state.getPartialTick())));
    }

}

package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.blockentity.WoodenChestBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

@SuppressWarnings("null")
public class WoodenChestGeoModel extends GeoModel<WoodenChestBlockEntity> {
    private static final ResourceLocation DEFAULT_MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/utility/wooden_chest_default.geo.json");
    private static final ResourceLocation DEFAULT_ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/block/utility/wooden_chest_default.animation.json");
    private static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/utility/wooden_chest_default.png");
    private static final ResourceLocation[] COLOR_TEXTURES = buildColorTextures();

    private static ResourceLocation[] buildColorTextures() {
        ResourceLocation[] textures = new ResourceLocation[WoodenChestColorPalette.size()];
        for (int i = 0; i < textures.length; i++) {
            textures[i] = new ResourceLocation(
                StardewCraft.MODID,
                String.format("textures/block/utility/wooden_chest_color_%02d.png", i)
            );
        }
        return textures;
    }

    @Override
    public ResourceLocation getModelResource(WoodenChestBlockEntity animatable) {
        return DEFAULT_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(WoodenChestBlockEntity animatable) {
        int index = WoodenChestColorPalette.clampIndex(animatable.getColorSelection());
        if (index < 0) {
            return DEFAULT_TEXTURE;
        }
        return COLOR_TEXTURES[index];
    }

    @Override
    public ResourceLocation getAnimationResource(WoodenChestBlockEntity animatable) {
        return DEFAULT_ANIMATION;
    }
}

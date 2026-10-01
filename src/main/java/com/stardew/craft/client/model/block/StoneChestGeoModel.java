package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.blockentity.StoneChestBlockEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

@SuppressWarnings("null")
public class StoneChestGeoModel extends BlockbenchModel<StoneChestBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/utility/stone_chest.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/block/utility/stone_chest.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/utility/stone_chest.png");
    private static final ResourceLocation[] COLOR_TEXTURES = buildColorTextures();

    private static ResourceLocation[] buildColorTextures() {
        ResourceLocation[] textures = new ResourceLocation[WoodenChestColorPalette.size()];
        for (int i = 0; i < textures.length; i++) {
            textures[i] = new ResourceLocation(
                StardewCraft.MODID,
                String.format("textures/block/utility/stone_chest_color_%02d.png", i)
            );
        }
        return textures;
    }

    @Override
    public ResourceLocation getModelResource(StoneChestBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(StoneChestBlockEntity animatable) {
        int index = WoodenChestColorPalette.clampIndex(animatable.getColorSelection());
        if (index < 0) {
            return TEXTURE;
        }
        return COLOR_TEXTURES[index];
    }

    @Override
    public ResourceLocation getAnimationResource(StoneChestBlockEntity animatable) {
        return ANIMATION;
    }
}
